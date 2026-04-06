/**
 * ui.js - User interface controls
 * Handles tool selection, color picker, traybar, timer, properties panel
 */

const UIControls = {
  /**
   * Initialize tool button listeners
   */
  initializeTools() {
    document.querySelectorAll('.tool-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const tool = btn.dataset.tool;
        if (tool) {
          this.selectTool(tool);
        }
      });
    });

    document.querySelectorAll('.color-option').forEach(option => {
      option.addEventListener('click', () => {
        this.selectColor(option.dataset.color);
      });
    });

    const eraserBtn = document.querySelector('[data-tool="eraser"]');
    if (eraserBtn) {
      eraserBtn.addEventListener('click', () => DrawingTools.activateEraser());
    }

    document.querySelectorAll('.toolbar-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const action = btn.dataset.action;
        this.handleToolbarAction(action);
      });
    });
  },

  /**
   * Select a drawing tool
   */
  selectTool(tool) {
    AppState.currentTool = tool;
    
    document.querySelectorAll('.tool-btn').forEach(btn => {
      btn.classList.remove('active');
    });
    
    const toolBtn = document.querySelector(`[data-tool="${tool}"]`);
    if (toolBtn) {
      toolBtn.classList.add('active');
    }
    
    try {
      const drawCanvasEl = document.getElementById('drawingCanvas');
      if (drawCanvasEl) {
        const drawTools = new Set(['pen','highlighter','line','rectangle','circle','eraser']);
        drawCanvasEl.classList.toggle('active', drawTools.has(tool));
      }
    } catch(_){ }

    this.updateCanvasCursor();
    console.log(`🔧 Tool selected: ${tool}`);
  },

  /**
   * Select a drawing color
   */
  selectColor(color) {
    AppState.currentColor = color;
    
    document.querySelectorAll('.color-option').forEach(option => {
      option.classList.remove('selected');
    });
    document.querySelector(`[data-color="${color}"]`)?.classList.add('selected');
  },

  /**
   * Update canvas cursor based on current tool
   */
  updateCanvasCursor() {
    if (!AppState.mainCanvas) {
      console.warn('⚠️ Main canvas not available for cursor update');
      return;
    }

    try {
      AppState.mainCanvas.className = 'main-canvas';
      
      if (AppState.traybarVisible) {
        AppState.mainCanvas.classList.add('traybar-visible');
      }
      
      switch(AppState.currentTool) {
        case 'hand':
          AppState.mainCanvas.classList.add('hand-mode');
          break;
        case 'select':
          AppState.mainCanvas.classList.add('select-mode');
          break; 
        case 'eraser':
          AppState.mainCanvas.classList.add('eraser-mode');
          AppState.mainCanvas.style.cursor = 'cell';
          break;
        default:
          AppState.mainCanvas.style.cursor = 'default';
      }
    } catch(err) {
      console.error('❌ Error updating cursor:', err);
    }
  },

  /**
   * Handle toolbar button actions
   */
  handleToolbarAction(action) {
    switch(action) {
      case 'move':
        this.selectTool('hand');
        break;
      case 'zoom-in':
        Canvas.zoomIn();
        break;
      case 'fit':
        Canvas.fitToScreen();
        break;
      case 'clear-board':
        if (confirm('Are you sure you want to completely clear the board? This cannot be undone.')) {
          AppState.ctx.clearRect(0, 0, AppState.canvas.width, AppState.canvas.height);
          AppState.boardData.elementsMeta = [];
          if (document.getElementById('canvasElements')) {
            document.getElementById('canvasElements').innerHTML = '';
          }
          History.saveState();
          try { Storage.saveBoardState(); } catch(_){ }
          try {
            if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
              const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
              CollaboSocket.publishElement(boardNumeric, {
                kind: 'erase',
                payload: { x: AppState.canvas.width/2, y: AppState.canvas.height/2, radius: Math.max(AppState.canvas.width, AppState.canvas.height) * 2 }
              });
            }
          } catch(err){}
        }
        break;
      case 'download-image':
        try {
          const dataUrl = AppState.canvas.toDataURL('image/png');
          const a = document.createElement('a');
          a.href = dataUrl;
          a.download = (AppState.boardData.name ? AppState.boardData.name.replace(/[^a-z0-9]/gi, '_').toLowerCase() : 'collabodraw') + '.png';
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
        } catch(err) {
          console.error('Failed to download image', err);
        }
        break;
    }
  },

  /**
   * Toggle traybar visibility
   */
  toggleTraybar() {
    const traybar = document.getElementById('traybar');
    const toggle = document.querySelector('.traybar-toggle');
    const mainCanvas = document.getElementById('mainCanvas');
    
    AppState.traybarVisible = !AppState.traybarVisible;
    
    if (AppState.traybarVisible) {
      traybar.classList.remove('hidden');
      traybar.classList.add('expanded');
      toggle.innerHTML = '◀';
      mainCanvas.classList.add('traybar-visible');
    } else {
      traybar.classList.add('hidden');
      traybar.classList.remove('expanded');
      toggle.innerHTML = '▶';
      mainCanvas.classList.remove('traybar-visible');
    }
  },

  /**
   * Toggle timer start/stop
   */
  toggleTimer() {
    if (AppState.timerRunning) {
      clearInterval(AppState.timerInterval);
      AppState.timerRunning = false;
    } else {
      AppState.timerInterval = setInterval(() => {
        AppState.timerSeconds++;
        this.updateTimerDisplay();
      }, 1000);
      AppState.timerRunning = true;
    }
  },

  /**
   * Update timer display
   */
  updateTimerDisplay() {
    const minutes = Math.floor(AppState.timerSeconds / 60);
    const seconds = AppState.timerSeconds % 60;
    const display = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    const timerEl = document.getElementById('timerDisplay');
    if (timerEl) timerEl.textContent = display;
  },

  /**
   * Enable/disable properties controls depending on selection state
   */
  setPropertiesEnabled(enabled) {
    ['bgColor', 'borderColor', 'opacity'].forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.disabled = !enabled;
    });
  },

  /**
   * Reset properties panel UI when no element is selected
   */
  resetPropertiesPanel() {
    const bgColor = document.getElementById('bgColor');
    const borderColor = document.getElementById('borderColor');
    const opacity = document.getElementById('opacity');

    if (bgColor) bgColor.value = '#ffffff';
    if (borderColor) borderColor.value = '#000000';
    if (opacity) opacity.value = 100;

    this.setPropertiesEnabled(false);
  },

  /**
   * Update properties panel for selected element
   */
  updatePropertiesPanel(element) {
    const bgColor = document.getElementById('bgColor');
    const borderColor = document.getElementById('borderColor');
    const opacity = document.getElementById('opacity');
    
    if (!bgColor || !borderColor || !opacity) return;
    if (!element) {
      this.resetPropertiesPanel();
      return;
    }

    this.setPropertiesEnabled(true);
    
    const computedStyle = window.getComputedStyle(element);
    const bgHex = this.rgbToHex(element.style.backgroundColor || computedStyle.backgroundColor || '#ffffff');
    const borderHex = this.rgbToHex(element.style.borderColor || computedStyle.borderColor || '#000000');
    const opacityValue = Math.round((parseFloat(computedStyle.opacity) || 1) * 100);

    bgColor.value = bgHex || '#ffffff';
    borderColor.value = borderHex || '#000000';
    opacity.value = Number.isFinite(opacityValue) ? Math.max(0, Math.min(100, opacityValue)) : 100;

    const applyToSelected = (fn) => {
      const selected = Array.isArray(AppState.selectedElements) && AppState.selectedElements.length > 0
        ? AppState.selectedElements
        : [element];

      selected.forEach((el) => {
        if (!el) return;
        fn(el);
      });
      History.saveState();
    };
    
    bgColor.onchange = () => {
      applyToSelected((el) => {
        el.style.backgroundColor = bgColor.value;
      });
    };
    
    borderColor.onchange = () => {
      applyToSelected((el) => {
        if (!el.style.borderWidth || el.style.borderWidth === '0px') {
          el.style.borderWidth = '1px';
          el.style.borderStyle = 'solid';
        }
        el.style.borderColor = borderColor.value;
      });
    };
    
    opacity.oninput = () => {
      applyToSelected((el) => {
        el.style.opacity = opacity.value / 100;
      });
    };
  },

  /**
   * Convert RGB to Hex
   */
  rgbToHex(rgb) {
    if (!rgb || typeof rgb !== 'string') return '#000000';
    if (rgb.startsWith('#')) return rgb.length === 9 ? rgb.slice(0, 7) : rgb;
    if (rgb === 'transparent') return '#ffffff';

    const result = rgb.match(/\d+(\.\d+)?/g);
    if (!result || result.length < 3) return '#000000';

    const [r, g, b] = result.slice(0, 3).map((x) => Math.max(0, Math.min(255, Math.round(parseFloat(x)))));
    return '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('');
  },

  /**
   * Show notification/toast
   */
  showNotification(message) {
    const notification = document.getElementById('notification');
    if (notification) {
      notification.innerHTML = message;
      notification.classList.add('show');
    }

    try {
      if (window.NotificationService) {
        window.NotificationService.push(message, 'info');
      }
    } catch (_) {}
    
    setTimeout(() => {
      if (notification) notification.classList.remove('show');
    }, 3000);
  },

  /**
   * Show help/shortcuts
   */
  showHelp() {
    const help = document.getElementById('shortcutsHelp');
    if (help) {
      help.classList.add('show');
      setTimeout(() => {
        help.classList.remove('show');
      }, 5000);
    }
  },

  /**
   * Initialize UI
   */
  initialize() {
    this.updateUserAvatars();
    Storage.updateVersionHistory();
    this.updateActiveUsers();
    this.resetPropertiesPanel();
    
    const boardNameInput = document.getElementById('boardName');
    if (boardNameInput && !boardNameInput.disabled) {
      boardNameInput.addEventListener('blur', function() {
        AppState.boardData.name = this.value;
        History.saveState();
      });
    }
  },

  /**
   * Update user avatars
   */
  updateUserAvatars() {
    const userAvatars = document.getElementById('userAvatars');
    if (!userAvatars) return;
    
    userAvatars.innerHTML = '';
    
    const currentUser = AppState.getCurrentUser();
    if (!Array.isArray(AppState.users) || AppState.users.length === 0) {
      AppState.users = [currentUser];
    } else if (!AppState.users.some(u => (u.id || u.userId) === (currentUser.id || currentUser.userId) || u.name === currentUser.name)) {
      AppState.users = [currentUser, ...AppState.users];
    }
    
    AppState.users.forEach((user, index) => {
      const avatar = document.createElement('div');
      avatar.className = 'avatar';
      avatar.style.background = user.color;
      avatar.dataset.tooltip = user.name + (index === 0 ? ' (You)' : '');
      avatar.textContent = user.initials;
      userAvatars.appendChild(avatar);
    });
  },

  /**
   * Update active users panel
   */
  updateActiveUsers() {
    const activeUsers = document.getElementById('activeUsers');
    if (!activeUsers) return;
    
    if (!Array.isArray(AppState.users) || AppState.users.length === 0) {
      const cu = AppState.getCurrentUser();
      AppState.users = [cu];
    }
    
    const userCount = AppState.users.length;
    
    activeUsers.innerHTML = `
      <div style="margin-bottom: 8px; font-weight: 500;">Online (${userCount})</div>
      ${AppState.users.map(user => `
        <div style="display: flex; align-items: center; gap: 8px; padding: 4px 0;">
          <span style="background: ${user.color}; width: 12px; height: 12px; border-radius: 50%; display: inline-block;"></span>
          <span>${user.name}</span>
        </div>
      `).join('')}
    `;
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = UIControls;
}
