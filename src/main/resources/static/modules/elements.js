/**
 * elements.js - Fixed & Refactored Canvas Element Management
 */

const ElementManager = {
  lastZIndex: 10,
  
  getNextZIndex() {
    this.lastZIndex++;
    return this.lastZIndex;
  },

  /**
   * Setup interaction handlers for canvas elements
   */
  setupElementInteraction(element) {
    // BUG FIX: Prevent duplicate event listeners on the same element
    if (element.dataset.hasListeners === 'true') return;
    element.dataset.hasListeners = 'true';

    let isDragging = false;
    let startX, startY, startLeft, startTop;
    
    element.addEventListener('mousedown', (e) => {
      if (AppState.currentTool !== 'select') return;
      
      // UI FIX: If clicking an input or textarea, don't trigger a drag
      const tag = e.target.tagName.toLowerCase();
      if (tag === 'input' || tag === 'textarea') return;

      e.stopPropagation();
      
      // UI FIX: Dynamic Z-Index update to bring to front
      element.style.zIndex = this.getNextZIndex();

      if (e.ctrlKey || e.metaKey || e.shiftKey) {
        this.toggleElementSelection(element);
        return;
      }

      isDragging = true;
      startX = e.clientX;
      startY = e.clientY;
      
      // BUG FIX: Use offsetLeft/Top for more stable relative positioning
      startLeft = element.offsetLeft;
      startTop = element.offsetTop;
      
      this.selectElement(element);
      element.classList.add('dragging');
    });
    
    // Global mousemove to handle fast dragging without losing focus
    document.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      
      const deltaX = e.clientX - startX;
      const deltaY = e.clientY - startY;
      
      element.style.left = (startLeft + deltaX) + 'px';
      element.style.top = (startTop + deltaY) + 'px';
    });
    
    document.addEventListener('mouseup', () => {
      if (!isDragging) return;
      
      isDragging = false;
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
      } catch(_){ }
    });
    
    element.addEventListener('dblclick', () => {
      this.editElement(element);
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

  createLinks(x, y) {
     // implementation for links if needed
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

  editElement(element) {
    const inputs = element.querySelectorAll('input, textarea');
    inputs.forEach(input => {
      input.removeAttribute('readonly');
      input.focus();
      if (input.select) input.select();
      
      input.addEventListener('blur', () => {
        input.setAttribute('readonly', 'true');
        History.saveState();
      }, { once: true });
    });
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

  restoreElementInteractions() {
    const container = document.getElementById('canvasElements');
    if (!container) return;
    
    // Clear and reset listener tracking before re-initializing
    container.querySelectorAll('.canvas-element').forEach((element) => {
      delete element.dataset.hasListeners;
      this.setupElementInteraction(element);
    });
    console.log('🔗 Element interactions fully restored');
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = ElementManager;
}