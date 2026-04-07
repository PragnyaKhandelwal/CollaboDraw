/**
 * init.js - Application initialization and orchestration
 * Coordinates module initialization and event setup
 */

let isPanningCanvas = false;
let lastPanClientX = 0;
let lastPanClientY = 0;

/**
 * Initialize the entire application
 */
async function initializeApp() {
  try {
    console.log('🚀 Initializing CollaboDraw...');

    // Get DOM elements
    AppState.canvas = document.getElementById('drawingCanvas');
    AppState.ctx = AppState.canvas?.getContext('2d');
    AppState.mainCanvas = document.getElementById('mainCanvas');
    
    if (!AppState.canvas || !AppState.ctx || !AppState.mainCanvas) {
      console.error('❌ Critical DOM elements missing - cannot initialize');
      return;
    }
    
    console.log('✅ DOM elements loaded');

    // Handle new board parameters
    try {
      const params = new URLSearchParams(window.location.search || '');
      const isNewBoard = (window.CD && String(window.CD.newBoard).toLowerCase() === '1') || params.get('new') === '1';
      if (isNewBoard) {
        localStorage.removeItem('collabodraw-board');
        localStorage.removeItem('collabodraw-versions');
        AppState.boardData = { name: 'Untitled Board', elements: [], settings: {} };
        AppState.selectedElements = [];
        AppState.undoStack = [];
        AppState.redoStack = [];
      }
    } catch (_) {}

    // Setup canvas
    Canvas.resizeCanvas();
    
    // Initialize modules
    UIControls.initializeTools();
    setupEventListeners();
    UIControls.selectTool('pen');

    // Resolve the active board before loading any cached state.
    // This keeps session boards isolated from stale data from other boards.
    await ensureStartupBoard();
    
    // Load state
    await Storage.loadBoardState();
    UIControls.initialize();
    History.initialize();
    
    // Load board name
    await loadBoardName();
    
    // Setup auto-save
    Storage.setupAutoSave();
    
    // Handle import/template seeding
    await setupImportFlow();

    // Hide loading screen
    setTimeout(() => {
      const loading = document.getElementById('loading');
      if (loading) {
        loading.classList.remove('show');
      }
    }, 1000);

    console.log('✅ App initialization complete');
  } catch(e) {
    console.error('❌ Initialization error:', e);
  }
}

/**
 * Setup canvas event listeners
 */
function setupEventListeners() {
  // Drawing canvas
  if (AppState.canvas) {
    AppState.canvas.addEventListener('mousedown', (e) => DrawingTools.startDrawing(e));
    AppState.canvas.addEventListener('mousemove', (e) => DrawingTools.draw(e));
    AppState.canvas.addEventListener('mouseup', () => DrawingTools.stopDrawing());
    AppState.canvas.addEventListener('mouseout', () => DrawingTools.stopDrawing());
  }

  // Main canvas
  AppState.mainCanvas.addEventListener('click', (e) => {
    if (AppState.currentTool === 'eraser') {
      DrawingTools.handleEraserClick(e);
      return;
    }
    handleCanvasClick(e);
  });
  
  AppState.mainCanvas.addEventListener('contextmenu', showContextMenu);
  let lastCursorSend = 0;
  AppState.mainCanvas.addEventListener('mousedown', handleCanvasMouseDown);
  AppState.mainCanvas.addEventListener('mousemove', (e) => {
    handleCanvasMouseMove(e);
    
    // Broadcast cursor to real-time sync with 33ms throttle (~30 FPS)
    const now = Date.now();
    if (now - lastCursorSend > 33 && window.CD && window.CD.boardId && window.CollaboSocket) {
      const rect = AppState.mainCanvas.getBoundingClientRect();
      const x = (e.clientX - rect.left) / AppState.zoomLevel - AppState.panX;
      const y = (e.clientY - rect.top) / AppState.zoomLevel - AppState.panY;
      
      try {
        const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
        const currentName = (window.CD && window.CD.currentUserName) || AppState.getCurrentUser().name;
        CollaboSocket.updateCursor(boardNumeric, x, y, AppState.getClientId(), currentName);
        lastCursorSend = now;
      } catch (err) {
        // ignore
      }
    }
  });
  AppState.mainCanvas.addEventListener('mouseup', handleCanvasMouseUp);
  
  // Keyboard
  document.addEventListener('keydown', handleKeyboard);
  
  // Window
  window.addEventListener('resize', () => Canvas.resizeCanvas());
  
  // Context menu
  document.addEventListener('click', hideContextMenu);
  
  document.querySelectorAll('.menu-item').forEach(item => {
    item.addEventListener('click', function() {
      handleContextAction(this.dataset.action);
      hideContextMenu();
    });
  });
}

