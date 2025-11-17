/**
 * CollaboDraw Whiteboard Application
 * Main JavaScript functionality for the collaborative whiteboard
 */

// Application State
let currentTool = 'select';
let currentColor = '#000000';
let isDrawing = false;
let selectedElements = [];
let clipboard = [];
let undoStack = [];
let redoStack = [];
let zoomLevel = 1;
let panX = 0;
let panY = 0;
let timerRunning = false;
let timerSeconds = 0;
let timerInterval;
let traybarVisible = true;
let users = [];
// Realtime state
let remoteCursors = {}; // { [userId]: { x, y, name, color } }
let wsBoardId = null;
let _lastParticipants = new Set();
let wsSubscriptions = { participants: null, cursors: null };
let boardData = {
  name: 'Untitled Board',
  elements: [],
  settings: {}
};
let mainCanvas = null;
let canvas = null;
let ctx = null;

// Configuration
const CONFIG = {
  AUTO_SAVE_INTERVAL: 30000, // 30 seconds
  MAX_UNDO_HISTORY: 50,
  CANVAS_WIDTH: 2000,
  CANVAS_HEIGHT: 1500,
  ZOOM_MIN: 0.1,
  ZOOM_MAX: 3,
  ZOOM_STEP: 1.2
};

/**
 * Initialize the application
 */
function initializeApp() {
  // Get DOM elements with safety checks
  canvas = document.getElementById('drawingCanvas');
  ctx = canvas?.getContext('2d');
  mainCanvas = document.getElementById('mainCanvas');
  
  // Guard: Exit if critical elements missing
  if (!canvas || !ctx || !mainCanvas) {
    console.error('❌ Critical DOM elements missing - cannot initialize');
    return;
  }
  
  console.log('✅ DOM elements loaded');
  // Set canvas size
  resizeCanvas();
  
  // Initialize tools and events
  initializeTools();
  setupEventListeners();
  
  // ✅ Set initial tool AFTER setup complete
  selectTool('pen');
  
  // Load saved state or create default
  loadBoardState();
  
  // Initialize user interface
  initializeUI();
  
  // ✅ Load board name from server
  loadBoardName();
  
  // Setup auto-save
  setInterval(autoSave, CONFIG.AUTO_SAVE_INTERVAL);
  
  // Ensure a valid server board exists (handles ?session= as well)
  ensureStartupBoard().then(() => {
    // Start real-time features after we have a valid board id
    startRealTimeSync();
  }).catch(() => {
    // Still try to start realtime (will no-op if board id unresolved)
    startRealTimeSync();
  });
  
  // Hide loading screen
  setTimeout(() => {
    const loading = document.getElementById('loading');
    if (loading) {
      loading.classList.remove('show');
    }
  }, 1000);

  // Wire import input change handler
  const importInput = document.getElementById('importInput');
  if (importInput && !importInput.__wired) {
    importInput.addEventListener('change', handleImportSelection);
    importInput.__wired = true;
  }

  // Handle pre-import payload or URL import parameter
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
          ensureServerBoardAndSave(base);
        } catch(e) {
          console.warn('⚠️ Failed to import JSON:', e);
        }
      } else if (payload && payload.kind === 'image' && typeof payload.data === 'string') {
        addImageToCanvas(payload.data, payload.name);
        const base = inferNameFromFile(payload.name || 'Imported Image');
        ensureServerBoardAndSave(base);
      } else if (params.has('import')) {
        setTimeout(() => importFile(), 300);
      }
    } else if (params.has('import')) {
      setTimeout(() => importFile(), 300);
    }
    
    console.log('✅ App initialization complete');
  } catch(e) {
    console.error('⚠️ Import handling error:', e);
  }
}

// Ensure there is a valid board on the server before collaborating/saving
async function ensureStartupBoard() {
  try {
    const qp = new URLSearchParams(window.location.search || '');
    const sessionCode = qp.get('session');
    let boardId = window.CD && window.CD.boardId ? Number(window.CD.boardId) : null;

    // If we already have a numeric board id, verify it exists
    if (Number.isFinite(boardId) && boardId > 0) {
      const resp = await fetch(`/api/boards/${boardId}`);
      if (resp.ok) {
        return; // board exists
      }
      // If not found, fall through to create
    }

    // If no board id or invalid, but we have a session code, resolve to a shared board id (find-or-create)
    if (sessionCode) {
      const res = await fetch('/api/boards/session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: sessionCode })
      });
      if (res.ok) {
        const data = await res.json();
        if (!window.CD) window.CD = {};
        window.CD.boardId = data.id;
        // Replace URL to stable ?board=ID and drop ?session=
        try {
          const url = new URL(window.location.href);
          url.searchParams.delete('session');
          url.searchParams.set('board', String(data.id));
          window.history.replaceState({}, '', url);
        } catch {}
        // Reflect name field
        const bn = document.getElementById('boardName');
        if (bn) { bn.value = data.name || bn.value || `Session ${sessionCode}`; bn.disabled = true; }
        return;
      } else {
        let errText = 'Failed to resolve session';
        try { const j = await res.json(); if (j && j.message) errText = j.message; } catch{}
        notify(errText + ` (code: ${sessionCode})`);
        console.warn('Failed to resolve session code:', sessionCode);
      }
    }
  } catch (e) {
    console.warn('ensureStartupBoard failed:', e);
  }
}

