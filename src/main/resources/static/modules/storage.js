/**
 * storage.js - Board persistence and state management
 * Handles localStorage, server persistence, versioning
 */

const Storage = {
  getBoardStorageKey() {
    const boardId = AppState.getBoardId();
    return boardId ? `collabodraw-board-${boardId}` : 'collabodraw-board';
  },

  getVersionHistoryKey() {
    const boardId = AppState.getBoardId();
    return boardId ? `collabodraw-versions-${boardId}` : 'collabodraw-versions';
  },

  resetBoardState() {
    const container = document.getElementById('canvasElements');
    if (container) {
      container.innerHTML = '';
    }

    if (AppState.ctx && AppState.canvas) {
      AppState.ctx.clearRect(0, 0, AppState.canvas.width, AppState.canvas.height);
    }

    AppState.boardData = {
      name: AppState.boardData?.name || 'Untitled Board',
      elements: '',
      settings: {}
    };

    AppState.selectedElements = [];
    AppState.clipboard = [];
  },

  /**
   * Save board state to localStorage and server
   */
  saveBoardState() {
    const container = document.getElementById('canvasElements');
    if (!container) return;
    
    // Embed canvas snapshot
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
        snap.src = drawingCanvas.toDataURL('image/png');
      }
    } catch(_){ }
    
    AppState.boardData.elements = container.innerHTML;
    AppState.boardData.name = document.getElementById('boardName')?.value || AppState.boardData.name;
    AppState.boardData.settings = {
      zoom: AppState.zoomLevel,
      pan: { x: AppState.panX, y: AppState.panY },
      timer: AppState.timerSeconds,
      tool: AppState.currentTool,
      color: AppState.currentColor
    };
    
    localStorage.setItem(this.getBoardStorageKey(), JSON.stringify(AppState.boardData));
    
    // Persist to server
    try {
      if (window.CD && window.CD.boardId) {
        const id = window.CD.boardId;
        fetch(`/api/boards/${id}/content`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            elements: AppState.boardData.elements,
            settings: AppState.boardData.settings,
            name: AppState.boardData.name
          })
        }).catch(() => {/* ignore background errors */});
      }
    } catch (e) { /* ignore */ }
    
    this.addToVersionHistory();
  },

  /**
   * Load board state from localStorage
   */
  loadBoardState() {
    const saved = localStorage.getItem(this.getBoardStorageKey());
    if (!saved) {
      this.resetBoardState();
      return;
    }
    
    try {
      AppState.boardData = JSON.parse(saved);
      
      if (AppState.boardData.elements) {
        document.getElementById('canvasElements').innerHTML = AppState.boardData.elements;
        document.querySelectorAll('.canvas-element').forEach(el => ElementManager.setupElementInteraction(el));
        
        try {
          const snap = document.getElementById('wb-snapshot');
          if (snap && snap.src && AppState.ctx) {
            const img = new Image();
            img.onload = () => {
              try { AppState.ctx.drawImage(img, 0, 0); } catch(_){ }
            };
            img.src = snap.src;
          }
        } catch(_){ }
      }
      
      if (AppState.boardData.name) {
        const nameInput = document.getElementById('boardName');
        if (nameInput) nameInput.value = AppState.boardData.name;
      }
      
      if (AppState.boardData.settings) {
        const settings = AppState.boardData.settings;
        if (settings.zoom) {
          AppState.zoomLevel = settings.zoom;
          Canvas.updateZoom();
        }
        if (settings.timer) {
          AppState.timerSeconds = settings.timer;
          UIControls.updateTimerDisplay();
        }
        if (settings.tool) {
          UIControls.selectTool(settings.tool);
        }
        if (settings.color) {
          UIControls.selectColor(settings.color);
        }
      }
    } catch (e) {
      console.error('Failed to load board state:', e);
    }
  },

  /**
   * Get version history from localStorage
   */
  getVersionHistory() {
    const history = JSON.parse(localStorage.getItem(this.getVersionHistoryKey()) || '[]');
    return history.slice(0, 10);
  },

  /**
   * Add to version history
   */
  addToVersionHistory() {
    const versions = this.getVersionHistory();
    const now = new Date();
    const timestamp = now.toLocaleTimeString();
    
    const newVersion = {
      id: AppState.generateId(),
      timestamp: timestamp,
      description: 'Auto-save',
      data: JSON.stringify(AppState.boardData)
    };
    
    versions.unshift(newVersion);
    versions.splice(10);
    
    localStorage.setItem(this.getVersionHistoryKey(), JSON.stringify(versions));
    this.updateVersionHistory();

    // Broadcast version event
    try {
      if (AppState.wsBoardId && window.CollaboSocket) {
        CollaboSocket.publishVersion(AppState.wsBoardId, {
          id: newVersion.id,
          description: newVersion.description,
          timestamp: newVersion.timestamp
        });
      }
    } catch(_){}
  },

  /**
   * Update version history UI
   */
  updateVersionHistory() {
    const versionHistory = document.getElementById('versionHistory');
    if (!versionHistory) return;
    
    const versions = this.getVersionHistory();
    
    versionHistory.innerHTML = versions.map(version => `
      <div class="version-item" onclick="Storage.restoreVersion('${version.id}')">
        <span>🕐</span>
        <span>${version.timestamp} - ${version.description}</span>
      </div>
    `).join('');
  },

  /**
   * Restore a previous version
   */
  restoreVersion(versionId) {
    if (!confirm('Are you sure you want to restore this version? Current changes will be lost.')) {
      return;
    }
    
    const versions = this.getVersionHistory();
    const version = versions.find(v => v.id === versionId);
    
    if (version) {
      try {
        AppState.boardData = JSON.parse(version.data);
        document.getElementById('canvasElements').innerHTML = AppState.boardData.elements;
        document.getElementById('boardName').value = AppState.boardData.name;

        if (AppState.boardData.settings) {
          if (AppState.boardData.settings.zoom) {
            AppState.zoomLevel = AppState.boardData.settings.zoom;
            Canvas.updateZoom();
          }
          if (AppState.boardData.settings.timer != null) {
            AppState.timerSeconds = AppState.boardData.settings.timer;
            UIControls.updateTimerDisplay();
          }
          if (AppState.boardData.settings.tool) UIControls.selectTool(AppState.boardData.settings.tool);
          if (AppState.boardData.settings.color) UIControls.selectColor(AppState.boardData.settings.color);
        }

        try {
          const snap = document.getElementById('wb-snapshot');
          if (snap && snap.src && AppState.ctx) {
            const img = new Image();
            img.onload = () => {
              try {
                AppState.ctx.clearRect(0, 0, AppState.canvas.width, AppState.canvas.height);
                AppState.ctx.drawImage(img, 0, 0);
              } catch (_) {}
            };
            img.src = snap.src;
          }
        } catch (_) {}
        
        document.querySelectorAll('.canvas-element').forEach(el => ElementManager.setupElementInteraction(el));
        
        UIControls.showNotification('Version restored: ' + version.timestamp);
      } catch (e) {
        console.error('Failed to restore version:', e);
        UIControls.showNotification('Failed to restore version');
      }
    }
  },

  /**
   * Manually save to server
   */
  manualSave() {
    if (History.isSaving) {
      console.log('⏳ Save in progress...');
      return;
    }
    
    History.isSaving = true;
    const boardId = AppState.getBoardId();
    const container = document.getElementById('canvasElements');
    
    if (!container) {
      console.error('❌ Canvas container not found');
      History.isSaving = false;
      return;
    }
    
    const saveData = {
      elements: container.innerHTML,
      settings: {
        zoom: AppState.zoomLevel,
        pan: { x: AppState.panX, y: AppState.panY },
        timer: AppState.timerSeconds,
        tool: AppState.currentTool,
        color: AppState.currentColor
      },
      name: document.getElementById('boardName')?.value || AppState.boardData.name || 'Untitled Board'
    };

    fetch(`/api/boards/${boardId}/content`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(saveData)
    })
    .then(response => {
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
      return response.json();
    })
    .then(data => {
      console.log('✅ Board saved successfully:', data);
      UIControls.showNotification('💾 Saved successfully');
      History.isSaving = false;
    })
    .catch(error => {
      console.error('❌ Error saving board:', error);
      UIControls.showNotification('❌ Error saving board');
      History.isSaving = false;
    });
  },

  /**
   * Setup auto-save timer
   */
  setupAutoSave() {
    setInterval(() => {
      const boardId = AppState.getBoardId();
      const container = document.getElementById('canvasElements');
      
      if (!container || !boardId) return;
      
      const currentState = History.createStateSnapshot();
      
      if (!History.lastSaveState || History.lastSaveState.checksum !== currentState.checksum) {
        console.log('🔄 Auto-saving...');
        this.manualSave();
      }
    }, AppState.CONFIG.AUTO_SAVE_INTERVAL);
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = Storage;
}