/**
 * Handle canvas click events
 */
function handleCanvasClick(e) {
  const rect = AppState.mainCanvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  
  switch(AppState.currentTool) {
    case 'sticky':
      ElementManager.createStickyNote(x, y);
      break;
    case 'text':
      ElementManager.createTextElement(x, y);
      break;
  }
}

/**
 * Handle canvas mouse down (drag start)
 */
function handleCanvasMouseDown(e) {
  if (AppState.currentTool !== 'hand') return;
  if (e.button !== 0) return;

  isPanningCanvas = true;
  lastPanClientX = e.clientX;
  lastPanClientY = e.clientY;
  AppState.mainCanvas.style.cursor = 'grabbing';
  e.preventDefault();
}

/**
 * Handle canvas mouse move
 */
function handleCanvasMouseMove(e) {
  if (!isPanningCanvas || AppState.currentTool !== 'hand') return;

  const dx = e.clientX - lastPanClientX;
  const dy = e.clientY - lastPanClientY;
  lastPanClientX = e.clientX;
  lastPanClientY = e.clientY;

  AppState.panX += dx / AppState.zoomLevel;
  AppState.panY += dy / AppState.zoomLevel;
  Canvas.updateZoom();
  e.preventDefault();
}

/**
 * Handle canvas mouse up (drag end)
 */
function handleCanvasMouseUp(e) {
  if (!isPanningCanvas) return;
  isPanningCanvas = false;

  if (AppState.currentTool === 'hand') {
    AppState.mainCanvas.style.cursor = 'grab';
  }

  e.preventDefault();
}

/**
 * Show context menu
 */
function showContextMenu(e) {
  e.preventDefault();
  
  const contextMenu = document.getElementById('contextMenu');
  if (!contextMenu) return;
  
  contextMenu.style.left = e.clientX + 'px';
  contextMenu.style.top = e.clientY + 'px';
  contextMenu.classList.add('show');
}

/**
 * Hide context menu
 */
function hideContextMenu() {
  const contextMenu = document.getElementById('contextMenu');
  if (contextMenu) contextMenu.classList.remove('show');
}

/**
 * Handle context menu actions
 */
function handleContextAction(action) {
  switch(action) {
    case 'edit':
      if (AppState.selectedElements.length > 0) {
        ElementManager.editElement(AppState.selectedElements[0]);
      }
      break;
    case 'duplicate':
      ElementManager.duplicateSelected();
      break;
    case 'copy':
      ElementManager.copySelected();
      break;
    case 'paste':
      ElementManager.pasteFromClipboard();
      break;
    case 'delete':
      ElementManager.deleteSelected();
      break;
    case 'bring-front':
      ElementManager.bringToFront();
      break;
    case 'send-back':
      ElementManager.sendToBack();
      break;
    case 'group':
      ElementManager.groupSelected();
      break;
    case 'ungroup':
      ElementManager.ungroupSelected();
      break;
  }
}

/**
 * Handle keyboard shortcuts
 */