async function loadBoardName() {
  try {
    // ✅ FIX: Handle boardId as either string or number
    let boardId = window.CD?.boardId;
    
    if (!boardId) return;
    
    // Remove "board-" prefix if it exists, then parse as integer
    if (typeof boardId === 'string') {
      boardId = parseInt(boardId.replace(/^board-/, ''), 10);
    } else if (typeof boardId === 'number') {
      // Already a number, just use it
      boardId = parseInt(boardId, 10);
    }
    
    if (isNaN(boardId)) {
      console.error('❌ Invalid board ID:', boardId);
      return;
    }
    
    const response = await fetch(`/api/boards/${boardId}`);
    if (!response.ok) return;
    
    const data = await response.json();
    
    if (data && data.name) {
      boardData.name = data.name;
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
 * Initialize user interface elements
 */
function initializeUI() {
  // Initialize user avatars
  updateUserAvatars();
  
  // Initialize version history
  updateVersionHistory();
  
  // Initialize active users
  updateActiveUsers();
  
  // Set up board name editing (kept for fallback flows). If disabled, skip.
  const boardNameInput = document.getElementById('boardName');
  if (boardNameInput && !boardNameInput.disabled) {
    boardNameInput.addEventListener('blur', function() {
      boardData.name = this.value;
      saveState();
    });
  }
}

/**
 * Update user avatars in header
 */
function updateUserAvatars() {
  const userAvatars = document.getElementById('userAvatars');
  userAvatars.innerHTML = '';
  
  // Get current user from session/localStorage or create default
  const currentUser = getCurrentUser();
  // If realtime participants already loaded, keep them and ensure current user is present
  if (!Array.isArray(users) || users.length === 0) {
    users = [currentUser];
  } else if (!users.some(u => (u.id || u.userId) === (currentUser.id || currentUser.userId) || u.name === currentUser.name)) {
    users = [currentUser, ...users];
  }
  
  users.forEach((user, index) => {
    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.style.background = user.color;
    avatar.title = user.name + (index === 0 ? ' (You)' : '');
    avatar.textContent = user.initials;
    userAvatars.appendChild(avatar);
  });
}

/**
 * Get current user information
 */
function getCurrentUser() {
  // Prefer server-provided identity for consistent initials across pages
  const injected = (window.CD && (window.CD.currentUserName || window.CD.currentUserInitials))
    ? {
        name: window.CD.currentUserName || 'User',
        initials: (window.CD.currentUserInitials || (window.CD.currentUserName ? window.CD.currentUserName.substring(0,2) : 'U')).toUpperCase(),
      }
    : null;

  // Fallback to hidden DOM dataset if available
  const dataEl = document.getElementById('currentUserData');
  const ds = dataEl ? dataEl.dataset : null;
  const injected2 = (!injected && ds && (ds.name || ds.initials))
    ? { name: ds.name || 'User', initials: (ds.initials || (ds.name ? ds.name.substring(0,2) : 'U')).toUpperCase() }
    : null;

  // Load or initialize local profile
  let user = JSON.parse(localStorage.getItem('collabodraw-user') || '{}');
  if (!user.id) {
    user = { id: generateId(), name: 'User', initials: 'U', color: '#3b82f6' };
  }

  // Apply injected identity if present and persist for consistency
  const chosen = injected || injected2 || user;
  // Normalize initials
  chosen.initials = (chosen.initials || (chosen.name ? chosen.name.substring(0,2) : 'U')).toUpperCase();
  localStorage.setItem('collabodraw-user', JSON.stringify({ ...user, ...chosen }));
  return { ...user, ...chosen };
}

/**
 * Update version history panel
 */
function updateVersionHistory() {
  const versionHistory = document.getElementById('versionHistory');
  const versions = getVersionHistory();
  
  versionHistory.innerHTML = versions.map(version => `
    <div class="version-item" onclick="restoreVersion('${version.id}')">
      <span>🕐</span>
      <span>${version.timestamp} - ${version.description}</span>
    </div>
  `).join('');
}

/**
 * Get version history from localStorage
 */
function getVersionHistory() {
  const history = JSON.parse(localStorage.getItem('collabodraw-versions') || '[]');
  return history.slice(0, 10); // Show last 10 versions
}

/**
 * Update active users panel
 */
function updateActiveUsers() {
  const activeUsers = document.getElementById('activeUsers');
  // Ensure at least current user present locally
  if (!Array.isArray(users) || users.length === 0) {
    const cu = getCurrentUser();
    users = [cu];
  }
  const userCount = users.length;
  
  activeUsers.innerHTML = `
    <div style="margin-bottom: 8px; font-weight: 500;">Online (${userCount})</div>
    ${users.map(user => `
      <div style="display: flex; align-items: center; gap: 8px; padding: 4px 0;">
        <span style="background: ${user.color}; width: 12px; height: 12px; border-radius: 50%; display: inline-block;"></span>
        <span>${user.name}</span>
      </div>
    `).join('')}
  `;
}

/**
 * Save drawing to database
 */
function saveDrawingToDatabase() {
  const canvas = document.getElementById('drawingCanvas');
  if (!canvas) return;
  
  // ✅ FIX: Handle boardId as either string or number
  let boardId = window.CD?.boardId;
  
  if (!boardId) {
    console.error('❌ Board ID not available');
    // Attempt to create a board automatically, then retry once
    return ensureServerBoardAndSave(document.getElementById('boardName')?.value || 'Untitled Board');
  }
  
  // Remove "board-" prefix if it exists
  if (typeof boardId === 'string') {
    boardId = parseInt(boardId.replace(/^board-/, ''), 10);
  } else if (typeof boardId === 'number') {
    boardId = parseInt(boardId, 10);
  }
  
  if (isNaN(boardId)) {
      console.error('❌ Invalid board ID:', boardId);
      return;
  }
  
  const imageData = canvas.toDataURL('image/png');
  
  const formData = new FormData();
  formData.append('boardId', boardId);
  formData.append('imageData', imageData);
  
  fetch('/api/drawings/save-canvas', {
      method: 'POST',
      body: formData
  })
  .then(res => res.json())
  .then(data => {
      if (data.success) {
          console.log('💾 Canvas saved - Board:', boardId);
      } else {
          console.error('❌ Save failed:', data.message);
      }
  })
  .catch(err => console.error('❌ Save error:', err));
}

/**
* Load drawing from database
*/
function loadDrawingFromDatabase() {
  // ✅ FIX: Handle boardId as either string or number
  let boardId = window.CD?.boardId;
  
  if (!boardId) {
    console.error('❌ Board ID not available');
    return;
  }
  
  // Remove "board-" prefix if it exists
  if (typeof boardId === 'string') {
    boardId = parseInt(boardId.replace(/^board-/, ''), 10);
  } else if (typeof boardId === 'number') {
    boardId = parseInt(boardId, 10);
  }
  
  if (isNaN(boardId)) {
      console.error('❌ Invalid board ID:', boardId);
      return;
  }
  
  fetch(`/api/drawings/load-canvas/${boardId}`)
  .then(res => res.json())
  .then(data => {
      if (data.success && data.imageData) {
          const canvas = document.getElementById('drawingCanvas');
          const ctx = canvas?.getContext('2d');
          
          if (ctx) {
              const img = new Image();
              img.onload = () => {
                  ctx.clearRect(0, 0, canvas.width, canvas.height);
                  ctx.drawImage(img, 0, 0);
                  console.log('📥 Canvas loaded - Board:', boardId);
              };
              img.src = data.imageData;
          }
      } else {
          console.log('ℹ️ No canvas found - Board:', boardId);
      }
  })
  .catch(err => console.error('❌ Load error:', err));
}

/**
* Auto-save drawing every 30 seconds
*/
function startAutoSaveDrawing() {
  setInterval(() => {
      saveDrawingToDatabase();
  }, 30000);  // 30 seconds
  
  console.log('⏱️ Auto-save started (every 30s)');
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
  setTimeout(() => {
      loadDrawingFromDatabase();
      startAutoSaveDrawing();
      console.log('✅ Drawing system initialized with DB integration');
  }, 2000);
});

/**
 * Resize canvas to match viewport
 */
function resizeCanvas() {
  const rect = mainCanvas.getBoundingClientRect();
  canvas.width = rect.width;
  canvas.height = rect.height;
}

/**
 * Initialize tool functionality
 */
function initializeTools() {
  // Tool buttons
  document.querySelectorAll('.tool-btn').forEach(btn => {
    btn.addEventListener('click', function() {
      const tool = this.dataset.tool;
      if (tool) {
        selectTool(tool);
      }
    });
  });

  // Color picker
  document.querySelectorAll('.color-option').forEach(option => {
    option.addEventListener('click', function() {
      selectColor(this.dataset.color);
    });
  });
// ✅ Add eraser tool button listener
const eraserBtn = document.querySelector('[data-tool="eraser"]');
if (eraserBtn) {
    eraserBtn.addEventListener('click', activateEraser);
}
  // Toolbar buttons
  document.querySelectorAll('.toolbar-btn').forEach(btn => {
    btn.addEventListener('click', function() {
      const action = this.dataset.action;
      handleToolbarAction(action);
    });
  });
}

/**
 * Update canvas cursor based on current tool
 */
function updateCanvasCursor() {
  if (!mainCanvas) {
    console.warn('⚠️ Main canvas not available for cursor update');
    return;
  }

  try {
    // Reset classes
    mainCanvas.className = 'main-canvas';
    
    // Add traybar class if visible  
    if (traybarVisible) {
      mainCanvas.classList.add('traybar-visible');
    }
    
    // Add tool-specific cursor classes
    switch(currentTool) {
      case 'hand':
        mainCanvas.classList.add('hand-mode');
        break;
      case 'select':
        mainCanvas.classList.add('select-mode');
        break; 
      case 'eraser':
        mainCanvas.classList.add('eraser-mode');
        mainCanvas.style.cursor = 'cell';
        break;
      default:
        // Default cursor
        mainCanvas.style.cursor = 'default';
    }
  } catch(err) {
    console.error('❌ Error updating cursor:', err);
  }
}
/**
 * Select drawing/editing tool
 */
function selectTool(tool) {
  currentTool = tool;
  
  // Update UI
  document.querySelectorAll('.tool-btn').forEach(btn => {
    btn.classList.remove('active');
  });
  
  const toolBtn = document.querySelector(`[data-tool="${tool}"]`);
  if (toolBtn) {
    toolBtn.classList.add('active');
  }
  
  // Enable pointer events on drawing canvas only for drawing tools
  try {
    const drawCanvasEl = document.getElementById('drawingCanvas');
    if (drawCanvasEl) {
      const drawTools = new Set(['pen','highlighter','line','rectangle','circle','eraser']);
      if (drawTools.has(tool)) {
        drawCanvasEl.classList.add('active');
      } else {
        drawCanvasEl.classList.remove('active');
      }
    }
  } catch(_){ }

  // Update cursor style
  updateCanvasCursor();
  
  // Log tool change
  console.log(`🔧 Tool selected: ${tool}`);
}
/**
 * Trigger file import dialog
 */
function importFile() {
  const input = document.getElementById('importInput');
  if (!input) return showNotification('Import not available');
  input.value = '';
  input.click();
}

/**
 * Handle selected file(s) from hidden input
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
      // Try to read name from JSON, else use filename
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
    showNotification('Unsupported file. Please import an image or JSON.');
  }
}

async function ensureServerBoardAndSave(name) {
  try {
    if (!window.CD) window.CD = {};
    if (!window.CD.boardId) {
      // Prefer resolving a human session code to a shared board before creating a new board
      let sessionCode = null;
      try {
        const qp = new URLSearchParams(window.location.search || '');
        if (qp.has('session')) sessionCode = qp.get('session');
      } catch {}

      if (sessionCode && sessionCode.trim()) {
        try {
          const r = await fetch('/api/boards/session', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code: sessionCode.trim() })
          });
          if (r.ok) {
            const data = await r.json();
            if (data && data.id) {
              window.CD.boardId = data.id;
              // Reflect UI and URL
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

      // If still no board id (no session or resolver failed), create a fresh board
      if (!window.CD.boardId) {
        const res = await fetch('/api/boards/new', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: name || document.getElementById('boardName').value || 'Untitled Board' })
        });
        if (res.ok) {
          const data = await res.json();
          if (data && data.id) {
            window.CD.boardId = data.id;
            // Reflect final name and lock editing
            try {
              const bn = document.getElementById('boardName');
              if (bn) {
                bn.value = (name || data.name || bn.value || 'Untitled Board');
                bn.disabled = true;
              }
            } catch(_) {}
            // update URL to include board for refresh continuity
            try {
              const url = new URL(window.location.href);
              url.searchParams.set('board', data.id);
              window.history.replaceState({}, '', url);
            } catch {}
          }
        }
      }
    }
    // Save snapshot now that we (likely) have a board id
    saveBoardState();
  } catch (e) {
    console.warn('Failed to ensure server board:', e);
  }
}

function inferNameFromFile(name) {
  if (!name) return 'Imported Board';
  return name.replace(/\.[^.]+$/, '').slice(0, 100) || 'Imported Board';
}

/**
 * Add an imported image onto the canvas area as a movable element
 */
function addImageToCanvas(dataUrl, name) {
  const container = document.getElementById('canvasElements');
  if (!container) return;

  const id = generateId();
  const wrapper = document.createElement('div');
  wrapper.className = 'canvas-element image-element';
  wrapper.dataset.id = id;
  // place near center-ish
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

  // Add resize handles for parity with other elements
  wrapper.appendChild(img);
  ['nw','ne','sw','se'].forEach(pos => {
    const h = document.createElement('div');
    h.className = 'resize-handle ' + pos;
    wrapper.appendChild(h);
  });

  container.appendChild(wrapper);
  setupElementInteraction(wrapper);
  saveState();
  showNotification('Image imported');
}

/**
 * Import a board from a JSON string
 */
function importBoardFromJSON(jsonText) {
  try {
    const parsed = JSON.parse(jsonText);
    // Two supported shapes: our boardData object or {elements: html}
    if (parsed && parsed.elements) {
      const container = document.getElementById('canvasElements');
      container.innerHTML = parsed.elements;
      container.querySelectorAll('.canvas-element').forEach(setupElementInteraction);
      if (parsed.name) {
        const nameInput = document.getElementById('boardName');
        if (nameInput) nameInput.value = parsed.name;
      }
      saveState();
      showNotification('Board imported');
      return;
    }
    if (parsed && parsed.board && parsed.board.elements) {
      const container = document.getElementById('canvasElements');
      container.innerHTML = parsed.board.elements;
      container.querySelectorAll('.canvas-element').forEach(setupElementInteraction);
      saveState();
      showNotification('Board imported');
      return;
    }
    // Fallback: if looks like our saved boardData
    if (parsed && (parsed.settings || parsed.name)) {
      boardData = parsed;
      document.getElementById('canvasElements').innerHTML = boardData.elements || '';
      document.querySelectorAll('.canvas-element').forEach(setupElementInteraction);
      if (boardData.name) {
        const nameInput = document.getElementById('boardName');
        if (nameInput) nameInput.value = boardData.name;
      }
      showNotification('Board imported');
      saveState();
    } else {
      showNotification('Unrecognized JSON format');
    }
  } catch (e) {
    console.error('Failed to import JSON:', e);
    showNotification('Failed to import JSON');
  }
}

/**
 * Select drawing color
 */
function selectColor(color) {
  currentColor = color;
  
  // Update UI
  document.querySelectorAll('.color-option').forEach(option => {
    option.classList.remove('selected');
  });
  document.querySelector(`[data-color="${color}"]`).classList.add('selected');
}

/**
 * Erase elements from canvas (DOM-based)
 */
function activateEraser() {
  selectTool('eraser');
  
  // Add eraser mode styling
  const mainCanvas = document.getElementById('mainCanvas');
  mainCanvas.classList.add('eraser-mode');
  
  console.log('🧹 Eraser activated');
}

/**
* Handle eraser click on canvas elements
*/
function eraserClick(e) {
  if (currentTool !== 'eraser') return;
  
  const container = document.getElementById('canvasElements');
  if (!container.contains(e.target)) return;
  
  // Find the closest canvas-element
  let element = e.target.closest('.canvas-element');
  
  if (element) {
      // Save state before erasing
      saveState();
      
      // Remove with animation
      element.style.opacity = '0';
      element.style.transform = 'scale(0.95)';
      
      setTimeout(() => {
          element.remove();
          console.log(`🗑️ Erased element: ${element.dataset.id}`);
      }, 200);
      
      // Save again after erasing
      saveState();
  }
}


/**
 * Handle toolbar button actions
 */
function handleToolbarAction(action) {
  switch(action) {
    case 'move':
      selectTool('hand');
      break;
    case 'zoom-in':
      zoomIn();
      break;
    case 'fit':
      fitToScreen();
      break;
  }
}

function setupEventListeners() {
  // Canvas drawing events
  if (canvas) {
    canvas.addEventListener('mousedown', startDrawing);
    canvas.addEventListener('mousemove', draw);
    canvas.addEventListener('mouseup', stopDrawing);
    canvas.addEventListener('mouseout', stopDrawing);
  } else {
    console.warn('⚠️ Drawing canvas not found, strokes will not broadcast');
  }

  // ✅ FIXED: Canvas interaction events - Now with eraser support
  mainCanvas.addEventListener('click', (e) => {
    // ✅ CHECK ERASER FIRST
    if (currentTool === 'eraser') {
      handleEraserClick(e);
      return; // Exit early, don't process other clicks
    }
    
    // Handle other click events
    handleCanvasClick(e);
  });
  
  mainCanvas.addEventListener('contextmenu', showContextMenu);
  mainCanvas.addEventListener('mousedown', handleCanvasMouseDown);
  mainCanvas.addEventListener('mousemove', handleCanvasMouseMove);
  mainCanvas.addEventListener('mouseup', handleCanvasMouseUp);
  
  // ✅ REMOVED: Eraser event listener (not valid)
  // mainCanvas.addEventListener('eraser', handleCanvasClick);  ← DELETE THIS LINE
  
  // Keyboard shortcuts
  document.addEventListener('keydown', handleKeyboard);
  
  // Window events
  window.addEventListener('resize', resizeCanvas);
  
  // Context menu
  document.addEventListener('click', hideContextMenu);
  
  // Context menu items
  document.querySelectorAll('.menu-item').forEach(item => {
    item.addEventListener('click', function() {
      handleContextAction(this.dataset.action);
      hideContextMenu();
    });
  });
}
/**
 * Handle eraser click on canvas elements
 */
function handleEraserClick(e) {
  console.log('🧹 Eraser click detected:', e.target);
  
  if (currentTool !== 'eraser') {
      return;
  }
  
  const container = document.getElementById('canvasElements');
  if (!container) {
      console.warn('⚠️ Canvas container not found');
      return;
  }
  
  // Find the closest canvas-element that was clicked
  const element = e.target.closest('.canvas-element');
  
  if (element) {
      console.log(`✂️ Erasing element`);
      
      // Save state before erasing
      saveState();
      
      // Remove with animation
      element.style.opacity = '0';
      element.style.transform = 'scale(0.95)';
      element.style.transition = 'all 0.2s ease';
      
      // Remove after animation
      setTimeout(() => {
          element.remove();
          console.log(`✅ Element erased`);
          
          // Save state after erasing
          saveState();
          
          // Broadcast to other users
          broadcastChange('erase', {
              elementId: element.dataset.id,
              timestamp: Date.now()
          });
      }, 200);
  } else {
      console.log('ℹ️ Click on an element to erase it');
  }
}


/**
 * Start drawing on canvas
 */
function startDrawing(e) {
  if (!['pen', 'highlighter', 'line', 'rectangle', 'circle'].includes(currentTool)) return;
  
  isDrawing = true;
  // Initialize stroke capture for realtime broadcast
  window._currentStroke = {
    points: [],
    color: currentColor,
    tool: currentTool,
    width: (currentTool === 'highlighter' ? 8 : 2),
    alpha: (currentTool === 'highlighter' ? 0.5 : 1)
  };
  const rect = canvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  if (window._currentStroke) window._currentStroke.points.push([x, y]);
  
  ctx.beginPath();
  ctx.moveTo(x, y);
  
  // Set drawing properties
  ctx.strokeStyle = currentColor;
  ctx.lineWidth = currentTool === 'highlighter' ? 8 : 2;
  ctx.lineCap = 'round';
  ctx.globalAlpha = currentTool === 'highlighter' ? 0.5 : 1;
}

/**
 * Continue drawing on canvas
 */
function draw(e) {
  if (!isDrawing) return;
  
  const rect = canvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  if (window._currentStroke) window._currentStroke.points.push([x, y]);
  // Progressive broadcast every 10 points for near real-time remote rendering
  if (window._currentStroke && window._currentStroke.points.length % 10 === 0) {
    try {
      if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
        const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
        CollaboSocket.publishElement(boardNumeric, {
          kind: 'stroke',
          payload: {
            points: window._currentStroke.points.slice(-10), // send recent segment
            color: window._currentStroke.color,
            width: window._currentStroke.width,
            alpha: window._currentStroke.alpha,
            tool: window._currentStroke.tool,
            partial: true,
            strokeId: window._currentStroke.id || (window._currentStroke.id = generateId())
          }
        });
      }
    } catch(err){ /* silent */ }
  }
  
  if (currentTool === 'pen' || currentTool === 'highlighter') {
    ctx.lineTo(x, y);
    ctx.stroke();
  }
}

function stopDrawing() {
  if (!isDrawing) return;
  
  isDrawing = false;
  ctx.closePath();
  
  // ✅ Save the drawing strokes to undo history
  const canvasImage = canvas.toDataURL('image/png');
  addCanvasElement({
    id: generateId(),
    type: 'drawing',
    timestamp: Date.now(),
    image: canvasImage,
    tool: currentTool,
    user: getCurrentUser().id
  });
  // Broadcast stroke vector data instead of full image for efficiency
  try {
    if (window.CD && window.CD.boardId && window._currentStroke && typeof CollaboSocket !== 'undefined') {
      const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
      CollaboSocket.publishElement(boardNumeric, {
        kind: 'stroke',
        payload: {
          points: window._currentStroke.points,
          color: window._currentStroke.color,
          width: window._currentStroke.width,
          alpha: window._currentStroke.alpha,
          tool: window._currentStroke.tool,
          strokeId: window._currentStroke.id || (window._currentStroke.id = generateId())
        }
      });
    }
  } catch(e){ console.warn('Stroke broadcast failed', e); }
  window._currentStroke = null;
  
  // ✅ Save state to undo stack
  saveState();
  // ✅ Also persist a canvas snapshot so refresh restores drawing
  try { saveBoardState(); } catch(_){ }
}
function handleEraserClick(e) {
  if (currentTool !== 'eraser') return;
  
  console.log('🧹 Eraser mode active');
  
  // Get canvas coordinates
  const rect = canvas.getBoundingClientRect();
  const eraserX = e.clientX - rect.left;
  const eraserY = e.clientY - rect.top;
  const eraserRadius = 20; // Eraser brush size
  
  // Clear circular area on canvas
  ctx.clearRect(
    eraserX - eraserRadius,
    eraserY - eraserRadius,
    eraserRadius * 2,
    eraserRadius * 2
  );
  
  console.log('✅ Erased at:', eraserX, eraserY);
  
  // Save state
  saveState();
  // Broadcast erase action
  try {
    if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
      const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
      CollaboSocket.publishElement(boardNumeric, {
        kind: 'erase',
        payload: { x: eraserX, y: eraserY, radius: eraserRadius }
      });
    }
  } catch(e){}
  // Also persist a canvas snapshot after erase so refresh shows latest bitmap
  try { saveBoardState(); } catch(_){ }
}

