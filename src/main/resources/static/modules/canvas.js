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

    const parseColorToRgb = (color) => {
      if (!color || typeof color !== 'string') return null;

      const hex = color.trim().match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
      if (hex) {
        let value = hex[1];
        if (value.length === 3) {
          value = value.split('').map((ch) => ch + ch).join('');
        }
        return {
          r: parseInt(value.slice(0, 2), 16),
          g: parseInt(value.slice(2, 4), 16),
          b: parseInt(value.slice(4, 6), 16)
        };
      }

      const rgb = color.match(/^rgba?\(([^)]+)\)$/i);
      if (rgb) {
        const parts = rgb[1].split(',').map((p) => parseFloat(p.trim()));
        if (parts.length >= 3) {
          return { r: parts[0], g: parts[1], b: parts[2] };
        }
      }

      const hsl = color.match(/^hsla?\(([^)]+)\)$/i);
      if (hsl) {
        const parts = hsl[1].split(',').map((p) => p.trim());
        if (parts.length >= 3) {
          const h = ((parseFloat(parts[0]) % 360) + 360) % 360;
          const s = Math.max(0, Math.min(100, parseFloat(parts[1]))) / 100;
          const l = Math.max(0, Math.min(100, parseFloat(parts[2]))) / 100;
          const c = (1 - Math.abs(2 * l - 1)) * s;
          const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
          const m = l - c / 2;
          let r1 = 0, g1 = 0, b1 = 0;

          if (h < 60) { r1 = c; g1 = x; }
          else if (h < 120) { r1 = x; g1 = c; }
          else if (h < 180) { g1 = c; b1 = x; }
          else if (h < 240) { g1 = x; b1 = c; }
          else if (h < 300) { r1 = x; b1 = c; }
          else { r1 = c; b1 = x; }

          return {
            r: Math.round((r1 + m) * 255),
            g: Math.round((g1 + m) * 255),
            b: Math.round((b1 + m) * 255)
          };
        }
      }

      return null;
    };

    const getReadableTextColor = (bgColor) => {
      const rgb = parseColorToRgb(bgColor);
      if (!rgb) return '#ffffff';
      const luminance = (0.299 * rgb.r + 0.587 * rgb.g + 0.114 * rgb.b);
      return luminance > 165 ? '#111827' : '#ffffff';
    };
    
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
  label.style.background = c.color || 'rgba(16, 185, 129, 0.95)';
  label.style.color = getReadableTextColor(c.color);
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