function handleKeyboard(e) {
  if (e.target.matches('input, textarea')) return;
  
  if (e.ctrlKey || e.metaKey) {
    switch(e.key.toLowerCase()) {
      case 'z':
        e.preventDefault();
        History.undo();
        break;
      case 'y':
        e.preventDefault();
        History.redo();
        break;
      case 'c':
        e.preventDefault();
        ElementManager.copySelected();
        break;
      case 'v':
        e.preventDefault();
        ElementManager.pasteFromClipboard();
        break;
      case 's':
        e.preventDefault();
        saveBoard();
        break;
    }
    return;
  }
  
  switch(e.key.toLowerCase()) {
    case 'v':
      UIControls.selectTool('select');
      break;
    case 'p':
      UIControls.selectTool('pen');
      break;
    case 'h':
      UIControls.selectTool('highlighter');
      break;
    case 't':
      UIControls.selectTool('text');
      break;
    case 's':
      UIControls.selectTool('sticky');
      break;
    case 'r':
      UIControls.selectTool('rectangle');
      break;
    case 'o':
      UIControls.selectTool('circle');
      break;
    case 'l':
      UIControls.selectTool('line');
      break;
    case 'a':
      UIControls.selectTool('arrow');
      break;
    case 'e':
      UIControls.selectTool('eraser');
      break;
    case ' ':
      e.preventDefault();
      UIControls.selectTool('hand');
      break;
    case 'delete':
      ElementManager.deleteSelected();
      break;
    case 'escape':
      ElementManager.clearSelection();
      break;
    case 'f1':
      e.preventDefault();
      UIControls.showHelp();
      break;
    case '+':
    case '=':
      Canvas.zoomIn();
      break;
    case '-':
      Canvas.zoomOut();
      break;
    case '0':
      Canvas.fitToScreen();
      break;
  }
}

/**
 * Load board name from server
 */
async function loadBoardName() {
  try {
    let boardId = AppState.getBoardId();
    if (!boardId) return;
    
    const response = await fetch(`/api/boards/${boardId}`, {
      credentials: 'include'
    });
    if (!response.ok) return;
    
    const data = await response.json();
    
    if (data && data.name) {
      AppState.boardData.name = data.name;
      const boardNameInput = document.getElementById('boardName');
      if (boardNameInput) {
        boardNameInput.value = data.name;
      }
      console.log(`✅ Board name loaded: ${data.name}`);
    }
  } catch (error) {
    console.error('❌ Failed to load board name:', error);
  }
}

/**
 * Setup import flow
 */
async function setupImportFlow() {
  try {
    const pre = sessionStorage.getItem('collabodraw-pre-import');
    const params = new URLSearchParams(window.location.search);
    
    if (pre) {
      sessionStorage.removeItem('collabodraw-pre-import');
      const payload = JSON.parse(pre);
      
      if (payload && payload.kind === 'json' && typeof payload.data === 'string') {
        try {
          importBoardFromJSON(payload.data);
          const base = inferNameFromFile(payload.name || 'Imported Board');
          await ensureServerBoardAndSave(base);
        } catch(e) {
          console.warn('⚠️ Failed to import JSON:', e);
        }
      } else if (payload && payload.kind === 'image' && typeof payload.data === 'string') {
        addImageToCanvas(payload.data, payload.name);
        const base = inferNameFromFile(payload.name || 'Imported Image');
        await ensureServerBoardAndSave(base);
      } else if (params.has('import')) {
        setTimeout(() => importFile(), 300);
      }
    } else if (params.has('import')) {
      setTimeout(() => importFile(), 300);
    }
  } catch(e) {
    console.error('⚠️ Import handling error:', e);
  }

  // Wire up import button
  const importInput = document.getElementById('importInput');
  if (importInput && !importInput.__wired) {
    importInput.addEventListener('change', handleImportSelection);
    importInput.__wired = true;
  }

  // Ensure startup board & apply templates
  try {
    await ensureStartupBoard();
    await applyTemplateSeedIfRequested();
    RealTime.startSync();
  } catch (e) {
    console.warn('Startup board or template error:', e);
    try {
      RealTime.startSync();
    } catch (_) {}
  }
}