function handleCanvasClick(e) {
  const rect = mainCanvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  
  // ✅ Handle eraser tool
  if (currentTool === 'eraser') {
      handleEraserClick(e);
      return;
  }
  
  switch(currentTool) {
      case 'sticky':
          createStickyNote(x, y);
          break;
      case 'text':
          createTextElement(x, y);
          break;
  }
}


/**
 * Create a new sticky note
 */
function createStickyNote(x, y) {
  const stickyId = generateId();
  const sticky = document.createElement('div');
  sticky.className = 'canvas-element sticky-note';
  sticky.style.left = x + 'px';
  sticky.style.top = y + 'px';
  sticky.dataset.id = stickyId;
  
  sticky.innerHTML = `
    <input type="text" class="sticky-title" value="New Note" placeholder="Note title">
    <textarea class="sticky-content" placeholder="Add your thoughts..."></textarea>
    <div class="sticky-dots">
      <div class="dot"></div>
    </div>
    <div class="resize-handle nw"></div>
    <div class="resize-handle ne"></div>
    <div class="resize-handle sw"></div>
    <div class="resize-handle se"></div>
  `;
  
  document.getElementById('canvasElements').appendChild(sticky);
  setupElementInteraction(sticky);
  selectElement(sticky);
  saveState();
  // Realtime broadcast of new sticky note
  try {
    if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
      const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
      CollaboSocket.publishElement(boardNumeric, {
        kind: 'sticky',
        payload: { id: stickyId, x, y, title: 'New Note', content: '' }
      });
    }
  } catch(e){}
  
  // Broadcast updates (debounced)
  const titleInput = sticky.querySelector('.sticky-title');
  const contentArea = sticky.querySelector('.sticky-content');
  let _stickyTimer;
  function queueStickyUpdate(){
    clearTimeout(_stickyTimer);
    _stickyTimer = setTimeout(()=>{
      try {
        if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
          const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
          CollaboSocket.publishElement(boardNumeric, {
            kind: 'sticky-update',
            payload: { id: stickyId, title: titleInput.value, content: contentArea.value }
          });
        }
      } catch(e){}
    }, 300);
  }
  if (titleInput) titleInput.addEventListener('input', queueStickyUpdate);
  if (contentArea) contentArea.addEventListener('input', queueStickyUpdate);

  return sticky;
}

