/**
 * state.js - Global application state management
 * Centralized state management for the whiteboard application
 */

const AppState = {
  // Drawing tool state
  currentTool: 'select',
  currentColor: '#000000',
  isDrawing: false,
  
  // Selection state
  selectedElements: [],
  clipboard: [],
  
  // History state
  undoStack: [],
  redoStack: [],
  historyRestoring: false,
  
  // View state
  zoomLevel: 1,
  panX: 0,
  panY: 0,
  traybarVisible: false,
  
  // Timer state
  timerRunning: false,
  timerSeconds: 0,
  timerInterval: null,
  
  // Board state
  boardData: {
    name: 'Untitled Board',
    elements: [],
    settings: {}
  },
  
  // Realtime collaboration state
  users: [],
  remoteCursors: {},
  wsBoardId: null,
  _lastParticipants: new Set(),
  wsSubscriptions: {
    participants: null,
    cursors: null,
    versions: null,
    elements: null
  },
  
  // DOM references
  mainCanvas: null,
  canvas: null,
  ctx: null,
  
  // Configuration
  CONFIG: {
    AUTO_SAVE_INTERVAL: 30000,
    MAX_UNDO_HISTORY: 50,
    CANVAS_WIDTH: 2000,
    CANVAS_HEIGHT: 1500,
    ZOOM_MIN: 0.1,
    ZOOM_MAX: 3,
    ZOOM_STEP: 1.2
  },
  
  // Utility to get current user
  getCurrentUser() {
    const authenticatedUserId = window.CD && window.CD.currentUserId != null ? window.CD.currentUserId : null;
    const injected = (window.CD && (window.CD.currentUserName || window.CD.currentUserInitials))
      ? {
          userId: authenticatedUserId,
          name: window.CD.currentUserName || 'User',
          initials: (window.CD.currentUserInitials || (window.CD.currentUserName ? window.CD.currentUserName.substring(0,2) : 'U')).toUpperCase(),
        }
      : null;

    const dataEl = document.getElementById('currentUserData');
    const ds = dataEl ? dataEl.dataset : null;
    const injected2 = (!injected && ds && (ds.name || ds.initials))
      ? { userId: authenticatedUserId, name: ds.name || 'User', initials: (ds.initials || (ds.name ? ds.name.substring(0,2) : 'U')).toUpperCase() }
      : null;

    let user = JSON.parse(localStorage.getItem('collabodraw-user') || '{}');
    if (!user.id) {
      user = { id: this.generateId(), userId: authenticatedUserId, name: 'User', initials: 'U', color: '#3b82f6' };
    }

    const chosen = injected || injected2 || user;
    if (authenticatedUserId != null && chosen.userId == null) {
      chosen.userId = authenticatedUserId;
    }
    chosen.initials = (chosen.initials || (chosen.name ? chosen.name.substring(0,2) : 'U')).toUpperCase();
    localStorage.setItem('collabodraw-user', JSON.stringify({ ...user, ...chosen }));
    return { ...user, ...chosen };
  },
  
  // Generate unique ID
  generateId() {
    return 'id-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
  },

  // Stable per-tab id to distinguish collaborators across tabs on same machine/session.
  getClientId() {
    try {
      if (!this._clientId) {
        const existing = sessionStorage.getItem('collabodraw-client-id');
        this._clientId = existing || this.generateId();
        sessionStorage.setItem('collabodraw-client-id', this._clientId);
      }
      return this._clientId;
    } catch (_) {
      if (!this._clientId) {
        this._clientId = this.generateId();
      }
      return this._clientId;
    }
  },
  
  // Get board ID from various sources
  getBoardId() {
    let boardId = window.CD?.boardId;
    if (!boardId) return null;
    
    if (typeof boardId === 'string') {
      boardId = parseInt(boardId.replace(/^board-/, ''), 10);
    } else if (typeof boardId === 'number') {
      boardId = parseInt(boardId, 10);
    }
    
    return isNaN(boardId) ? null : boardId;
  }
};

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
  module.exports = AppState;
}