/**
 * Save board to server (internal implementation)
 */
function saveBoard_internal() {
  Storage.saveBoardState();
  const statusEl = document.getElementById('saveStatus');
  if (statusEl) statusEl.textContent = 'Saved';
  UIControls.showNotification('Board saved successfully!');
  
  setTimeout(() => {
    if (statusEl) statusEl.textContent = 'Auto-saved';
  }, 2000);
}

/**
 * Auto-save callback
 */
function autoSave() {
  Storage.saveBoardState();
  const status = document.getElementById('saveStatus');
  if (status) status.textContent = 'Auto-saved';
}

/**
 * Trigger file import dialog
 */
function importFile() {
  const input = document.getElementById('importInput');
  if (!input) return UIControls.showNotification('Import not available');
  input.value = '';
  input.click();
}

/**
 * Handle file selection from import dialog
 */
function handleImportSelection(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;

  const type = file.type || '';
  if (type.startsWith('image/')) {
    const reader = new FileReader();
    reader.onload = async () => {
      addImageToCanvas(reader.result, file.name);
      await ensureServerBoardAndSave(inferNameFromFile(file.name));
    };
    reader.readAsDataURL(file);
  } else if (type === 'application/json' || file.name.toLowerCase().endsWith('.json')) {
    const reader = new FileReader();
    reader.onload = async () => {
      importBoardFromJSON(reader.result);
      let name = inferNameFromFile(file.name);
      try {
        const parsed = JSON.parse(reader.result);
        if (parsed && parsed.name) name = parsed.name;
        if (parsed && parsed.board && parsed.board.name) name = parsed.board.name;
      } catch {}
      await ensureServerBoardAndSave(name);
    };
    reader.readAsText(file);
  } else if (file.name.toLowerCase().endsWith('.svg')) {
    const reader = new FileReader();
    reader.onload = async () => {
      addImageToCanvas(reader.result, file.name);
      await ensureServerBoardAndSave(inferNameFromFile(file.name));
    };
    reader.readAsDataURL(file);
  } else {
    UIControls.showNotification('Unsupported file. Please import an image or JSON.');
  }
}

/**
 * Add image to canvas
 */
function addImageToCanvas(dataUrl, name) {
  const container = document.getElementById('canvasElements');
  if (!container) return;

  const id = AppState.generateId();
  const wrapper = document.createElement('div');
  wrapper.className = 'canvas-element image-element';
  wrapper.dataset.id = id;
  
  const rect = document.getElementById('mainCanvas').getBoundingClientRect();
  wrapper.style.left = Math.max(40, Math.floor(rect.width / 2 - 200)) + 'px';
  wrapper.style.top = Math.max(40, Math.floor(rect.height / 2 - 150)) + 'px';

  const img = document.createElement('img');
  img.src = dataUrl;
  img.alt = name || 'imported-image';
  img.style.maxWidth = '400px';
  img.style.maxHeight = '300px';
  img.style.display = 'block';
  img.style.borderRadius = '8px';
  img.style.boxShadow = '0 2px 8px rgba(0,0,0,0.1)';

  wrapper.appendChild(img);
  ['nw','ne','sw','se'].forEach(pos => {
    const h = document.createElement('div');
    h.className = 'resize-handle ' + pos;
    wrapper.appendChild(h);
  });

  container.appendChild(wrapper);
  ElementManager.setupElementInteraction(wrapper);
  History.saveState();
  UIControls.showNotification('Image imported');
}

/**
 * Import board from JSON
 */