/**
 * Create a new text element
 */
function createTextElement(x, y) {
  const textId = generateId();
  const textEl = document.createElement('div');
  textEl.className = 'canvas-element';
  textEl.style.left = x + 'px';
  textEl.style.top = y + 'px';
  textEl.dataset.id = textId;
  
  const input = document.createElement('input');
  input.type = 'text';
  input.value = 'Text';
  input.style.border = 'none';
  input.style.background = 'transparent';
  input.style.outline = 'none';
  input.style.fontSize = '16px';
  input.style.color = currentColor;
  
  textEl.appendChild(input);
  document.getElementById('canvasElements').appendChild(textEl);
  
  input.focus();
  input.select();
  
  setupElementInteraction(textEl);
  saveState();
  // Realtime broadcast of new text element
  try {
    if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
      const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
      CollaboSocket.publishElement(boardNumeric, {
        kind: 'text',
        payload: { id: textId, x, y, value: 'Text' }
      });
    // Broadcast text updates
    const inputEl = textEl.querySelector('input');
    let _textTimer;
    if (inputEl) {
      inputEl.addEventListener('input', () => {
        clearTimeout(_textTimer);
        _textTimer = setTimeout(()=>{
          try {
            if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
              const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
              CollaboSocket.publishElement(boardNumeric, {
                kind: 'text-update',
                payload: { id: textId, value: inputEl.value }
              });
            }
          } catch(e){}
        }, 250);
      });
    }
    }
  } catch(e){}
}

/**
 * Setup drag and drop and interaction for canvas elements
 */
function setupElementInteraction(element) {
  let isDragging = false;
  let startX, startY, startLeft, startTop;
  
  element.addEventListener('mousedown', function(e) {
    if (currentTool !== 'select') return;
    
    e.stopPropagation();
    isDragging = true;
    startX = e.clientX;
    startY = e.clientY;
    
    const rect = element.getBoundingClientRect();
    const canvasRect = mainCanvas.getBoundingClientRect();
    startLeft = rect.left - canvasRect.left;
    startTop = rect.top - canvasRect.top;
    
    selectElement(element);
    element.classList.add('dragging');
  });
  
  document.addEventListener('mousemove', function(e) {
    if (!isDragging) return;
    
    const deltaX = e.clientX - startX;
    const deltaY = e.clientY - startY;
    
    element.style.left = (startLeft + deltaX) + 'px';
    element.style.top = (startTop + deltaY) + 'px';
  });
  
  document.addEventListener('mouseup', function() {
    if (!isDragging) return;
    
    isDragging = false;
    element.classList.remove('dragging');
    
    saveState();
    // Realtime broadcast element move
    try {
      if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
        const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
        CollaboSocket.publishElement(boardNumeric, {
          kind: 'move',
          payload: {
            id: element.dataset.id,
            x: parseInt(element.style.left, 10) || 0,
            y: parseInt(element.style.top, 10) || 0
          }
        });
      }
    } catch(_){ }
  });
  
  // Double-click to edit
  element.addEventListener('dblclick', function() {
    editElement(element);
  });
}

/**
 * Select a canvas element
 */
function selectElement(element) {
  // Clear previous selection
  selectedElements.forEach(el => el.classList.remove('selected'));
  selectedElements = [];
  
  // Add to selection
  element.classList.add('selected');
  selectedElements.push(element);
  
  // Update properties panel
  updatePropertiesPanel(element);
}

/**
 * Edit a canvas element
 */
function editElement(element) {
  const inputs = element.querySelectorAll('input, textarea');
  inputs.forEach(input => {
    input.removeAttribute('readonly');
    input.focus();
    if (input.select) input.select();
    
    input.addEventListener('blur', function() {
      input.setAttribute('readonly', 'true');
      saveState();
    });
    
    input.addEventListener('keydown', function(e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        input.blur();
      }
    });
  });
}

/**
 * Show context menu
 */
function showContextMenu(e) {
  e.preventDefault();
  
  const contextMenu = document.getElementById('contextMenu');
  contextMenu.style.left = e.clientX + 'px';
  contextMenu.style.top = e.clientY + 'px';
  contextMenu.classList.add('show');
}

/**
 * Hide context menu
 */
function hideContextMenu() {
  document.getElementById('contextMenu').classList.remove('show');
}

/**
 * Handle context menu actions
 */
function handleContextAction(action) {
  switch(action) {
    case 'edit':
      if (selectedElements.length > 0) {
        editElement(selectedElements[0]);
      }
      break;
    case 'duplicate':
      duplicateSelected();
      break;
    case 'copy':
      copySelected();
      break;
    case 'paste':
      pasteFromClipboard();
      break;
    case 'delete':
      deleteSelected();
      break;
    case 'bring-front':
      bringToFront();
      break;
    case 'send-back':
      sendToBack();
      break;
  }
}

/**
 * Duplicate selected elements
 */
function duplicateSelected() {
  selectedElements.forEach(element => {
    const clone = element.cloneNode(true);
    clone.dataset.id = generateId();
    
    // Offset position
    const left = parseInt(element.style.left) + 20;
    const top = parseInt(element.style.top) + 20;
    clone.style.left = left + 'px';
    clone.style.top = top + 'px';
    
    document.getElementById('canvasElements').appendChild(clone);
    setupElementInteraction(clone);
  });
  
  saveState();
  showNotification('Elements duplicated');
}

/**
 * Delete selected elements
 */
function deleteSelected() {
  selectedElements.forEach(element => {
    element.remove();
  });
  selectedElements = [];
  saveState();
  showNotification('Elements deleted');
}

