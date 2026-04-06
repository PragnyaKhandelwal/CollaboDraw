/**
 * canvas.js - Canvas rendering and view management
 * Handles zoom, pan, canvas resizing, and remote cursor rendering
 */

const Canvas = {
  /**
   * Resize canvas to match viewport
   */
  resizeCanvas() {
    const rect = AppState.mainCanvas.getBoundingClientRect();
    AppState.canvas.width = rect.width;
    AppState.canvas.height = rect.height;
  },

  /**
   * Zoom in
   */
  zoomIn() {
    AppState.zoomLevel = Math.min(AppState.zoomLevel * AppState.CONFIG.ZOOM_STEP, AppState.CONFIG.ZOOM_MAX);
    this.updateZoom();
  },

  /**
   * Zoom out
   */
  zoomOut() {
    AppState.zoomLevel = Math.max(AppState.zoomLevel / AppState.CONFIG.ZOOM_STEP, AppState.CONFIG.ZOOM_MIN);
    this.updateZoom();
  },

  /**
   * Fit to screen
   */
  fitToScreen() {
    AppState.zoomLevel = 1;
    AppState.panX = 0;
    AppState.panY = 0;
    this.updateZoom();
  },

  /**
   * Update zoom transform
   */
  updateZoom() {
    AppState.mainCanvas.style.transform = `scale(${AppState.zoomLevel}) translate(${AppState.panX}px, ${AppState.panY}px)`;
    const zoomEl = document.getElementById('zoomLevel');
    if (zoomEl) {
      zoomEl.textContent = Math.round(AppState.zoomLevel * 100) + '%';
    }
  },

  /**
   * Render remote user cursors
   */
  renderRemoteCursors() {
    const container = document.getElementById('userCursors');
    if (!container) return;
    container.innerHTML = '';
    
    Object.keys(AppState.remoteCursors).forEach(key => {
      const c = AppState.remoteCursors[key];
      const el = document.createElement('div');
      el.className = 'user-cursor';
      el.style.position = 'absolute';
      el.style.left = `${Math.max(0, Math.floor(c.x))}px`;
      el.style.top = `${Math.max(0, Math.floor(c.y))}px`;
      el.style.pointerEvents = 'none';
      el.style.transform = 'translate(-4px, -28px)';

      const bubble = document.createElement('div');
      bubble.style.display = 'inline-flex';
      bubble.style.flexDirection = 'column';
      bubble.style.alignItems = 'flex-start';
      bubble.style.gap = '4px';

      const label = document.createElement('div');
      label.textContent = c.name || 'User';
      label.style.background = 'rgba(16, 185, 129, 0.95)';
      label.style.color = '#fff';
      label.style.padding = '4px 9px';
      label.style.borderRadius = '999px';
      label.style.fontSize = '12px';
      label.style.fontWeight = '700';
      label.style.lineHeight = '1';
      label.style.whiteSpace = 'nowrap';
      label.style.boxShadow = '0 6px 14px rgba(0,0,0,0.18)';

      const pointer = document.createElement('div');
      pointer.style.width = '10px';
      pointer.style.height = '10px';
      pointer.style.borderRadius = '50%';
      pointer.style.background = c.color;
      pointer.style.boxShadow = '0 0 0 2px rgba(255,255,255,0.95), 0 4px 10px rgba(0,0,0,0.15)';

      bubble.appendChild(label);
      bubble.appendChild(pointer);
      el.appendChild(bubble);
      container.appendChild(el);
    });
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = Canvas;
}
