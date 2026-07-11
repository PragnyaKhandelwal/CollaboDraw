/**
 * elements.js - Fixed & Refactored Canvas Element Management
 */

const ElementManager = {
  lastZIndex: 10,

  // Shared drag state for the single document-level mousemove/mouseup pair below. Previously
  // setupElementInteraction() registered its own mousemove/mouseup listeners on `document` for
  // every element, and they were never removed - a board with N elements accumulated N
  // permanently-live document listeners, and restoreElementInteractions() (called on every
  // undo/redo) re-ran setupElementInteraction for every element again, doubling the count each
  // time. Tracking the one element currently being dragged in module state and registering the
  // move/up handlers once (see initGlobalDragHandlers) fixes both the leak and the compounding.
  _drag: { element: null, startX: 0, startY: 0, startLeft: 0, startTop: 0 },
  _globalHandlersInstalled: false,

  getNextZIndex() {
    this.lastZIndex++;
    return this.lastZIndex;
  },

  initGlobalDragHandlers() {
    if (this._globalHandlersInstalled) return;
    this._globalHandlersInstalled = true;

    // Pointer events (not mouse events) so dragging an element works from touch input too.
    document.addEventListener('pointermove', (e) => {
      const element = this._drag.element;
      if (!element) return;

      const deltaX = e.clientX - this._drag.startX;
      const deltaY = e.clientY - this._drag.startY;

      element.style.left = (this._drag.startLeft + deltaX) + 'px';
      element.style.top = (this._drag.startTop + deltaY) + 'px';
    });

    const endDrag = () => {
      const element = this._drag.element;
      if (!element) return;
      this._drag.element = null;

      element.classList.remove('dragging');
      History.saveState();

      // Sync with Aiven/Socket
      try {
        if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
          const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
          CollaboSocket.publishElement(boardNumeric, {
            kind: 'move',
            payload: {
              id: element.dataset.id,
              x: parseInt(element.style.left, 10) || 0,
              y: parseInt(element.style.top, 10) || 0,
              zIndex: element.style.zIndex
            }
          });
        }
      } catch (_) { }
    };
    document.addEventListener('pointerup', endDrag);
    // A touch drag can be interrupted (e.g. an OS gesture or notification) without ever
    // firing pointerup - without this the element would stay stuck in "dragging" state.
    document.addEventListener('pointercancel', endDrag);
  },

  /**
   * Setup interaction handlers for canvas elements
   */
  setupElementInteraction(element) {
    this.initGlobalDragHandlers();

    // BUG FIX: Prevent duplicate event listeners on the same element
    if (element.dataset.hasListeners === 'true') return;
    element.dataset.hasListeners = 'true';

    element.addEventListener('pointerdown', (e) => {
      // UI FIX: If clicking an input or textarea, don't trigger a drag
      const tag = e.target.tagName.toLowerCase();
      if (tag === 'input' || tag === 'textarea') return;

      e.stopPropagation();

      if (e.ctrlKey || e.metaKey || e.shiftKey) {
        this.toggleElementSelection(element);
        return;
      }

      // Selecting an element (which populates the Properties panel) should always work,
      // even while a drawing tool is active - this whole handler used to bail out unless
      // the Select tool was the current tool, so clicking an existing element to check or
      // edit its color/opacity silently did nothing unless you switched tools first.
      this.selectElement(element);

      // Only dragging (and the z-index bump-to-front that goes with it) is select-tool-only;
      // otherwise picking up a sticky note while mid-drawing would move it unexpectedly.
      if (AppState.currentTool !== 'select') return;

      element.style.zIndex = this.getNextZIndex();

      this._drag.element = element;
      this._drag.startX = e.clientX;
      this._drag.startY = e.clientY;

      // BUG FIX: Use offsetLeft/Top for more stable relative positioning
      this._drag.startLeft = element.offsetLeft;
      this._drag.startTop = element.offsetTop;

      element.classList.add('dragging');
    });

    element.addEventListener('dblclick', (e) => {
      this.editElement(element, e.target);
    });
  },

  /**
   * Create a sticky note element
   */
  createStickyNote(x, y) {
    const stickyId = AppState.generateId();
    const sticky = document.createElement('div');
    sticky.className = 'canvas-element sticky-note';
    sticky.style.left = x + 'px';
    sticky.style.top = y + 'px';
    sticky.style.zIndex = this.getNextZIndex();
    sticky.dataset.id = stickyId;
    
    sticky.innerHTML = `
      <div class="sticky-header">
        <input type="text" class="sticky-title" value="New Note" placeholder="Title" readonly>
      </div>
      <textarea class="sticky-content" placeholder="Add your thoughts..." readonly></textarea>
      <div class="sticky-footer">
        <div class="sticky-dots"><div class="dot"></div></div>
      </div>
      <div class="resize-handle nw"></div>
      <div class="resize-handle ne"></div>
      <div class="resize-handle sw"></div>
      <div class="resize-handle se"></div>
    `;
    
    document.getElementById('canvasElements').appendChild(sticky);
    this.setupElementInteraction(sticky);
    this.selectElement(sticky);
    History.saveState();
    
    // Broadcast Creation
    this._broadcastChange('sticky', { 
        id: stickyId, x, y, title: 'New Note', content: '', zIndex: sticky.style.zIndex 
    });
    
    // Debounced Content Updates
    const titleInput = sticky.querySelector('.sticky-title');
    const contentArea = sticky.querySelector('.sticky-content');
    let _stickyTimer;
    
    const queueUpdate = () => {
      clearTimeout(_stickyTimer);
      _stickyTimer = setTimeout(() => {
        this._broadcastChange('sticky-update', { 
            id: stickyId, title: titleInput.value, content: contentArea.value 
        });
      }, 300);
    };
    
    titleInput.addEventListener('input', queueUpdate);
    contentArea.addEventListener('input', queueUpdate);
    
    return sticky;
  },

  /**
   * Helper for socket broadcasting
   */
  _broadcastChange(kind, payload) {
    try {
      if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
        const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
        CollaboSocket.publishElement(boardNumeric, { kind, payload });
      }
    } catch(e){}
  },

  createTextElement(x, y) {
    const textId = AppState.generateId();
    const textEl = document.createElement('div');
    textEl.className = 'canvas-element text-element';
    textEl.style.left = x + 'px';
    textEl.style.top = y + 'px';
    textEl.style.zIndex = this.getNextZIndex();
    textEl.dataset.id = textId;
    
    const input = document.createElement('input');
    input.type = 'text';
    input.value = 'Text';
    input.className = 'canvas-text-input';
    input.style.color = AppState.currentColor;
    
    textEl.appendChild(input);
    document.getElementById('canvasElements').appendChild(textEl);
    
    input.focus();
    input.select();
    
    this.setupElementInteraction(textEl);
    History.saveState();
    
    this._broadcastChange('text', { id: textId, x, y, value: 'Text', zIndex: textEl.style.zIndex });

    return textEl;
  },

  selectElement(element) {
    this.clearSelection();
    element.classList.add('selected');
    AppState.selectedElements.push(element);
    if (typeof UIControls !== 'undefined') UIControls.updatePropertiesPanel(element);
  },

  toggleElementSelection(element) {
    if (!element) return;
    const index = AppState.selectedElements.indexOf(element);
    if (index >= 0) {
      element.classList.remove('selected');
      AppState.selectedElements.splice(index, 1);
    } else {
      element.classList.add('selected');
      AppState.selectedElements.push(element);
    }

    if (typeof UIControls !== 'undefined') {
      const active = AppState.selectedElements[AppState.selectedElements.length - 1] || null;
      UIControls.updatePropertiesPanel(active);
    }
  },

  clearSelection() {
    AppState.selectedElements.forEach(el => el.classList.remove('selected'));
    AppState.selectedElements = [];

    if (typeof UIControls !== 'undefined') {
      UIControls.updatePropertiesPanel(null);
    }
  },

  editElement(element, target) {
    // Only unlock the field that was actually double-clicked (a sticky note has both a
    // title input and a content textarea) - looping over every input/textarea and calling
    // focus() on each in turn meant only the last one ever ended up editable, since each
    // earlier field's blur handler re-applied readonly before the user could type into it.
    // That's why typing into a sticky note's title silently did nothing.
    const input = (target && target.closest && target.closest('input, textarea'))
      || element.querySelector('input, textarea');
    if (!input) return;

    input.removeAttribute('readonly');
    input.focus();
    if (input.select) input.select();

    input.addEventListener('blur', () => {
      input.setAttribute('readonly', 'true');
      History.saveState();
    }, { once: true });
  },

  bringToFront() {
    // BUG FIX: Set z-index higher than the current max on canvas
    const elements = Array.from(document.querySelectorAll('.canvas-element'));
    const maxZ = elements.reduce((max, el) => Math.max(max, parseInt(el.style.zIndex) || 0), 0);
    
    AppState.selectedElements.forEach(element => {
      element.style.zIndex = maxZ + 1;
    });
    this.lastZIndex = maxZ + 1;
    History.saveState();
  },

  sendToBack() {
    AppState.selectedElements.forEach(element => {
      element.style.zIndex = '1';
    });
    History.saveState();
  },

  /**
   * Delete selected elements. Referenced by the context menu and the Delete key
   * (modules/init.js), but was never defined here - both actions silently did nothing.
   */
  deleteSelected() {
    if (AppState.selectedElements.length === 0) return;
    AppState.selectedElements.forEach(element => element.remove());
    AppState.selectedElements = [];
    History.saveState();
    if (typeof UIControls !== 'undefined') {
      UIControls.updatePropertiesPanel(null);
      UIControls.showNotification('Elements deleted');
    }
  },

  /**
   * Copy selected elements to an in-memory clipboard. Referenced by the context menu and
   * Ctrl+C (modules/init.js), but was never defined here.
   */
  copySelected() {
    AppState.clipboard = AppState.selectedElements.map(el => ({
      html: el.outerHTML
    }));
    if (typeof UIControls !== 'undefined') UIControls.showNotification('Copied to clipboard');
  },

  /**
   * Paste elements from the in-memory clipboard, offset from their original position.
   * Referenced by the context menu and Ctrl+V (modules/init.js), but was never defined here.
   */
  pasteFromClipboard() {
    if (!AppState.clipboard || AppState.clipboard.length === 0) {
      if (typeof UIControls !== 'undefined') UIControls.showNotification('Nothing to paste');
      return;
    }

    const container = document.getElementById('canvasElements');
    if (!container) return;

    const pasted = [];
    AppState.clipboard.forEach(item => {
      const temp = document.createElement('div');
      temp.innerHTML = item.html;
      const element = temp.firstElementChild;
      if (!element) return;
      element.dataset.id = AppState.generateId();
      element.dataset.hasListeners = ''; // force re-registration on the pasted clone
      delete element.dataset.hasListeners;

      const left = (parseInt(element.style.left, 10) || 0) + 30;
      const top = (parseInt(element.style.top, 10) || 0) + 30;
      element.style.left = left + 'px';
      element.style.top = top + 'px';
      element.classList.remove('selected');

      container.appendChild(element);
      this.setupElementInteraction(element);
      pasted.push(element);
    });

    this.clearSelection();
    pasted.forEach(el => this.toggleElementSelection(el));

    History.saveState();
    if (typeof UIControls !== 'undefined') UIControls.showNotification('Pasted from clipboard');
  },

  /**
   * Duplicate selected elements in place, offset from the originals. Referenced by the
   * context menu (modules/init.js), but was never defined here.
   */
  duplicateSelected() {
    if (AppState.selectedElements.length === 0) return;
    const container = document.getElementById('canvasElements');
    if (!container) return;

    const duplicates = [];
    AppState.selectedElements.forEach(element => {
      const clone = element.cloneNode(true);
      clone.dataset.id = AppState.generateId();
      delete clone.dataset.hasListeners;
      clone.classList.remove('selected');

      const left = (parseInt(element.style.left, 10) || 0) + 20;
      const top = (parseInt(element.style.top, 10) || 0) + 20;
      clone.style.left = left + 'px';
      clone.style.top = top + 'px';

      container.appendChild(clone);
      this.setupElementInteraction(clone);
      duplicates.push(clone);
    });

    this.clearSelection();
    duplicates.forEach(el => this.toggleElementSelection(el));

    History.saveState();
    if (typeof UIControls !== 'undefined') UIControls.showNotification('Elements duplicated');
  },

  /**
   * Tag selected elements as a group (shared dataset.groupId) so they can be selected/moved
   * together later. Referenced by the context menu (modules/init.js), but was never defined
   * here.
   */
  groupSelected() {
    if (AppState.selectedElements.length < 2) {
      if (typeof UIControls !== 'undefined') UIControls.showNotification('Select at least two elements to group');
      return;
    }
    const groupId = AppState.generateId();
    AppState.selectedElements.forEach(element => {
      element.dataset.groupId = groupId;
      element.classList.add('grouped');
    });
    History.saveState();
    if (typeof UIControls !== 'undefined') UIControls.showNotification('Elements grouped');
  },

  /**
   * Remove the group tag from selected elements. Referenced by the context menu
   * (modules/init.js), but was never defined here.
   */
  ungroupSelected() {
    if (AppState.selectedElements.length === 0) {
      if (typeof UIControls !== 'undefined') UIControls.showNotification('Select grouped elements to ungroup');
      return;
    }
    AppState.selectedElements.forEach(element => {
      delete element.dataset.groupId;
      element.classList.remove('grouped');
    });
    History.saveState();
    if (typeof UIControls !== 'undefined') UIControls.showNotification('Elements ungrouped');
  },

  restoreElementInteractions() {
    const container = document.getElementById('canvasElements');
    if (!container) return;
    
    // Clear and reset listener tracking before re-initializing
    container.querySelectorAll('.canvas-element').forEach((element) => {
      delete element.dataset.hasListeners;
      this.setupElementInteraction(element);
    });
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = ElementManager;
}