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
let boardData = {
  name: 'Untitled Board',
  elements: [],
  settings: {}
};

// Canvas and Context
let canvas, ctx, mainCanvas;

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
  // Get DOM elements
  canvas = document.getElementById('drawingCanvas');
  ctx = canvas.getContext('2d');
  mainCanvas = document.getElementById('mainCanvas');
  
  // Set canvas size
  resizeCanvas();
  
  // Initialize tools and events
  initializeTools();
  setupEventListeners();
  
  // Load saved state or create default
  loadBoardState();
  
  // Initialize user interface
  initializeUI();
  
  // Setup auto-save
  setInterval(autoSave, CONFIG.AUTO_SAVE_INTERVAL);
  
  // Start real-time features
  startRealTimeSync();
  
  // Hide loading screen
  setTimeout(() => {
    document.getElementById('loading').classList.remove('show');
  }, 1000);
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
  
  // Set up board name editing
  const boardNameInput = document.getElementById('boardName');
  boardNameInput.addEventListener('blur', function() {
    boardData.name = this.value;
    saveState();
  });
}

/**
 * Update user avatars in header
 */
function updateUserAvatars() {
  const userAvatars = document.getElementById('userAvatars');
  userAvatars.innerHTML = '';
  
  // Get current user from session/localStorage or create default
  const currentUser = getCurrentUser();
  users = [currentUser]; // Start with current user
  
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
  // Try to get from localStorage or create default
  let user = JSON.parse(localStorage.getItem('collabodraw-user') || '{}');
  
  if (!user.id) {
    // Create default user
    user = {
      id: generateId(),
      name: 'User',
      initials: 'U',
      color: '#3b82f6'
    };
    localStorage.setItem('collabodraw-user', JSON.stringify(user));
  }
  
  return user;
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

  // Toolbar buttons
  document.querySelectorAll('.toolbar-btn').forEach(btn => {
    btn.addEventListener('click', function() {
      const action = this.dataset.action;
      handleToolbarAction(action);
    });
  });
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
  document.querySelector(`[data-tool="${tool}"]`).classList.add('active');
  
  // Update canvas cursor
  updateCanvasCursor();
  
  // Show/hide drawing canvas
  const drawingCanvas = document.getElementById('drawingCanvas');
  if (['pen', 'highlighter', 'line', 'rectangle', 'circle', 'arrow'].includes(tool)) {
    drawingCanvas.classList.add('active');
  } else {
    drawingCanvas.classList.remove('active');
  }
  
  // Show color picker for drawing tools
  const colorPicker = document.getElementById('colorPicker');
  if (['pen', 'highlighter', 'rectangle', 'circle', 'arrow', 'text'].includes(tool)) {
    colorPicker.classList.add('show');
  } else {
    colorPicker.classList.remove('show');
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
 * Update canvas cursor based on current tool
 */
function updateCanvasCursor() {
  mainCanvas.className = 'main-canvas' + (traybarVisible ? ' traybar-visible' : '');
  
  if (currentTool === 'hand') {
    mainCanvas.classList.add('hand-mode');
  } else if (currentTool === 'select') {
    mainCanvas.classList.add('select-mode');
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

/**
 * Setup all event listeners
 */
function setupEventListeners() {
  // Canvas drawing events
  canvas.addEventListener('mousedown', startDrawing);
  canvas.addEventListener('mousemove', draw);
  canvas.addEventListener('mouseup', stopDrawing);
  canvas.addEventListener('mouseout', stopDrawing);

  // Canvas interaction events
  mainCanvas.addEventListener('click', handleCanvasClick);
  mainCanvas.addEventListener('contextmenu', showContextMenu);
  mainCanvas.addEventListener('mousedown', handleCanvasMouseDown);
  mainCanvas.addEventListener('mousemove', handleCanvasMouseMove);
  mainCanvas.addEventListener('mouseup', handleCanvasMouseUp);

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
 * Start drawing on canvas
 */
function startDrawing(e) {
  if (!['pen', 'highlighter', 'line', 'rectangle', 'circle'].includes(currentTool)) return;
  
  isDrawing = true;
  const rect = canvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  
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
  
  if (currentTool === 'pen' || currentTool === 'highlighter') {
    ctx.lineTo(x, y);
    ctx.stroke();
  }
}

/**
 * Stop drawing on canvas
 */
function stopDrawing() {
  if (!isDrawing) return;
  isDrawing = false;
  
  // Save state for undo
  saveState();
  
  // Broadcast changes to other users
  broadcastChange('draw', {
    tool: currentTool,
    color: currentColor,
    timestamp: Date.now()
  });
}

/**
 * Handle canvas click for tools like sticky notes and text
 */
function handleCanvasClick(e) {
  const rect = mainCanvas.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  
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
    broadcastChange('move', {
      id: element.dataset.id,
      x: element.style.left,
      y: element.style.top
    });
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
    switch(e.key) {
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
  
  // Tool selection shortcuts
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
    case ' ':
      e.preventDefault();
      selectTool('hand');
      break;
    case 'Delete':
      deleteSelected();
      break;
    case 'Escape':
      selectedElements.forEach(el => el.classList.remove('selected'));
      selectedElements = [];
      break;
    case 'F1':
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
 * Undo/Redo functionality
 */
function saveState() {
  const container = document.getElementById('canvasElements');
  const state = {
    html: container.innerHTML,
    boardName: boardData.name,
    timestamp: Date.now()
  };
  
  undoStack.push(state);
  if (undoStack.length > CONFIG.MAX_UNDO_HISTORY) {
    undoStack.shift();
  }
  
  redoStack = []; // Clear redo stack
}

function undo() {
  if (undoStack.length === 0) {
    showNotification('Nothing to undo');
    return;
  }
  
  const container = document.getElementById('canvasElements');
  const currentState = {
    html: container.innerHTML,
    boardName: boardData.name,
    timestamp: Date.now()
  };
  redoStack.push(currentState);
  
  const previousState = undoStack.pop();
  container.innerHTML = previousState.html;
  
  // Re-setup interactions for restored elements
  container.querySelectorAll('.canvas-element').forEach(setupElementInteraction);
  
  showNotification('Undone');
}

function redo() {
  if (redoStack.length === 0) {
    showNotification('Nothing to redo');
    return;
  }
  
  const container = document.getElementById('canvasElements');
  const currentState = {
    html: container.innerHTML,
    boardName: boardData.name,
    timestamp: Date.now()
  };
  undoStack.push(currentState);
  
  const nextState = redoStack.pop();
  container.innerHTML = nextState.html;
  
  // Re-setup interactions for restored elements
  container.querySelectorAll('.canvas-element').forEach(setupElementInteraction);
  
  showNotification('Redone');
}

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

function exportBoard() {
  try {
    const exportCanvas = document.createElement('canvas');
    const exportCtx = exportCanvas.getContext('2d');
    exportCanvas.width = 1920;
    exportCanvas.height = 1080;
    
    // Draw white background
    exportCtx.fillStyle = 'white';
    exportCtx.fillRect(0, 0, exportCanvas.width, exportCanvas.height);
    
    // Export as image
    const link = document.createElement('a');
    link.download = `${boardData.name || 'collabodraw-board'}.png`;
    link.href = exportCanvas.toDataURL();
    link.click();
    
    showNotification('Board exported successfully!');
  } catch (e) {
    console.error('Export failed:', e);
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
    window.location.href = 'home.html';
  }
}

/**
 * Real-time collaboration simulation
 */
function startRealTimeSync() {
  // Simulate real-time cursor movement for demo purposes
  setInterval(() => {
    updateUserCursors();
  }, 2000);
}

function updateUserCursors() {
  const cursorsContainer = document.getElementById('userCursors');
  
  // Remove existing cursors
  cursorsContainer.innerHTML = '';
  
  // Add cursors for other users (simulated)
  const otherUsers = users.slice(1); // Exclude current user
  
  otherUsers.forEach(user => {
    const cursor = document.createElement('div');
    cursor.className = 'user-cursor';
    cursor.innerHTML = `
      <div class="cursor-pointer" style="background: ${user.color};"></div>
      <div class="cursor-label">${user.name}</div>
    `;
    
    // Random position for demo
    cursor.style.left = Math.random() * 300 + 100 + 'px';
    cursor.style.top = Math.random() * 300 + 100 + 'px';
    
    cursorsContainer.appendChild(cursor);
  });
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
  broadcastChange('cursor', {
    x: e.clientX,
    y: e.clientY,
    user: getCurrentUser().id
  });
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