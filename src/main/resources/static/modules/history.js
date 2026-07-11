/**
 * history.js - Undo/Redo functionality
 * Manages state snapshots and history navigation
 */

const History = {
  lastSaveState: null,
  isSaving: false,

  /**
   * Create a snapshot of current state
   */
  createStateSnapshot() {
    const container = document.getElementById('canvasElements');
    if (!container) {
      console.warn('⚠️ Canvas container not found');
      return null;
    }

    const boardNameInput = document.getElementById('boardName');
    const boardDataSnapshot = AppState.boardData ? JSON.parse(JSON.stringify(AppState.boardData)) : null;
    
    return {
      html: container.innerHTML,
      imageData: (() => {
        try {
          return AppState.canvas ? AppState.canvas.toDataURL('image/png') : null;
        } catch (_) {
          return null;
        }
      })(),
      boardData: boardDataSnapshot,
      boardName: boardNameInput ? boardNameInput.value : (AppState.boardData?.name || 'Untitled Board'),
      zoomLevel: AppState.zoomLevel,
      panX: AppState.panX,
      panY: AppState.panY,
      timerSeconds: AppState.timerSeconds,
      currentTool: AppState.currentTool,
      currentColor: AppState.currentColor,
      timestamp: Date.now(),
      elementCount: container.querySelectorAll('.canvas-element').length,
      checksum: this.generateChecksum(container.innerHTML)
    };
  },

  /**
   * Generate checksum for state validation
   */
  generateChecksum(html) {
    let hash = 0;
    for (let i = 0; i < html.length; i++) {
      const char = html.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash;
    }
    return Math.abs(hash).toString(16);
  },

  /**
   * Save current state to undo stack
   */
  saveState() {
    if (AppState.historyRestoring) return;

    const canvasData = this.createStateSnapshot();
    if (!canvasData) return;
    
    AppState.undoStack.push(canvasData);
    
    if (AppState.undoStack.length > AppState.CONFIG.MAX_UNDO_HISTORY) {
      AppState.undoStack.shift();
    }
    
    AppState.redoStack = [];
    this.updateUndoRedoButtons();
  },

  /**
   * Undo to previous state
   */
  undo() {
    if (AppState.undoStack.length <= 1) {
      console.warn('⏳ Nothing to undo');
      return;
    }
    
    const currentState = AppState.undoStack.pop();
    if (currentState) {
      AppState.redoStack.push(currentState);
    }

    const previousState = AppState.undoStack[AppState.undoStack.length - 1];
    if (previousState) {
      this.applyStateSnapshot(previousState);
    }

    this.updateUndoRedoButtons();
  },

  /**
   * Redo to next state
   */
  redo() {
    if (AppState.redoStack.length === 0) {
      console.warn('⏳ Nothing to redo');
      return;
    }
    
    const nextState = AppState.redoStack.pop();
    if (nextState) {
      AppState.undoStack.push(nextState);
      this.applyStateSnapshot(nextState);
    }

    this.updateUndoRedoButtons();
  },

  /**
   * Restore canvas snapshot from data URL
   */
  restoreCanvasSnapshot(dataUrl) {
    if (!AppState.ctx || !AppState.canvas || !dataUrl) return;

    const image = new Image();
    image.onload = () => {
      try {
        AppState.ctx.clearRect(0, 0, AppState.canvas.width, AppState.canvas.height);
        AppState.ctx.drawImage(image, 0, 0, AppState.canvas.width, AppState.canvas.height);
      } catch (error) {
        console.warn('⚠️ Failed to restore canvas snapshot', error);
      }
    };
    image.src = dataUrl;
  },

  /**
   * Apply a snapshot to current state
   */
  applyStateSnapshot(snapshot) {
    if (!snapshot) return;

    AppState.historyRestoring = true;
    try {
      const container = document.getElementById('canvasElements');
      if (container && typeof snapshot.html === 'string') {
        container.innerHTML = snapshot.html;
        ElementManager.restoreElementInteractions();
      }

      if (snapshot.boardData && typeof snapshot.boardData === 'object') {
        AppState.boardData = JSON.parse(JSON.stringify(snapshot.boardData));
      }

      const boardNameInput = document.getElementById('boardName');
      if (boardNameInput && typeof snapshot.boardName === 'string') {
        boardNameInput.value = snapshot.boardName;
      }
      if (AppState.boardData) {
        AppState.boardData.name = snapshot.boardName || AppState.boardData.name;
      }

      if (typeof snapshot.zoomLevel === 'number') {
        AppState.zoomLevel = snapshot.zoomLevel;
        Canvas.updateZoom();
      }
      if (typeof snapshot.panX === 'number') AppState.panX = snapshot.panX;
      if (typeof snapshot.panY === 'number') AppState.panY = snapshot.panY;
      if (typeof snapshot.timerSeconds === 'number') {
        AppState.timerSeconds = snapshot.timerSeconds;
        UIControls.updateTimerDisplay();
      }
      if (snapshot.currentTool) UIControls.selectTool(snapshot.currentTool);
      if (snapshot.currentColor) UIControls.selectColor(snapshot.currentColor);

      if (snapshot.imageData) {
        this.restoreCanvasSnapshot(snapshot.imageData);
      }

      Storage.saveBoardState();
    } finally {
      AppState.historyRestoring = false;
      this.updateUndoRedoButtons();
    }
  },

  /**
   * Update undo/redo button states
   */
  updateUndoRedoButtons() {
    const undoBtn = document.getElementById('btnUndo');
    const redoBtn = document.getElementById('btnRedo');
    const saveBtn = document.getElementById('btnSave');
    
    if (undoBtn) {
      const disabled = AppState.undoStack.length <= 1;
      undoBtn.disabled = disabled;
      undoBtn.classList.toggle('disabled', disabled);
    }
    
    if (redoBtn) {
      const disabled = AppState.redoStack.length === 0;
      redoBtn.disabled = disabled;
      redoBtn.classList.toggle('disabled', disabled);
    }

    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.classList.remove('disabled');
    }
  },

  /**
   * Clear all undo/redo history
   */
  clearHistory() {
    AppState.undoStack = [];
    AppState.redoStack = [];
    this.lastSaveState = null;
    this.updateUndoRedoButtons();
  },

  /**
   * Initialize undo/redo system
   */
  initialize() {
    const initialState = this.createStateSnapshot();
    if (initialState) {
      this.lastSaveState = initialState;
      if (AppState.undoStack.length === 0) {
        AppState.undoStack.push(initialState);
      }
    }
    
    this.updateUndoRedoButtons();
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = History;
}