/**
 * Copy selected elements to clipboard
 */
function copySelected() {
  clipboard = selectedElements.map(el => ({
    html: el.outerHTML,
    id: el.dataset.id
  }));
  showNotification('Copied to clipboard');
}

/**
 * Paste from clipboard
 */
function pasteFromClipboard() {
  if (clipboard.length === 0) {
    showNotification('Nothing to paste');
    return;
  }
  
  const container = document.getElementById('canvasElements');
  clipboard.forEach(item => {
    const temp = document.createElement('div');
    temp.innerHTML = item.html;
    const element = temp.firstChild;
    element.dataset.id = generateId();
    
    // Offset position
    const left = parseInt(element.style.left) + 30;
    const top = parseInt(element.style.top) + 30;
    element.style.left = left + 'px';
    element.style.top = top + 'px';
    
    container.appendChild(element);
    setupElementInteraction(element);
  });
  
  saveState();
  showNotification('Pasted from clipboard');
}

/**
 * Bring selected elements to front
 */
function bringToFront() {
  selectedElements.forEach(element => {
    element.style.zIndex = '1000';
  });
  saveState();
}

/**
 * Send selected elements to back
 */
function sendToBack() {
  selectedElements.forEach(element => {
    element.style.zIndex = '1';
  });
  saveState();
}
/**
 * Handle keyboard shortcuts
 */
function handleKeyboard(e) {
  // Ignore if typing in input fields
  if (e.target.matches('input, textarea')) return;
  
  // Ctrl/Cmd shortcuts
  if (e.ctrlKey || e.metaKey) {
    switch(e.key.toLowerCase()) {
      case 'z':
        e.preventDefault();
        undo();
        break;
      case 'y':
        e.preventDefault();
        redo();
        break;
      case 'c':
        e.preventDefault();
        copySelected();
        break;
      case 'v':
        e.preventDefault();
        pasteFromClipboard();
        break;
      case 's':
        e.preventDefault();
        saveBoard();
        break;
    }
    return;
  }
  
  // Tool selection shortcuts (no Ctrl required)
  switch(e.key.toLowerCase()) {
    case 'v':
      selectTool('select');
      break;
    case 'p':
      selectTool('pen');
      break;
    case 'h':
      selectTool('highlighter');
      break;
    case 't':
      selectTool('text');
      break;
    case 's':
      selectTool('sticky');
      break;
    case 'r':
      selectTool('rectangle');
      break;
    case 'o':
      selectTool('circle');
      break;
    case 'l':
      selectTool('line');
      break;
    case 'a':
      selectTool('arrow');
      break;
    case 'e':  // ✅ ERASER TOOL - E key
      selectTool('eraser');
      break;
    case ' ':
      e.preventDefault();
      selectTool('hand');
      break;
    case 'delete':
      deleteSelected();
      break;
    case 'escape':
      selectedElements.forEach(el => el.classList.remove('selected'));
      selectedElements = [];
      break;
    case 'f1':
      e.preventDefault();
      showHelp();
      break;
    case '+':
    case '=':
      zoomIn();
      break;
    case '-':
      zoomOut();
      break;
    case '0':
      fitToScreen();
      break;
  }
}

/**
 * Zoom functions
 */
function zoomIn() {
  zoomLevel = Math.min(zoomLevel * CONFIG.ZOOM_STEP, CONFIG.ZOOM_MAX);
  updateZoom();
}

function zoomOut() {
  zoomLevel = Math.max(zoomLevel / CONFIG.ZOOM_STEP, CONFIG.ZOOM_MIN);
  updateZoom();
}

function fitToScreen() {
  zoomLevel = 1;
  panX = 0;
  panY = 0;
  updateZoom();
}

function updateZoom() {
  mainCanvas.style.transform = `scale(${zoomLevel}) translate(${panX}px, ${panY}px)`;
  document.getElementById('zoomLevel').textContent = Math.round(zoomLevel * 100) + '%';
}
/**
 * Enhanced Undo/Redo/Save functionality with state management
 */

// State management
let lastSaveState = null;
let isSaving = false;

/**
 * Create a snapshot of the current state
 */
function createStateSnapshot() {
    const container = document.getElementById('canvasElements');
    if (!container) {
        console.warn('⚠️ Canvas container not found');
        return null;
    }
    
    return {
        html: container.innerHTML,
        boardName: boardData.name,
        timestamp: Date.now(),
        elementCount: container.querySelectorAll('.canvas-element').length,
        // Add checksum for validation
        checksum: generateChecksum(container.innerHTML)
    };
}

/**
 * Generate a simple checksum for state validation
 */
function generateChecksum(html) {
    let hash = 0;
    for (let i = 0; i < html.length; i++) {
        const char = html.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash).toString(16);
}

/**
 * Save current state to undo stack
 */
function saveState() {
  // ✅ Save current canvas state to undo stack
  const canvasData = {
    imageData: ctx.getImageData(0, 0, canvas.width, canvas.height),
    timestamp: Date.now(),
    tool: currentTool
  };
  
  undoStack.push(canvasData);
  
  // ✅ Limit undo history size
  if (undoStack.length > CONFIG.MAX_UNDO_HISTORY) {
    undoStack.shift();
  }
  
  // ✅ Clear redo stack when new action performed
  redoStack = [];
  
  console.log(`💾 State saved (${undoStack.length} states)`);
}

function undo() {
  if (undoStack.length === 0) {
    console.warn('⏳ Nothing to undo');
    return;
  }
  
  // Save current state to redo
  const currentState = {
    imageData: ctx.getImageData(0, 0, canvas.width, canvas.height),
    timestamp: Date.now(),
    tool: currentTool
  };
  redoStack.push(currentState);
  
  // Restore previous state
  const previousState = undoStack.pop();
  ctx.putImageData(previousState.imageData, 0, 0);
  
  console.log(`↶ Undo performed (${undoStack.length} states left)`);
}

function redo() {
  if (redoStack.length === 0) {
    console.warn('⏳ Nothing to redo');
    return;
  }
  
  // Save current state to undo
  const currentState = {
    imageData: ctx.getImageData(0, 0, canvas.width, canvas.height),
    timestamp: Date.now(),
    tool: currentTool
  };
  undoStack.push(currentState);
  
  // Restore next state
  const nextState = redoStack.pop();
  ctx.putImageData(nextState.imageData, 0, 0);
  
  console.log(`↷ Redo performed (${redoStack.length} states left)`);
}

/**
 * Restore element interactions after undo/redo
 */
function restoreElementInteractions() {
    const container = document.getElementById('canvasElements');
    if (!container) return;
    
    // Re-setup all element interactions
    container.querySelectorAll('.canvas-element').forEach((element) => {
        setupElementInteraction(element);
    });
    
    // Restore any other event listeners or state
    console.log('🔗 Element interactions restored');
}

/**
 * Update undo/redo button states
 */
function updateUndoRedoButtons() {
    const undoBtn = document.getElementById('btnUndo');
    const redoBtn = document.getElementById('btnRedo');
    
    if (undoBtn) {
        undoBtn.disabled = undoStack.length === 0;
        undoBtn.classList.toggle('disabled', undoStack.length === 0);
    }
    
    if (redoBtn) {
        redoBtn.disabled = redoStack.length === 0;
        redoBtn.classList.toggle('disabled', redoStack.length === 0);
    }
}

/**
 * Clear all undo/redo history
 */
function clearHistory() {
    undoStack = [];
    redoStack = [];
    lastSaveState = null;
    updateUndoRedoButtons();
    console.log('🗑️ Undo/Redo history cleared');
}

/**
 * Manual save to server (persists to database)
 */
function manualSave() {
    if (isSaving) {
        console.log('⏳ Save in progress...');
        return;
    }
    
    isSaving = true;
    const boardId = getBoardIdFromURL();
    const container = document.getElementById('canvasElements');
    
    if (!container) {
        console.error('❌ Canvas container not found');
        isSaving = false;
        return;
    }
    
    const saveData = {
        boardId: boardId,
        content: container.innerHTML,
        boardName: boardData.name,
        timestamp: new Date().toISOString()
    };
    
    // Send to server
    fetch(`/api/boards/${boardId}/save`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(saveData)
    })
    .then(response => {
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        return response.json();
    })
    .then(data => {
        console.log('✅ Board saved successfully:', data);
        showNotification('💾 Saved successfully');
        isSaving = false;
    })
    .catch(error => {
        console.error('❌ Error saving board:', error);
        showNotification('❌ Error saving board');
        isSaving = false;
    });
}

/**
 * Auto-save functionality (runs periodically)
 */
function setupAutoSave() {
    setInterval(() => {
        const boardId = getBoardIdFromURL();
        const container = document.getElementById('canvasElements');
        
        if (!container || !boardId) return;
        
        const currentState = createStateSnapshot();
        
        // Only auto-save if state has changed since last save
        if (!lastSaveState || lastSaveState.checksum !== currentState.checksum) {
            console.log('🔄 Auto-saving...');
            manualSave();
        }
    }, CONFIG.AUTO_SAVE_INTERVAL);
}

/**
 * Setup keyboard shortcuts for undo/redo/save
 */
