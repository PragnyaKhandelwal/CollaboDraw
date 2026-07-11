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

  applyBoardState(state) {
    if (!state || typeof state !== 'object') return false;

    const container = document.getElementById('canvasElements');
    const elements = typeof state.elements === 'string' ? state.elements : '';
    const settings = state.settings && typeof state.settings === 'object' ? state.settings : {};

    AppState.boardData = {
      name: typeof state.name === 'string' && state.name.trim() ? state.name : (AppState.boardData?.name || 'Untitled Board'),
      elements,
      settings
    };

    if (container) {
      container.innerHTML = elements;
      document.querySelectorAll('.canvas-element').forEach(el => ElementManager.setupElementInteraction(el));
    }

    const nameInput = document.getElementById('boardName');
    if (nameInput && AppState.boardData.name) {
      nameInput.value = AppState.boardData.name;
    }

    if (settings.zoom) {
      AppState.zoomLevel = Number(settings.zoom) || AppState.zoomLevel;
      Canvas.updateZoom();
    }
    if (settings.timer != null) {
      AppState.timerSeconds = Number(settings.timer) || 0;
      UIControls.updateTimerDisplay();
    }
    if (settings.tool) {
      UIControls.selectTool(settings.tool);
    }
    if (settings.color) {
      UIControls.selectColor(settings.color);
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

    return true;
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
      if (window.CD && window.CD.boardId && AppState.canWrite !== false) {
        const id = window.CD.boardId;
        fetch(`/api/boards/${id}/content`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            elements: AppState.boardData.elements,
            settings: AppState.boardData.settings,
            name: AppState.boardData.name,
            expectedLastModified: AppState.lastModified || null
          })
        }).then(res => this.handleSaveResponse(res)).catch(() => {/* ignore background errors */});
      }
    } catch (e) { /* ignore */ }

    this.addToVersionHistory();
  },

  /**
   * Shared response handling for both the silent autosave path (saveBoardState) and the
   * explicit Save button (manualSave). On a 409 the server is telling us someone else saved
   * this board after we last loaded it - warn once rather than silently clobbering their
   * change on the next autosave tick.
   */
  async handleSaveResponse(response) {
    if (response.ok) {
      try {
        const data = await response.json();
        if (data && data.lastModified) AppState.lastModified = data.lastModified;
      } catch (_) {}
      return true;
    }

    if (response.status === 409) {
      try {
        const data = await response.json();
        if (data && data.currentLastModified) AppState.lastModified = data.currentLastModified;
      } catch (_) {}
      if (!this._warnedConflict) {
        this._warnedConflict = true;
        UIControls.showNotification('Someone else saved changes to this board. Reload to see the latest version.');
      }
      return false;
    }

    return false;
  },

  /**
   * Load board state from localStorage
   */
  async loadBoardState() {
    const boardId = AppState.getBoardId();
    if (boardId) {
      try {
        const response = await fetch(`/api/boards/${boardId}/content`, { credentials: 'include' });
        if (response.ok) {
          const data = await response.json();
          AppState.lastModified = data.lastModified || null;
          AppState.role = data.role || null;
          AppState.canWrite = data.canWrite !== false;
          if (typeof UIControls !== 'undefined' && typeof UIControls.applyReadOnlyMode === 'function') {
            UIControls.applyReadOnlyMode(!AppState.canWrite);
          }
          if (data && (typeof data.elements === 'string' || data.settings || data.name)) {
            const applied = this.applyBoardState({
              name: data.name || AppState.boardData?.name || 'Untitled Board',
              elements: data.elements || '',
              settings: data.settings || {}
            });
            if (applied) {
              localStorage.setItem(this.getBoardStorageKey(), JSON.stringify(AppState.boardData));
              return;
            }
          }
        }
      } catch (error) {
        console.warn('Failed to load board from server, falling back to local cache:', error);
      }
    }

    const saved = localStorage.getItem(this.getBoardStorageKey());
    if (!saved) {
      this.resetBoardState();
      return;
    }

    try {
      const parsed = JSON.parse(saved);
      this.applyBoardState(parsed);
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

    // Built with DOM APIs (not an innerHTML template string) so version.id/description -
    // which can arrive from a remote WS "version" event, not just this browser's own local
    // history - can never break out of an HTML/attribute context into script.
    versionHistory.innerHTML = '';

    if (!versions.length) {
      const empty = document.createElement('div');
      empty.className = 'version-empty';
      empty.style.cssText = 'font-size:13px;color:var(--gray);padding:4px 0;';
      empty.textContent = 'No versions saved yet - your first save will appear here.';
      versionHistory.appendChild(empty);
      return;
    }

    versions.forEach(version => {
      const item = document.createElement('div');
      item.className = 'version-item';
      item.dataset.versionId = version.id;

      const icon = document.createElement('span');
      const iconSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      iconSvg.setAttribute('class', 'icon');
      iconSvg.setAttribute('aria-hidden', 'true');
      const iconUse = document.createElementNS('http://www.w3.org/2000/svg', 'use');
      iconUse.setAttribute('href', '#icon-clock');
      iconSvg.appendChild(iconUse);
      icon.appendChild(iconSvg);

      const label = document.createElement('span');
      label.textContent = `${version.timestamp} - ${version.description}`;

      item.appendChild(icon);
      item.appendChild(label);
      item.addEventListener('click', () => this.restoreVersion(version.id));
      versionHistory.appendChild(item);
    });
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
      name: document.getElementById('boardName')?.value || AppState.boardData.name || 'Untitled Board',
      expectedLastModified: AppState.lastModified || null
    };

    fetch(`/api/boards/${boardId}/content`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(saveData)
    })
    .then(response => {
      if (response.status === 409) {
        this.handleSaveResponse(response);
        History.isSaving = false;
        return null;
      }
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
      return response.json();
    })
    .then(data => {
      if (!data) return; // conflict already handled above
      if (data.lastModified) AppState.lastModified = data.lastModified;
      UIControls.showNotification('Saved successfully');
      History.isSaving = false;
    })
    .catch(error => {
      console.error('❌ Error saving board:', error);
      UIControls.showNotification('Error saving board');
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
        this.manualSave();
      }
    }, AppState.CONFIG.AUTO_SAVE_INTERVAL);
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = Storage;
}