function importBoardFromJSON(jsonText) {
  try {
    const parsed = JSON.parse(jsonText);
    if (parsed && parsed.elements) {
      const container = document.getElementById('canvasElements');
      container.innerHTML = parsed.elements;
      container.querySelectorAll('.canvas-element').forEach(el => ElementManager.setupElementInteraction(el));
      if (parsed.name) {
        const nameInput = document.getElementById('boardName');
        if (nameInput) nameInput.value = parsed.name;
      }
      History.saveState();
      UIControls.showNotification('Board imported');
      return;
    }
    if (parsed && parsed.board && parsed.board.elements) {
      const container = document.getElementById('canvasElements');
      container.innerHTML = parsed.board.elements;
      container.querySelectorAll('.canvas-element').forEach(el => ElementManager.setupElementInteraction(el));
      History.saveState();
      UIControls.showNotification('Board imported');
      return;
    }
    if (parsed && (parsed.settings || parsed.name)) {
      AppState.boardData = parsed;
      document.getElementById('canvasElements').innerHTML = AppState.boardData.elements || '';
      document.querySelectorAll('.canvas-element').forEach(el => ElementManager.setupElementInteraction(el));
      if (AppState.boardData.name) {
        const nameInput = document.getElementById('boardName');
        if (nameInput) nameInput.value = AppState.boardData.name;
      }
      UIControls.showNotification('Board imported');
      History.saveState();
    } else {
      UIControls.showNotification('Unrecognized JSON format');
    }
  } catch (e) {
    console.error('Failed to import JSON:', e);
    UIControls.showNotification('Failed to import JSON');
  }
}

/**
 * Extract name from filename
 */
function inferNameFromFile(name) {
  if (!name) return 'Imported Board';
  return name.replace(/\.[^.]+$/, '').slice(0, 100) || 'Imported Board';
}

/**
 * Ensure server board exists
 */
async function ensureStartupBoard() {
  try {
    const qp = new URLSearchParams(window.location.search || '');
    const sessionCode = qp.get('session');
    const previewId = qp.get('preview');
    const wantsNewBoard = qp.get('new') === '1' || (window.CD && String(window.CD.newBoard).toLowerCase() === '1');
    const requestedName = (qp.get('name') || (window.CD && window.CD.boardName) || '').trim();
    let boardId = window.CD && window.CD.boardId ? Number(window.CD.boardId) : null;

    // Session code explicitly overrides stale board query state so users can switch rooms reliably.
    if (sessionCode) {
      const res = await fetch('/api/boards/session', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: sessionCode })
      });
      if (res.ok) {
        const data = await res.json();
        if (!window.CD) window.CD = {};
        window.CD.boardId = data.id;
        try {
          const url = new URL(window.location.href);
          url.searchParams.delete('session');
          url.searchParams.set('board', String(data.id));
          window.history.replaceState ({}, '', url);
        } catch {}
        const bn = document.getElementById('boardName');
        if (bn) { bn.value = data.name || bn.value || `Session ${sessionCode}`; bn.disabled = true; }
        return;
      }
    }

    if (Number.isFinite(boardId) && boardId > 0) {
      const resp = await fetch(`/api/boards/${boardId}`, { credentials: 'include' });
      if (resp.ok) return;
    }

    if (!boardId && !previewId && wantsNewBoard) {
      const boardName = requestedName || (document.getElementById('boardName')?.value || AppState.boardData?.name || 'Untitled Board').trim() || 'Untitled Board';
      const response = await fetch('/api/boards/create', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `name=${encodeURIComponent(boardName)}`
      });
      if (response.ok) {
        const data = await response.json();
        if (data && data.id) {
          if (!window.CD) window.CD = {};
          window.CD.boardId = data.id;
          try {
            localStorage.setItem('collabodraw-boards-updated', JSON.stringify({
              action: 'created',
              boardId: data.id,
              timestamp: Date.now()
            }));
          } catch (_) {}
          try {
            const url = new URL(window.location.href);
            url.searchParams.delete('new');
            url.searchParams.set('board', String(data.id));
            window.history.replaceState({}, '', url);
          } catch (_) {}
          const boardNameInput = document.getElementById('boardName');
          if (boardNameInput) {
            boardNameInput.value = data.name || boardName;
          }
          return;
        }
      }
    }
  } catch (e) {
    console.warn('ensureStartupBoard failed:', e);
  }
}