function setupUndoRedoShortcuts() {
    document.addEventListener('keydown', (e) => {
        // Undo: Ctrl+Z (or Cmd+Z on Mac)
        if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
            e.preventDefault();
            undo();
        }
        
        // Redo: Ctrl+Y or Ctrl+Shift+Z (or Cmd+Shift+Z on Mac)
        if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.shiftKey && e.key === 'z'))) {
            e.preventDefault();
            redo();
        }
        
        // Save: Ctrl+S (or Cmd+S on Mac)
        if ((e.ctrlKey || e.metaKey) && e.key === 's') {
            e.preventDefault();
            manualSave();
        }
        
        // Eraser: E key
        if (e.key === 'e' || e.key === 'E') {
            selectTool('eraser');
        }
    });
}

/**
 * Initialize undo/redo system
 */
function initializeUndoRedo() {
    // Create initial state snapshot
    const initialState = createStateSnapshot();
    if (initialState) {
        lastSaveState = initialState;
    }
    
    // Setup keyboard shortcuts
    setupUndoRedoShortcuts();
    
    // Setup auto-save
    setupAutoSave();
    
    // Setup button listeners
    const undoBtn = document.getElementById('btnUndo');
    const redoBtn = document.getElementById('btnRedo');
    const saveBtn = document.getElementById('btnSave');
    
    if (undoBtn) {
        undoBtn.addEventListener('click', undo);
    }
    
    if (redoBtn) {
        redoBtn.addEventListener('click', redo);
    }
    
    if (saveBtn) {
        saveBtn.addEventListener('click', manualSave);
    }
    
    // Update button states
    updateUndoRedoButtons();
    
    console.log('✅ Undo/Redo system initialized');
}

// Call this in your initializeApp() function

/**
 * Timer functionality
 */
function toggleTimer() {
  if (timerRunning) {
    clearInterval(timerInterval);
    timerRunning = false;
  } else {
    timerInterval = setInterval(() => {
      timerSeconds++;
      updateTimerDisplay();
    }, 1000);
    timerRunning = true;
  }
}

function updateTimerDisplay() {
  const minutes = Math.floor(timerSeconds / 60);
  const seconds = timerSeconds % 60;
  const display = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  document.getElementById('timerDisplay').textContent = display;
}

/**
 * Toggle traybar visibility
 */
function toggleTraybar() {
  const traybar = document.getElementById('traybar');
  const toggle = document.querySelector('.traybar-toggle');
  const mainCanvas = document.getElementById('mainCanvas');
  
  traybarVisible = !traybarVisible;
  
  if (traybarVisible) {
    traybar.classList.remove('hidden');
    toggle.innerHTML = '🔧';
    mainCanvas.classList.add('traybar-visible');
  } else {
    traybar.classList.add('hidden');
    toggle.innerHTML = '◀';
    mainCanvas.classList.remove('traybar-visible');
  }
}

/**
 * Update properties panel for selected element
 */
function updatePropertiesPanel(element) {
  const bgColor = document.getElementById('bgColor');
  const borderColor = document.getElementById('borderColor');
  const opacity = document.getElementById('opacity');
  
  if (!bgColor || !borderColor || !opacity) return;
  
  // Update property inputs based on selected element
  const computedStyle = window.getComputedStyle(element);
  bgColor.value = rgbToHex(computedStyle.backgroundColor);
  borderColor.value = rgbToHex(computedStyle.borderColor);
  opacity.value = Math.round(parseFloat(computedStyle.opacity) * 100);
  
  // Add event listeners
  bgColor.onchange = () => {
    element.style.backgroundColor = bgColor.value;
    saveState();
  };
  
  borderColor.onchange = () => {
    element.style.borderColor = borderColor.value;
    saveState();
  };
  
  opacity.oninput = () => {
    element.style.opacity = opacity.value / 100;
    saveState();
  };
}

/**
 * Utility function to convert RGB to Hex
 */
function rgbToHex(rgb) {
  if (rgb.startsWith('#')) return rgb;
  const result = rgb.match(/\d+/g);
  if (!result) return '#000000';
  return '#' + result.map(x => parseInt(x).toString(16).padStart(2, '0')).join('');
}

/**
 * Show notification to user
 */
function showNotification(message) {
  const notification = document.getElementById('notification');
  notification.innerHTML = message;
  notification.classList.add('show');
  
  setTimeout(() => {
    notification.classList.remove('show');
  }, 3000);
}

/**
 * Board management functions
 */
function saveBoard() {
  saveBoardState();
  document.getElementById('saveStatus').textContent = 'Saved';
  showNotification('Board saved successfully!');
  
  setTimeout(() => {
    document.getElementById('saveStatus').textContent = 'Auto-saved';
  }, 2000);
}

function autoSave() {
  saveBoardState();
  document.getElementById('saveStatus').textContent = 'Auto-saved';
}

function saveBoardState() {
  const container = document.getElementById('canvasElements');
  // Ensure we embed a hidden snapshot of the drawing canvas so it's persisted to server
  try {
    const drawingCanvas = document.getElementById('drawingCanvas');
    if (drawingCanvas && container) {
      let snap = container.querySelector('#wb-snapshot');
      if (!snap) {
        snap = document.createElement('img');
        snap.id = 'wb-snapshot';
        snap.alt = 'canvas-snapshot';
        snap.style.display = 'none';
        container.appendChild(snap);
      }
      // Update snapshot data URL (PNG)
      snap.src = drawingCanvas.toDataURL('image/png');
    }
  } catch(_){ }
  boardData.elements = container.innerHTML;
  boardData.name = document.getElementById('boardName').value;
  boardData.settings = {
    zoom: zoomLevel,
    pan: { x: panX, y: panY },
    timer: timerSeconds,
    tool: currentTool,
    color: currentColor
  };
  
  localStorage.setItem('collabodraw-board', JSON.stringify(boardData));
  
  // Persist to server if a board is loaded via controller (window.CD is set in mainscreen)
  try {
    if (window.CD && window.CD.boardId) {
      const id = window.CD.boardId;
      fetch(`/api/boards/${id}/content`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          elements: boardData.elements,
          settings: boardData.settings,
          name: boardData.name
        })
      }).catch(() => {/* ignore background errors */});
    }
  } catch (e) { /* ignore */ }
  
  // Add to version history
  addToVersionHistory();
}

function loadBoardState() {
  const saved = localStorage.getItem('collabodraw-board');
  if (!saved) return;
  
  try {
    boardData = JSON.parse(saved);
    
    if (boardData.elements) {
      document.getElementById('canvasElements').innerHTML = boardData.elements;
      // Re-setup interactions for loaded elements
      document.querySelectorAll('.canvas-element').forEach(setupElementInteraction);
      // If a snapshot of the drawing exists, render it back onto the canvas
      try {
        const snap = document.getElementById('wb-snapshot');
        if (snap && snap.src && ctx) {
          const img = new Image();
          img.onload = () => {
            try { ctx.drawImage(img, 0, 0); } catch(_){ }
          };
          img.src = snap.src;
        }
      } catch(_){ }
    }
    
    if (boardData.name) {
      document.getElementById('boardName').value = boardData.name;
    }
    
    if (boardData.settings) {
      const settings = boardData.settings;
      if (settings.zoom) {
        zoomLevel = settings.zoom;
        updateZoom();
      }
      if (settings.timer) {
        timerSeconds = settings.timer;
        updateTimerDisplay();
      }
      if (settings.tool) {
        selectTool(settings.tool);
      }
      if (settings.color) {
        selectColor(settings.color);
      }
    }
  } catch (e) {
    console.error('Failed to load board state:', e);
  }
}

function addToVersionHistory() {
  const versions = getVersionHistory();
  const now = new Date();
  const timestamp = now.toLocaleTimeString();
  
  const newVersion = {
    id: generateId(),
    timestamp: timestamp,
    description: 'Auto-save',
    data: JSON.stringify(boardData)
  };
  
  versions.unshift(newVersion);
  versions.splice(10); // Keep only 10 versions
  
  localStorage.setItem('collabodraw-versions', JSON.stringify(versions));
  updateVersionHistory();

  // Broadcast version event to collaborators
  try {
    if (wsBoardId && window.CollaboSocket) {
      CollaboSocket.publishVersion(wsBoardId, {
        id: newVersion.id,
        description: newVersion.description,
        timestamp: newVersion.timestamp
      });
    }
  } catch(_){}
}

function restoreVersion(versionId) {
  if (!confirm('Are you sure you want to restore this version? Current changes will be lost.')) {
    return;
  }
  
  const versions = getVersionHistory();
  const version = versions.find(v => v.id === versionId);
  
  if (version) {
    try {
      boardData = JSON.parse(version.data);
      document.getElementById('canvasElements').innerHTML = boardData.elements;
      document.getElementById('boardName').value = boardData.name;
      
      // Re-setup interactions
      document.querySelectorAll('.canvas-element').forEach(setupElementInteraction);
      
      showNotification('Version restored: ' + version.timestamp);
    } catch (e) {
      console.error('Failed to restore version:', e);
      showNotification('Failed to restore version');
    }
  }
}

/**
 * Share and export functions
 */
function shareBoard() {
  const shareUrl = `${window.location.origin}${window.location.pathname}?board=${generateId()}`;
  
  if (navigator.clipboard) {
    navigator.clipboard.writeText(shareUrl)
      .then(() => showNotification('Board link copied to clipboard!'))
      .catch(() => showNotification('Failed to copy link'));
  } else {
    showNotification('Sharing not supported in this browser');
  }
}

