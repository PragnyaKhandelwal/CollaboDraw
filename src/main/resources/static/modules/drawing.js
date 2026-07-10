/**
 * drawing.js - Drawing tools and canvas manipulation
 * Handles pen, highlighter, eraser, and stroke rendering
 */

const SHAPE_TOOLS = ['line', 'rectangle', 'circle', 'arrow'];

const DrawingTools = {
  /**
   * Draw a geometric shape (used for both the live drag preview and the final render, locally
   * and on remote clients) - as opposed to freehand strokes, which are just a traced point path.
   */
  drawShape(ctx, tool, start, end, color, width) {
    const [x1, y1] = start;
    const [x2, y2] = end;
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.globalAlpha = 1;
    ctx.beginPath();

    if (tool === 'line') {
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
    } else if (tool === 'rectangle') {
      ctx.rect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    } else if (tool === 'circle') {
      const cx = (x1 + x2) / 2;
      const cy = (y1 + y2) / 2;
      const rx = Math.max(Math.abs(x2 - x1) / 2, 0.5);
      const ry = Math.max(Math.abs(y2 - y1) / 2, 0.5);
      ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
    } else if (tool === 'arrow') {
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      const angle = Math.atan2(y2 - y1, x2 - x1);
      const headLen = Math.max(10, width * 4);
      ctx.lineTo(x2 - headLen * Math.cos(angle - Math.PI / 6), y2 - headLen * Math.sin(angle - Math.PI / 6));
      ctx.moveTo(x2, y2);
      ctx.lineTo(x2 - headLen * Math.cos(angle + Math.PI / 6), y2 - headLen * Math.sin(angle + Math.PI / 6));
    }

    ctx.stroke();
    ctx.closePath();
    ctx.restore();
  },

  /**
   * Start drawing on canvas
   */
  startDrawing(e) {
    const tool = AppState.currentTool;
    if (!['pen', 'highlighter', ...SHAPE_TOOLS].includes(tool)) return;

    AppState.isDrawing = true;
    const rect = AppState.canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    if (SHAPE_TOOLS.includes(tool)) {
      // Shape tools are a rubber-band drag from a fixed start point to the live cursor
      // position, not a freehand point trail - snapshot the canvas once so each mousemove
      // can restore it and redraw just the current preview, without duplicating past frames.
      window._currentShape = {
        tool,
        start: [x, y],
        color: AppState.currentColor,
        width: 2,
        snapshot: AppState.ctx.getImageData(0, 0, AppState.canvas.width, AppState.canvas.height)
      };
      return;
    }

    window._currentStroke = {
      points: [],
      color: AppState.currentColor,
      tool: AppState.currentTool,
      width: (AppState.currentTool === 'highlighter' ? 8 : 2),
      alpha: (AppState.currentTool === 'highlighter' ? 0.5 : 1)
    };

    if (window._currentStroke) window._currentStroke.points.push([x, y]);

    AppState.ctx.beginPath();
    AppState.ctx.moveTo(x, y);

    AppState.ctx.strokeStyle = AppState.currentColor;
    AppState.ctx.lineWidth = AppState.currentTool === 'highlighter' ? 8 : 2;
    AppState.ctx.lineCap = 'round';
    AppState.ctx.globalAlpha = AppState.currentTool === 'highlighter' ? 0.5 : 1;

    this.renderQueue = [];
    this.lastBroadcast = 0;
    this.lastBroadcastPointCount = 0;
    window._lastRenderedPoint = [x, y];
    if (!this.rendering) {
      this.rendering = true;
      requestAnimationFrame(this.renderLoop.bind(this));
    }
  },

  renderLoop() {
    if (!this.rendering) return;
    if (this.renderQueue && this.renderQueue.length > 0) {
      AppState.ctx.beginPath();
      
      // Ensure we maintain styling in case of overlapping renders
      AppState.ctx.strokeStyle = window._currentStroke ? window._currentStroke.color : AppState.currentColor;
      AppState.ctx.lineWidth = window._currentStroke ? window._currentStroke.width : (AppState.currentTool === 'highlighter' ? 8 : 2);
      AppState.ctx.lineCap = 'round';
      AppState.ctx.globalAlpha = window._currentStroke ? window._currentStroke.alpha : (AppState.currentTool === 'highlighter' ? 0.5 : 1);

      if (window._lastRenderedPoint) {
         AppState.ctx.moveTo(window._lastRenderedPoint[0], window._lastRenderedPoint[1]);
      }
      
      while (this.renderQueue.length > 0) {
        const pt = this.renderQueue.shift();
        AppState.ctx.lineTo(pt[0], pt[1]);
        window._lastRenderedPoint = pt;
      }
      AppState.ctx.stroke();
    }
    if (AppState.isDrawing) {
      requestAnimationFrame(this.renderLoop.bind(this));
    } else {
      this.rendering = false;
      window._lastRenderedPoint = null;
    }
  },

  /**
   * Continue drawing on canvas
   */
  draw(e) {
    if (!AppState.isDrawing) return;

    const rect = AppState.canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    if (window._currentShape) {
      // Rubber-band preview: restore the pre-drag snapshot, then draw the shape fresh from
      // the fixed start point to the live cursor position.
      AppState.ctx.putImageData(window._currentShape.snapshot, 0, 0);
      this.drawShape(AppState.ctx, window._currentShape.tool, window._currentShape.start, [x, y],
        window._currentShape.color, window._currentShape.width);
      window._currentShape.end = [x, y];
      return;
    }

    if (window._currentStroke) {
        window._currentStroke.points.push([x, y]);
        if (!this.renderQueue) this.renderQueue = [];
        this.renderQueue.push([x, y]);
    }
    
    // Progressive broadcast at ~50 FPS or when enough new points accumulate.
    const now = Date.now();
    if (!this.lastBroadcast) this.lastBroadcast = parseInt(now);

    const pointDelta = window._currentStroke ? ((window._currentStroke.points || []).length - (this.lastBroadcastPointCount || 0)) : 0;
    if (window._currentStroke && ((now - this.lastBroadcast > 20) || pointDelta >= 4)) {
      this.lastBroadcast = now;
      try {
        if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
          const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
          const points = window._currentStroke.points || [];
          const startIndex = this.lastBroadcastPointCount > 0 ? Math.max(0, this.lastBroadcastPointCount - 1) : Math.max(0, points.length - 2);
          const partialPoints = points.slice(startIndex);
          if (!partialPoints.length) return;
          CollaboSocket.publishElement(boardNumeric, {
            kind: 'stroke',
            payload: {
              originClientId: AppState.getClientId(),
              points: partialPoints,
              color: window._currentStroke.color,
              width: window._currentStroke.width,
              alpha: window._currentStroke.alpha,
              tool: window._currentStroke.tool,
              partial: true,
              strokeId: window._currentStroke.id || (window._currentStroke.id = AppState.generateId())
            }
          });
          this.lastBroadcastPointCount = points.length;
        }
      } catch(err){ /* silent */ }
    }
  },

  /**
   * Stop drawing and save stroke
   */
  stopDrawing() {
    if (!AppState.isDrawing) return;

    AppState.isDrawing = false;

    if (window._currentShape) {
      const shape = window._currentShape;
      window._currentShape = null;
      // The final preview frame (drawn in draw()) is already the finished shape - just
      // broadcast it, record history, and persist, mirroring the freehand stroke path below.
      try {
        if (window.CD && window.CD.boardId && shape.end && typeof CollaboSocket !== 'undefined') {
          const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
          CollaboSocket.publishElement(boardNumeric, {
            kind: 'shape',
            payload: {
              shapeTool: shape.tool,
              start: shape.start,
              end: shape.end,
              color: shape.color,
              width: shape.width
            }
          });
        }
      } catch (e) { console.warn('Shape broadcast failed', e); }

      History.saveState();
      try { Storage.saveBoardState(); } catch (_) { }
      return;
    }

    this.rendering = false; // ensure rAF loop exits cleanly
    window._lastRenderedPoint = null;
    AppState.ctx.closePath();

    const canvasImage = AppState.canvas.toDataURL('image/png');
    const canvasElement = {
      id: AppState.generateId(),
      type: 'drawing',
      timestamp: Date.now(),
      image: canvasImage,
      tool: AppState.currentTool,
      user: AppState.getCurrentUser().id
    };
    
    if (!Array.isArray(AppState.boardData.elementsMeta)) {
      AppState.boardData.elementsMeta = [];
    }
    AppState.boardData.elementsMeta.push(canvasElement);
    
    // Broadcast final stroke
    try {
      if (window.CD && window.CD.boardId && window._currentStroke && typeof CollaboSocket !== 'undefined') {
        const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
        CollaboSocket.publishElement(boardNumeric, {
          kind: 'stroke',
          payload: {
            originClientId: AppState.getClientId(),
            points: window._currentStroke.points,
            color: window._currentStroke.color,
            width: window._currentStroke.width,
            alpha: window._currentStroke.alpha,
            tool: window._currentStroke.tool,
            partial: false,
            strokeId: window._currentStroke.id || (window._currentStroke.id = AppState.generateId())
          }
        });
      }
    } catch(e){ console.warn('Stroke broadcast failed', e); }
    
    window._currentStroke = null;
    
    // Save state
    History.saveState();
    try { Storage.saveBoardState(); } catch(_){ }
  },

  /**
   * Activate eraser tool
   */
  activateEraser() {
    UIControls.selectTool('eraser');
    const mainCanvas = document.getElementById('mainCanvas');
    mainCanvas.classList.add('eraser-mode');
  },

  /**
   * Handle eraser click on canvas
   */
  handleEraserClick(e) {
    if (AppState.currentTool !== 'eraser') return;
    
    const rect = AppState.canvas.getBoundingClientRect();
    const eraserX = e.clientX - rect.left;
    const eraserY = e.clientY - rect.top;
    const eraserRadius = 20;
    
    AppState.ctx.clearRect(
      eraserX - eraserRadius,
      eraserY - eraserRadius,
      eraserRadius * 2,
      eraserRadius * 2
    );
        
    History.saveState();
    
    try {
      if (window.CD && window.CD.boardId && typeof CollaboSocket !== 'undefined') {
        const boardNumeric = String(window.CD.boardId).replace(/^board-/, '');
        CollaboSocket.publishElement(boardNumeric, {
          kind: 'erase',
          payload: { x: eraserX, y: eraserY, radius: eraserRadius }
        });
      }
    } catch(e){}
    
    try { Storage.saveBoardState(); } catch(_){ }
  },

  /**
   * Render remote strokes received from other users
   */
  renderRemoteStroke(payload) {
    if (!payload || !Array.isArray(payload.points)) return;
    
    const sid = payload.strokeId || 'unknown';
    window._remoteStrokePaths = window._remoteStrokePaths || {};
    const pts = payload.points;
    const isPartial = !!payload.partial;

    const drawPath = (pathPoints, fromIndex) => {
      if (!Array.isArray(pathPoints) || pathPoints.length < 2 || fromIndex >= pathPoints.length) return;
      AppState.ctx.save();
      AppState.ctx.lineCap = 'round';
      AppState.ctx.strokeStyle = payload.color || '#000';
      AppState.ctx.globalAlpha = payload.alpha != null ? payload.alpha : 1;
      AppState.ctx.lineWidth = payload.width || 2;
      AppState.ctx.beginPath();
      const start = Math.max(1, fromIndex);
      AppState.ctx.moveTo(pathPoints[start - 1][0], pathPoints[start - 1][1]);
      for (let i = start; i < pathPoints.length; i++) {
        AppState.ctx.lineTo(pathPoints[i][0], pathPoints[i][1]);
      }
      AppState.ctx.stroke();
      AppState.ctx.closePath();
      AppState.ctx.restore();
    };

    const existing = window._remoteStrokePaths[sid] || { renderedCount: 0, finalized: false };
    if (existing.finalized) return;

    if (isPartial) {
      // Partial packets can arrive out of order; only append truly new tail points.
      const startIndex = Math.max(1, Math.min(existing.renderedCount, pts.length - 1));
      drawPath(pts, startIndex);
      existing.renderedCount = Math.max(existing.renderedCount, pts.length);
      existing.lastPoint = pts[pts.length - 1] || existing.lastPoint;
      existing.partial = true;
      window._remoteStrokePaths[sid] = existing;
      return;
    }

    // Final packet must guarantee completeness even if some partial packets were dropped.
    if (pts.length >= 2) {
      if (existing.renderedCount < pts.length) {
        const startIndex = Math.max(1, existing.renderedCount);
        drawPath(pts, startIndex);
      } else if (existing.renderedCount <= 1) {
        drawPath(pts, 1);
      }
    }

    existing.renderedCount = Math.max(existing.renderedCount, pts.length);
    existing.lastPoint = pts[pts.length - 1] || existing.lastPoint;
    existing.partial = false;
    existing.finalized = true;
    window._remoteStrokePaths[sid] = existing;
  },

  /**
   * Render a shape (line/rectangle/circle/arrow) broadcast by another collaborator.
   */
  renderRemoteShape(payload) {
    if (!payload || !Array.isArray(payload.start) || !Array.isArray(payload.end)) return;
    this.drawShape(AppState.ctx, payload.shapeTool, payload.start, payload.end,
      payload.color || '#000', payload.width || 2);
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = DrawingTools;
}