/**
 * Apply template seed if requested
 */
async function applyTemplateSeedIfRequested() {
  try {
    const templateId = window.CD && window.CD.templateId ? String(window.CD.templateId) : '';
    const seedRaw = window.CD && window.CD.seedTemplate != null ? String(window.CD.seedTemplate).toLowerCase() : '';
    const shouldSeed = seedRaw === '1' || seedRaw === 'true' || seedRaw === 'yes';
    if (!templateId || !shouldSeed) return;

    let boardId = window.CD && window.CD.boardId ? Number(String(window.CD.boardId).replace(/^board-/, '')) : null;
    if (!boardId || Number.isNaN(boardId)) return;

    const onceKey = `collabodraw-template-seeded-${boardId}-${templateId}`;
    if (sessionStorage.getItem(onceKey) === '1') return;

    let hasExistingContent = false;
    try {
      const current = await fetch(`/api/boards/${boardId}/content`, { credentials: 'include' });
      if (current.ok) {
        const data = await current.json();
        hasExistingContent = !!(data && typeof data.elements === 'string' && data.elements.trim().length > 0);
      }
    } catch (_) {}
    if (hasExistingContent) {
      sessionStorage.setItem(onceKey, '1');
      return;
    }

    const res = await fetch(`/api/templates/use/${encodeURIComponent(templateId)}`, { credentials: 'include' });
    if (!res.ok) return;
    const tpl = await res.json();
    if (!tpl || !tpl.success) return;

    const container = document.getElementById('canvasElements');
    if (container) {
      container.innerHTML = (typeof tpl.elements === 'string') ? tpl.elements : '';
      document.querySelectorAll('.canvas-element').forEach(el => ElementManager.setupElementInteraction(el));
    }

    const settings = tpl.settings || {};
    if (settings.zoom) { AppState.zoomLevel = Number(settings.zoom) || AppState.zoomLevel; Canvas.updateZoom(); }
    if (settings.tool) { UIControls.selectTool(settings.tool); }
    if (settings.color) { UIControls.selectColor(settings.color); }

    History.saveState();
    sessionStorage.setItem(onceKey, '1');

    try {
      const url = new URL(window.location.href);
      url.searchParams.delete('seedTemplate');
      window.history.replaceState({}, '', url);
    } catch (_) {}

    UIControls.showNotification(`Template applied: ${tpl.title || templateId}`);
  } catch (e) {
    console.warn('Template seeding failed:', e);
  }
}

/**
 * Ensure server board and save
 */
async function ensureServerBoardAndSave(name) {
  try {
    if (!window.CD) window.CD = {};
    if (!window.CD.boardId) {
      let sessionCode = null;
      try {
        const qp = new URLSearchParams(window.location.search || '');
        if (qp.has('session')) sessionCode = qp.get('session');
      } catch {}

      if (sessionCode && sessionCode.trim()) {
        try {
          const r = await fetch('/api/boards/session', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code: sessionCode.trim() })
          });
          if (r.ok) {
            const data = await r.json();
            if (data && data.id) {
              window.CD.boardId = data.id;
              try {
                const bn = document.getElementById('boardName');
                if (bn) {
                  bn.value = (data.name || bn.value || `Session ${sessionCode}`);
                  bn.disabled = true;
                }
              } catch {}
              try {
                const url = new URL(window.location.href);
                url.searchParams.delete('session');
                url.searchParams.set('board', String(data.id));
                window.history.replaceState({}, '', url);
              } catch {}
            }
          }
        } catch {}
      }

      if (!window.CD.boardId) {
        const res = await fetch('/api/boards/new', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: name || document.getElementById('boardName').value || 'Untitled Board' })
        });
        if (res.ok) {
          const data = await res.json();
          if (data && data.id) {
            window.CD.boardId = data.id;
            try {
              const bn = document.getElementById('boardName');
              if (bn) {
                bn.value = (name || data.name || bn.value || 'Untitled Board');
                bn.disabled = true;
              }
            } catch(_) {}
            try {
              const url = new URL(window.location.href);
              url.searchParams.set('board', data.id);
              window.history.replaceState({}, '', url);
            } catch {}
          }
        }
      }
    }
    Storage.saveBoardState();
  } catch (e) {
    console.warn('Failed to ensure server board:', e);
  }
}