/**
 * ✅ FIXED: Export board as PNG with actual canvas content
 */
function exportBoard() {
  try {
    const drawingCanvas = document.getElementById('drawingCanvas');
    if (!drawingCanvas) {
      showNotification('Canvas not found');
      return;
    }
    
    // Get actual canvas content
    const dataURL = drawingCanvas.toDataURL('image/png');
    
    // Create download link
    const link = document.createElement('a');
    link.download = `${boardData.name || 'collabodraw-board'}-${Date.now()}.png`;
    link.href = dataURL;
    link.click();
    
    showNotification('Board exported successfully!');
    console.log('✅ Board exported to PNG');
  } catch (error) {
    console.error('❌ Export failed:', error);
    showNotification('Export failed');
  }
}

/**
 * Help and utilities
 */
function showHelp() {
  const help = document.getElementById('shortcutsHelp');
  help.classList.add('show');
  
  setTimeout(() => {
    help.classList.remove('show');
  }, 5000);
}

function goHome() {
  if (confirm('Are you sure you want to leave? Unsaved changes will be lost.')) {
    window.location.href = '/home';
  }
}

/**
 * Real-time collaboration simulation
 */
function startRealTimeSync() {
  try {
    // Normalize board id
    let bid = window.CD && window.CD.boardId;
    if (!bid) return;
    if (typeof bid === 'string') bid = parseInt(bid.replace(/^board-/, ''), 10);
    if (typeof bid === 'number') bid = parseInt(bid, 10);
    if (!bid || isNaN(bid)) return;
    wsBoardId = bid;

    if (!window.CollaboSocket) {
      console.warn('Realtime client (CollaboSocket) not loaded');
      return;
    }

    CollaboSocket.connect(() => {
      // Join board and start heartbeat
      CollaboSocket.joinBoard(wsBoardId);
      CollaboSocket.startHeartbeat(wsBoardId, 15000);

      // Fetch and replay historical events before subscribing to live ones
      fetchAndReplayEvents(wsBoardId).catch(e => console.warn('Replay failed', e));

      // Subscribe participants and map to UI users
      if (wsSubscriptions.participants) { try { wsSubscriptions.participants.unsubscribe(); } catch(_){} }
      wsSubscriptions.participants = CollaboSocket.subscribeParticipants(wsBoardId, (items) => {
        try {
          const mapped = (items || []).map(p => ({
            id: p.userId,
            userId: p.userId,
            name: p.username,
            initials: (p.username || 'U').substring(0,2).toUpperCase(),
            color: colorFromString(p.username || String(p.userId))
          }));
          // Merge strategy: if server returned empty list, keep existing users (heartbeat edge case)
          const effective = mapped.length === 0 && users.length > 0 ? users : mapped;
          // Delta-detect join/leave
          const current = new Set(effective.map(m => m.name || String(m.userId)));
          const joined = [];
          const left = [];
          current.forEach(n => { if (!_lastParticipants.has(n)) joined.push(n); });
          _lastParticipants.forEach(n => { if (!current.has(n)) left.push(n); });
          if (joined.length) notify(`${joined.join(', ')} joined`);
          if (left.length) notify(`${left.join(', ')} left`);
          _lastParticipants = current;
          users = effective;
          // Refresh UI panels
          updateActiveUsers();
          // Render avatars without clobbering users
          const avatars = document.getElementById('userAvatars');
          if (avatars) {
            avatars.innerHTML = '';
            users.forEach((u, index) => {
              const avatar = document.createElement('div');
              avatar.className = 'avatar';
              avatar.style.background = u.color;
              avatar.title = u.name + (index === 0 ? ' (You)' : '');
              avatar.textContent = (u.initials || 'U');
              avatars.appendChild(avatar);
            });
          }
        } catch (e) { console.warn('participants mapping failed', e); }
      });

      // Subscribe cursor events
      if (wsSubscriptions.cursors) { try { wsSubscriptions.cursors.unsubscribe(); } catch(_){} }
      wsSubscriptions.cursors = CollaboSocket.subscribeCursors(wsBoardId, (evt) => {
        if (!evt || evt.type !== 'cursor') return;
        // Try to avoid rendering own cursor if identifiable
        const myName = (window.CD && window.CD.currentUserName) || (getCurrentUser().name);
        if (evt.username && myName && evt.username === myName) return;
        const key = evt.userId || evt.username || 'unknown';
        remoteCursors[key] = {
          x: evt.x || 0,
          y: evt.y || 0,
          name: evt.username || String(evt.userId || ''),
          color: colorFromString((evt.username || String(evt.userId || '')))
        };
        // Fallback participant tracking if participants topic not emitting
        const cursorName = remoteCursors[key].name;
        if (cursorName && !users.some(u => u.name === cursorName)) {
          users.push({
            id: key,
            userId: key,
            name: cursorName,
            initials: (cursorName.substring(0,2) || 'U').toUpperCase(),
            color: remoteCursors[key].color
          });
          updateActiveUsers();
          notify(cursorName + ' joined');
        }
        renderRemoteCursors();
      });

      // Subscribe version events to sync sidebar
      if (wsSubscriptions.versions) { try { wsSubscriptions.versions.unsubscribe(); } catch(_){} }
      wsSubscriptions.versions = CollaboSocket.subscribeVersions(wsBoardId, (evt) => {
        try {
          if (!evt || evt.type !== 'version') return;
          const list = getVersionHistory();
          if (!list.find(v => v.id === evt.id)) {
            const merged = [{ id: evt.id || generateId(), timestamp: evt.timestamp || new Date().toLocaleTimeString(), description: evt.description || 'Update', data: null }, ...list].slice(0,10);
            localStorage.setItem('collabodraw-versions', JSON.stringify(merged));
            updateVersionHistory();
          }
        } catch(e){ console.warn('version event handling failed', e); }
      });
      // Subscribe element updates (strokes, notes, text)
      if (wsSubscriptions.elements) { try { wsSubscriptions.elements.unsubscribe(); } catch(_){} }
      wsSubscriptions.elements = CollaboSocket.subscribeElements(wsBoardId, (payload, meta) => {
        try {
          if (!meta || !meta.kind) return;
          const kind = meta.kind;
          if (kind === 'stroke' && payload && Array.isArray(payload.points)) {
            // Maintain ongoing path per strokeId for smoother remote rendering
            const sid = payload.strokeId || 'unknown';
            window._remoteStrokePaths = window._remoteStrokePaths || {};
            const existing = window._remoteStrokePaths[sid];
            const pts = payload.points;
            ctx.save();
            ctx.lineCap = 'round';
            ctx.strokeStyle = payload.color || '#000';
            ctx.globalAlpha = payload.alpha != null ? payload.alpha : 1;
            ctx.lineWidth = payload.width || 2;
            ctx.beginPath();
            if (existing && existing.lastPoint) {
              ctx.moveTo(existing.lastPoint[0], existing.lastPoint[1]);
            } else if (pts.length) {
              ctx.moveTo(pts[0][0], pts[0][1]);
            }
            for (let i=0;i<pts.length;i++) {
              const [px, py] = pts[i];
              ctx.lineTo(px, py);
            }
            ctx.stroke();
            ctx.closePath();
            ctx.restore();
            if (pts.length) {
              window._remoteStrokePaths[sid] = { lastPoint: pts[pts.length-1] };
            }
          } else if (kind === 'sticky' && payload) {
            if (!document.querySelector(`[data-id="${payload.id}"]`)) {
              const el = createStickyNote(payload.x, payload.y);
              if (el) el.dataset.id = payload.id;
            }
          } else if (kind === 'sticky-update' && payload) {
            const el = document.querySelector(`[data-id="${payload.id}"]`);
            if (el) {
              const ti = el.querySelector('.sticky-title');
              const ta = el.querySelector('.sticky-content');
              if (ti && typeof payload.title === 'string') ti.value = payload.title;
              if (ta && typeof payload.content === 'string') ta.value = payload.content;
            }
          } else if (kind === 'text' && payload) {
            if (!document.querySelector(`[data-id="${payload.id}"]`)) {
              const el = createTextElement(payload.x, payload.y);
              if (el) el.dataset.id = payload.id;
            }
          } else if (kind === 'text-update' && payload) {
            const el = document.querySelector(`[data-id="${payload.id}"]`);
            if (el) {
              const input = el.querySelector('input');
              if (input && typeof payload.value === 'string') input.value = payload.value;
            }
          } else if (kind === 'move' && payload) {
            const el = document.querySelector(`[data-id="${payload.id}"]`);
            if (el) {
              el.style.left = (parseInt(payload.x, 10) || 0) + 'px';
              el.style.top = (parseInt(payload.y, 10) || 0) + 'px';
            }
          } else if (kind === 'erase' && payload) {
            // Apply erase to local canvas for remote user
            const r = payload.radius || 20;
            ctx.clearRect((payload.x||0) - r, (payload.y||0) - r, r*2, r*2);
          }
        } catch(err){ console.warn('element event handling failed', err); }
      });
    });

    // Clean up on unload
    window.addEventListener('beforeunload', () => {
      try { if (wsBoardId) CollaboSocket.leaveBoard(wsBoardId); } catch(_){}
      try { CollaboSocket.disconnect(); } catch(_){}
    });
  } catch (e) {
    console.warn('Failed to start realtime sync:', e);
  }
}