/**
 * Share board (copy link to clipboard)
 */
function shareBoard() {
  let shareUrl = `${window.location.origin}${window.location.pathname}`;
  try {
    const url = new URL(window.location.href);
    const currentBoardId = window.CD && window.CD.boardId ? String(window.CD.boardId) : null;
    const sessionCode = url.searchParams.get('session');

    if (currentBoardId && currentBoardId.trim()) {
      shareUrl = `${window.location.origin}${window.location.pathname}?board=${encodeURIComponent(currentBoardId)}`;
    } else if (sessionCode && sessionCode.trim()) {
      shareUrl = `${window.location.origin}${window.location.pathname}?session=${encodeURIComponent(sessionCode.trim())}`;
    }
  } catch (_) {}
  
  if (navigator.clipboard) {
    navigator.clipboard.writeText(shareUrl)
      .then(() => UIControls.showNotification('Board link copied to clipboard!'))
      .catch(() => UIControls.showNotification('Failed to copy link'));
  } else {
    UIControls.showNotification('Sharing not supported in this browser');
  }
}

/**
 * Export board as PNG
 */
function exportBoard() {
  try {
    const drawingCanvas = document.getElementById('drawingCanvas');
    if (!drawingCanvas) {
      UIControls.showNotification('Canvas not found');
      return;
    }
    
    const dataURL = drawingCanvas.toDataURL('image/png');
    const link = document.createElement('a');
    link.download = `${AppState.boardData.name || 'collabodraw-board'}-${Date.now()}.png`;
    link.href = dataURL;
    link.click();
    
    UIControls.showNotification('Board exported successfully!');
    console.log('✅ Board exported to PNG');
  } catch (error) {
    console.error('❌ Export failed:', error);
    UIControls.showNotification('Export failed');
  }
}

/**
 * Navigate home
 */
function goHome() {
  if (confirm('Are you sure you want to leave? Unsaved changes will be lost.')) {
    window.location.href = '/home';
  }
}

/**
 * GLOBAL WRAPPER FUNCTIONS
 * These functions are exposed globally for onclick handlers in HTML templates
 */

// History wrapper functions
function undo() { History.undo(); }
function redo() { History.redo(); }

// UI wrapper functions
function toggleTraybar() { UIControls.toggleTraybar(); }
function toggleTimer() { UIControls.toggleTimer(); }
function selectTool(tool) { UIControls.selectTool(tool); }
function selectColor(color) { UIControls.selectColor(color); }

// Canvas wrapper functions
function zoomIn() { Canvas.zoomIn(); }
function zoomOut() { Canvas.zoomOut(); }
function fitToScreen() { Canvas.fitToScreen(); }

// Storage wrapper functions
function saveBoard() { saveBoard_internal(); }
function saveBoard_internal() {
  Storage.saveBoardState();
  const statusEl = document.getElementById('saveStatus');
  if (statusEl) statusEl.textContent = 'Saved';
  UIControls.showNotification('Board saved successfully!');
  setTimeout(() => {
    if (statusEl) statusEl.textContent = 'Auto-saved';
  }, 2000);
}

// Already defined but ensure global
function shareBoard() { /* Already defined */ }
function exportBoard() { /* Already defined */ }
function goHome() { /* Already defined */ }
function showHelp() { UIControls.showHelp(); }

/**
 * Initialize tooltips using a single global tooltip element
 */
function initializeTooltips() {
  try {
    let globalTooltip = document.getElementById('globalTooltip');
    if (!globalTooltip) {
      globalTooltip = document.createElement('div');
      globalTooltip.id = 'globalTooltip';
      globalTooltip.className = 'tooltip';
      document.body.appendChild(globalTooltip);
    }

    const placeTooltipBelow = (el) => {
      if (!el) return;

      const rect = el.getBoundingClientRect();
      const docX = window.scrollX || window.pageXOffset || 0;
      const docY = window.scrollY || window.pageYOffset || 0;
      const gap = 10;

      // Ensure dimensions are measurable before positioning.
      globalTooltip.style.visibility = 'hidden';
      globalTooltip.style.position = 'absolute';
      globalTooltip.classList.add('show');
      
      // Get tooltip dimensions after showing
      const tipWidth = globalTooltip.offsetWidth || 150;
      const tipHeight = globalTooltip.offsetHeight || 30;

      // Center tooltip horizontally under the element
      let left = rect.left + (rect.width / 2) - (tipWidth / 2) + docX;
      const minLeft = docX + 8;
      const maxLeft = docX + window.innerWidth - tipWidth - 8;
      left = Math.max(minLeft, Math.min(left, maxLeft));

      // Position tooltip below the element with gap
      const top = rect.bottom + gap + docY;
      
      globalTooltip.style.left = `${left}px`;
      globalTooltip.style.top = `${top}px`;
      globalTooltip.style.visibility = '';
    };
    
    document.addEventListener('mouseover', (e) => {
      const el = e.target.closest('[data-tooltip]');
      if (!el) return;
      const text = el.getAttribute('data-tooltip');
      if (text) {
        globalTooltip.innerHTML = text;
        placeTooltipBelow(el);
        globalTooltip.classList.add('show');
      }
    });

    document.addEventListener('mousemove', (e) => {
      if (globalTooltip.classList.contains('show')) {
        const el = e.target.closest('[data-tooltip]');
        if (el) {
          placeTooltipBelow(el);
        }
      }
    });

    document.addEventListener('mouseout', (e) => {
      const el = e.target.closest('[data-tooltip]');
      if (!el) {
        globalTooltip.classList.remove('show');
      } else {
        // Hide if we leave the element entirely
         const related = e.relatedTarget;
         if (!el.contains(related)) {
             globalTooltip.classList.remove('show');
         }
      }
    });

    document.addEventListener('mousedown', () => {
      globalTooltip.classList.remove('show');
    });

    console.log('✅ Global tooltips initialized');
  } catch (e) {
    console.warn('⚠️ Tooltip initialization error:', e);
  }
}

/**
 * Attach event listeners to traybar buttons
 */
function attachTraybarListeners() {
  try {
    // Attach tool button listeners
    document.querySelectorAll('.tool-btn[data-tool]').forEach(btn => {
      btn.addEventListener('click', function() {
        const tool = this.getAttribute('data-tool');
        if (tool) {
          selectTool(tool);
        }
      });
    });
    
    // Attach undo/redo/save button listeners
    const undoBtn = document.getElementById('btnUndo');
    const redoBtn = document.getElementById('btnRedo');
    const saveBtn = document.getElementById('btnSave');
    
    if (undoBtn) undoBtn.addEventListener('click', () => undo());
    if (redoBtn) redoBtn.addEventListener('click', () => redo());
    if (saveBtn) saveBtn.addEventListener('click', () => saveBoard());
    
    console.log('✅ Traybar listeners attached');
  } catch (e) {
    console.warn('⚠️ Traybar listener attachment error:', e);
  }
}

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', async function() {
  await initializeApp();
  initializeTooltips();
  attachTraybarListeners();
});