// Fetch prior events from REST replay endpoint and render them
async function fetchAndReplayEvents(bid) {
  if (!bid) return;
  try {
    const resp = await fetch(`/api/live/${bid}`);
    if (!resp.ok) return;
    const body = await resp.json();
    const events = Array.isArray(body) ? body : (Array.isArray(body?.events) ? body.events : []);
    if (!Array.isArray(events)) return;
    console.log(`🕘 Replaying ${events.length} prior events for board ${bid}`);
    events.forEach(ev => {
      try {
        const kind = ev.kind || ev.type || ev.eventType;
        const payload = ev.payload || ev.data || ev.body;
        if (!kind) return;
        // Reuse existing rendering logic
        if (kind === 'stroke' && payload && Array.isArray(payload.points)) {
          const pts = payload.points;
          ctx.save();
          ctx.beginPath();
          ctx.lineCap = 'round';
          ctx.strokeStyle = payload.color || '#000';
          ctx.globalAlpha = payload.alpha != null ? payload.alpha : 1;
          ctx.lineWidth = payload.width || 2;
          for (let i=0;i<pts.length;i++) {
            const [px, py] = pts[i];
            if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py);
          }
          ctx.stroke();
          ctx.closePath();
          ctx.restore();
        } else if (kind === 'sticky' && payload) {
          if (!document.querySelector(`[data-id="${payload.id}"]`)) {
            const el = createStickyNote(payload.x, payload.y);
            if (el) el.dataset.id = payload.id;
          }
        } else if (kind === 'sticky-update' && payload) {
          const el = document.querySelector(`[data-id="${payload.id}"]`);
          if (el) {
            const ti = el.querySelector('.sticky-title');
            const ta = el.querySelector('.sticky-content');
            if (ti && typeof payload.title === 'string') ti.value = payload.title;
            if (ta && typeof payload.content === 'string') ta.value = payload.content;
          }
        } else if (kind === 'text' && payload) {
          if (!document.querySelector(`[data-id="${payload.id}"]`)) {
            const el = createTextElement(payload.x, payload.y);
            if (el) el.dataset.id = payload.id;
          }
        } else if (kind === 'text-update' && payload) {
          const el = document.querySelector(`[data-id="${payload.id}"]`);
          if (el) {
            const input = el.querySelector('input');
            if (input && typeof payload.value === 'string') input.value = payload.value;
          }
        } else if (kind === 'erase' && payload) {
          const r = payload.radius || 20;
          ctx.clearRect((payload.x||0) - r, (payload.y||0) - r, r*2, r*2);
        }
      } catch(re){ console.warn('Replay event failed', re); }
    });
  } catch (e) {
    console.warn('Failed to fetch replay events', e);
  }
}

function renderRemoteCursors() {
  const container = document.getElementById('userCursors');
  if (!container) return;
  container.innerHTML = '';
  Object.keys(remoteCursors).forEach(key => {
    const c = remoteCursors[key];
    const el = document.createElement('div');
    el.className = 'user-cursor';
    el.style.position = 'absolute';
    el.style.left = `${Math.max(0, Math.floor(c.x))}px`;
    el.style.top = `${Math.max(0, Math.floor(c.y))}px`;
    el.style.pointerEvents = 'none';
    el.innerHTML = `
      <div class="cursor-pointer" style="width:8px;height:8px;border-radius:50%;background:${c.color};box-shadow:0 0 0 2px rgba(255,255,255,0.8)"></div>
      <div class="cursor-label" style="position:relative;left:10px;top:-6px;background:rgba(0,0,0,0.7);color:#fff;padding:2px 6px;border-radius:6px;font-size:11px;">${c.name}</div>
    `;
    container.appendChild(el);
  });
}

// Simple on-page toast/notification helper
function notify(message, timeoutMs = 2500) {
  try {
    let host = document.getElementById('toastHost');
    if (!host) {
      host = document.createElement('div');
      host.id = 'toastHost';
      host.style.position = 'fixed';
      host.style.right = '16px';
      host.style.bottom = '16px';
      host.style.zIndex = '9999';
      host.style.display = 'flex';
      host.style.flexDirection = 'column';
      host.style.gap = '8px';
      document.body.appendChild(host);
    }
    const toast = document.createElement('div');
    toast.textContent = String(message || '');
    toast.style.background = 'rgba(0,0,0,0.8)';
    toast.style.color = '#fff';
    toast.style.padding = '8px 12px';
    toast.style.borderRadius = '8px';
    toast.style.fontSize = '13px';
    toast.style.boxShadow = '0 4px 16px rgba(0,0,0,0.25)';
    toast.style.opacity = '0';
    toast.style.transition = 'opacity 150ms ease';
    host.appendChild(toast);
    requestAnimationFrame(() => toast.style.opacity = '1');
    setTimeout(() => {
      toast.style.opacity = '0';
      setTimeout(() => host.removeChild(toast), 200);
    }, timeoutMs);
  } catch {}
}

function broadcastChange(type, data) {
  // Simulate broadcasting changes to other users
  console.log('Broadcasting change:', type, data);
  
  // In a real implementation, this would send data to a WebSocket server
  // or real-time collaboration service
}

/**
 * Utility functions
 */
function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substr(2);
}

function handleCanvasMouseDown(e) {
  // Handle pan mode and other interactions
  if (currentTool === 'hand') {
    e.preventDefault();
    // Implement panning logic here
  }
}

function handleCanvasMouseMove(e) {
  // Update cursor position for collaboration
  try {
    const rect = mainCanvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    if (wsBoardId && window.CollaboSocket) {
      CollaboSocket.updateCursor(wsBoardId, Math.round(x), Math.round(y));
    }
  } catch(_){}
}

function handleCanvasMouseUp(e) {
  // End any drag operations
}

// Initialize the application when DOM is ready
document.addEventListener('DOMContentLoaded', initializeApp);

class TooltipManager {
    constructor() {
      this.tooltip = document.getElementById('tooltip');
      this.currentElement = null;
      this.showTimer = null;
      this.hideTimer = null;
      this.init();
    }

    init() {
      // Add event listeners to all elements with data-tooltip
      this.bindTooltipEvents();
      
      // Re-bind when new elements are added
      this.observer = new MutationObserver(() => {
        this.bindTooltipEvents();
      });
      
      this.observer.observe(document.body, {
        childList: true,
        subtree: true
      });
    }

    bindTooltipEvents() {
      document.querySelectorAll('[data-tooltip]').forEach(element => {
        if (element.tooltipBound) return;
        element.tooltipBound = true;

        element.addEventListener('mouseenter', (e) => {
          this.showTooltip(e.target, e);
        });

        element.addEventListener('mouseleave', () => {
          this.hideTooltip();
        });

        element.addEventListener('mousemove', (e) => {
          if (this.currentElement === e.target) {
            this.updatePosition(e);
          }
        });
      });
    }

    showTooltip(element, event) {
      clearTimeout(this.hideTimer);
      
      this.showTimer = setTimeout(() => {
        this.currentElement = element;
        const tooltipText = element.getAttribute('data-tooltip');
        
        if (tooltipText) {
          this.tooltip.innerHTML = tooltipText;
          this.tooltip.classList.add('show');
          this.updatePosition(event);
        }
      }, 300); // Delay before showing
    }

    hideTooltip() {
      clearTimeout(this.showTimer);
      
      this.hideTimer = setTimeout(() => {
        this.tooltip.classList.remove('show');
        this.currentElement = null;
      }, 100); // Small delay before hiding
    }

    updatePosition(event) {
      if (!this.tooltip.classList.contains('show')) return;

      const rect = this.tooltip.getBoundingClientRect();
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;
      
      let x = event.clientX;
      let y = event.clientY - rect.height - 10;

      // Adjust horizontal position if tooltip goes off screen
      if (x + rect.width > viewportWidth) {
        x = viewportWidth - rect.width - 10;
      }
      if (x < 10) {
        x = 10;
      }

      // Adjust vertical position if tooltip goes off screen
      if (y < 10) {
        y = event.clientY + 20;
        this.tooltip.classList.add('bottom');
      } else {
        this.tooltip.classList.remove('bottom');
      }

      this.tooltip.style.left = x + 'px';
      this.tooltip.style.top = y + 'px';
    }
  }

  // Initialize tooltip system when DOM is ready
  document.addEventListener('DOMContentLoaded', () => {
    new TooltipManager();
  });

  // Add tooltips to dynamically created elements
  function addTooltipToElement(element, text) {
    element.setAttribute('data-tooltip', text);
  }

  // Example usage for dynamic elements
  function createElementWithTooltip(tagName, tooltip, content) {
    const element = document.createElement(tagName);
    element.setAttribute('data-tooltip', tooltip);
    if (content) element.innerHTML = content;
    return element;
  }

  // Simple deterministic color from string
  function colorFromString(str) {
    try {
      let hash = 0;
      for (let i = 0; i < str.length; i++) {
        hash = str.charCodeAt(i) + ((hash << 5) - hash);
      }
      const hue = Math.abs(hash) % 360;
      return `hsl(${hue}, 70%, 55%)`;
    } catch { return '#3b82f6'; }
  }
