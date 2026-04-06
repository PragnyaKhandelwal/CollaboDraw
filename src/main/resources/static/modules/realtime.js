/**
 * realtime.js - Real-time collaboration via WebSocket
 * Handles STOMP subscription, remote cursors, element sync
 */

const RealTime = {
  _serverParticipants: [],

  resetPresenceState() {
    this._serverParticipants = [];
    AppState.remoteCursors = {};
    AppState.users = [];
    AppState._lastParticipants = new Set();
  },

  getPresenceIdentity(userId, username, clientId) {
    if (userId !== null && userId !== undefined && userId !== '') {
      return `user:${userId}`;
    }
    if (clientId) {
      return `client:${clientId}`;
    }
    if (username) {
      return `name:${String(username).toLowerCase()}`;
    }
    return null;
  },

  rebuildPresenceUsers() {
    const usersById = new Map();

    const upsert = (identity, userId, name, color) => {
      if (!identity) return;
      usersById.set(String(identity), {
        id: String(identity),
        userId: userId ?? identity,
        userId: id,
        name: name || 'User',
        initials: ((name || 'U').substring(0, 2) || 'U').toUpperCase(),
        color: color || this.colorFromString(String(name || id))
      });
    };

    const me = AppState.getCurrentUser();
    const meIdentity = this.getPresenceIdentity(window.CD && window.CD.currentUserId, me && me.name, me && me.id);
    if (meIdentity) {
      upsert(meIdentity, window.CD && window.CD.currentUserId ? window.CD.currentUserId : me.id, me.name, me.color);
    }

    (this._serverParticipants || []).forEach((p) => {
      const identity = this.getPresenceIdentity(p.userId, p.name, null);
      upsert(identity, p.userId, p.name, p.color);
    });

    Object.keys(AppState.remoteCursors || {}).forEach((key) => {
      const c = AppState.remoteCursors[key];
      const identity = this.getPresenceIdentity(c.userId, c.name, c.clientId || key);
      upsert(identity, c.userId, c.name, c.color);
    });

    AppState.users = Array.from(usersById.values());
    UIControls.updateActiveUsers();
    this.updateAvatarDisplay();
  },

  /**
   * Start real-time synchronization
   */
  startSync() {
    try {
      this.resetPresenceState();

      let bid = window.CD && window.CD.boardId;
      if (!bid) return;
      if (typeof bid === 'string') bid = parseInt(bid.replace(/^board-/, ''), 10);
      if (typeof bid === 'number') bid = parseInt(bid, 10);
      if (!bid || isNaN(bid)) return;
      AppState.wsBoardId = bid;

      if (!window.CollaboSocket) {
        console.warn('Realtime client (CollaboSocket) not loaded');
        return;
      }

      CollaboSocket.connect(() => {
        CollaboSocket.joinBoard(AppState.wsBoardId);
        CollaboSocket.startHeartbeat(AppState.wsBoardId, 15000);

        this.fetchAndReplayEvents(AppState.wsBoardId).catch(e => console.warn('Replay failed', e));

        // Participants
        if (AppState.wsSubscriptions.participants) { try { AppState.wsSubscriptions.participants.unsubscribe(); } catch(_){} }
        AppState.wsSubscriptions.participants = CollaboSocket.subscribeParticipants(AppState.wsBoardId, (items) => {
          this.handleParticipants(items);
        });

        // Cursors
        if (AppState.wsSubscriptions.cursors) { try { AppState.wsSubscriptions.cursors.unsubscribe(); } catch(_){} }
        AppState.wsSubscriptions.cursors = CollaboSocket.subscribeCursors(AppState.wsBoardId, (evt) => {
          this.handleCursorEvent(evt);
        });

        // Versions
        if (AppState.wsSubscriptions.versions) { try { AppState.wsSubscriptions.versions.unsubscribe(); } catch(_){} }
        AppState.wsSubscriptions.versions = CollaboSocket.subscribeVersions(AppState.wsBoardId, (evt) => {
          this.handleVersionEvent(evt);
        });

        // Elements
        if (AppState.wsSubscriptions.elements) { try { AppState.wsSubscriptions.elements.unsubscribe(); } catch(_){} }
        AppState.wsSubscriptions.elements = CollaboSocket.subscribeElements(AppState.wsBoardId, (payload, meta) => {
          this.handleElementEvent(payload, meta);
        });
        // Events
        window.addEventListener('rt:connected', () => {
          const overlay = document.getElementById('connectionOverlay');
          if (overlay) overlay.style.display = 'none';
        });
        window.addEventListener('rt:disconnected', () => {
          const overlay = document.getElementById('connectionOverlay');
          if (overlay) overlay.style.display = 'flex';
        });

      });

      window.addEventListener('beforeunload', () => {
        try { if (AppState.wsBoardId) CollaboSocket.leaveBoard(AppState.wsBoardId); } catch(_){}
        try { CollaboSocket.disconnect(); } catch(_){}
      });
    } catch (e) {
      console.warn('Failed to start realtime sync:', e);
    }
  },

  /**
   * Handle participant join/leave events
   */
  handleParticipants(items) {
    try {
      const mapped = (items || []).map(p => ({
        id: p.userId,
        userId: p.userId,
        name: p.username,
        initials: (p.username || 'U').substring(0,2).toUpperCase(),
        color: this.colorFromString(p.username || String(p.userId))
      }));

      const current = new Set(mapped.map(m => `user:${m.userId}`));
      const joined = [];
      const left = [];
      
      current.forEach(n => { if (!AppState._lastParticipants.has(n)) joined.push(n); });
      AppState._lastParticipants.forEach(n => { if (!current.has(n)) left.push(n); });
      
      if (joined.length) this.notify('A collaborator joined');
      if (left.length) this.notify('A collaborator left');
      
      AppState._lastParticipants = current;
      this._serverParticipants = mapped;
      this.rebuildPresenceUsers();
    } catch (e) { console.warn('participants mapping failed', e); }
  },

  /**
   * Handle cursor position updates
   */
  handleCursorEvent(evt) {
    if (!evt || evt.type !== 'cursor') return;
    
    const myClientId = AppState.getClientId();
    const remoteName = evt.displayName || evt.username || String(evt.userId || '');
    if (evt.clientId && myClientId && evt.clientId === myClientId) return;
    
    const key = evt.userId || evt.clientId || remoteName || 'unknown';
    AppState.remoteCursors[key] = {
      x: evt.x || 0,
      y: evt.y || 0,
      name: remoteName,
      userId: evt.userId || null,
      clientId: evt.clientId || null,
      color: this.colorFromString(remoteName || String(evt.userId || ''))
    };

    this.rebuildPresenceUsers();
    Canvas.renderRemoteCursors();
  },

  /**
   * Handle remote element events
   */
  handleElementEvent(payload, meta) {
    try {
      if (!meta || !meta.kind) return;
      const kind = meta.kind;
      const myClientId = AppState.getClientId();

      // Ignore only this tab's own live stroke echoes; do not suppress same-user other tabs.
      if (!meta.replay && kind === 'stroke' && payload && payload.originClientId && myClientId && payload.originClientId === myClientId) {
        return;
      }
      
      if (kind === 'stroke' && payload && Array.isArray(payload.points)) {
        DrawingTools.renderRemoteStroke(payload);
      } else if (kind === 'sticky' && payload) {
        if (!document.querySelector(`[data-id="${payload.id}"]`)) {
          const el = ElementManager.createStickyNote(payload.x, payload.y);
          if (el) el.dataset.id = payload.id;
          if (el && payload.zIndex) {
            el.style.zIndex = payload.zIndex;
            if (parseInt(payload.zIndex, 10) > ElementManager.lastZIndex) ElementManager.lastZIndex = parseInt(payload.zIndex, 10);
          }
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
          const el = ElementManager.createTextElement(payload.x, payload.y);
          if (el) el.dataset.id = payload.id;
          if (el && payload.zIndex) {
            el.style.zIndex = payload.zIndex;
            if (parseInt(payload.zIndex, 10) > ElementManager.lastZIndex) ElementManager.lastZIndex = parseInt(payload.zIndex, 10);
          }
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
          if (payload.zIndex) {
             el.style.zIndex = payload.zIndex;
             if (parseInt(payload.zIndex, 10) > ElementManager.lastZIndex) ElementManager.lastZIndex = parseInt(payload.zIndex, 10);
          }
        }
      } else if (kind === 'erase' && payload) {
        const r = payload.radius || 20;
        AppState.ctx.clearRect((payload.x||0) - r, (payload.y||0) - r, r*2, r*2);
      }
    } catch(err){ console.warn('element event handling failed', err); }
  },

  /**
   * Handle version events
   */
  handleVersionEvent(evt) {
    try {
      if (!evt || evt.type !== 'version') return;
      const list = Storage.getVersionHistory();
      if (!list.find(v => v.id === evt.id)) {
        const merged = [
          { id: evt.id || AppState.generateId(), timestamp: evt.timestamp || new Date().toLocaleTimeString(), description: evt.description || 'Update', data: null },
          ...list
        ].slice(0,10);
        localStorage.setItem('collabodraw-versions', JSON.stringify(merged));
        Storage.updateVersionHistory();
      }
    } catch(e){ console.warn('version event handling failed', e); }
  },

  /**
   * Fetch and replay prior events
   */
  async fetchAndReplayEvents(bid) {
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
          this.handleElementEvent(payload, { kind, by: ev.by || ev.meta?.by, userId: ev.meta?.userId, replay: true });
        } catch(re){ console.warn('Replay event failed', re); }
      });
    } catch (e) {
      console.warn('Failed to fetch replay events', e);
    }
  },

  /**
   * Generate color from string (for consistent user colors)
   */
  colorFromString(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = str.charCodeAt(i) + ((hash << 5) - hash);
    }
    const hue = (hash % 360);
    return `hsl(${hue}, 70%, 50%)`;
  },

  /**
   * Update avatar display
   */
  updateAvatarDisplay() {
    const avatars = document.getElementById('userAvatars');
    if (!avatars) return;
    avatars.innerHTML = '';
    AppState.users.forEach((u, index) => {
      const avatar = document.createElement('div');
      avatar.className = 'avatar';
      avatar.style.background = u.color;
      avatar.dataset.tooltip = u.name + (index === 0 ? ' (You)' : '');
      avatar.textContent = (u.initials || 'U');
      avatars.appendChild(avatar);
    });
  },

  /**
   * Simple toast notification
   */
  notify(message, timeoutMs = 2500) {
    try {
      try {
        if (window.NotificationService) {
          window.NotificationService.push(message, 'info');
        }
      } catch (_) {}

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
        setTimeout(() => toast.remove(), 150);
      }, timeoutMs);
    } catch (_) {}
  }
};

if (typeof module !== 'undefined' && module.exports) {
  module.exports = RealTime;
}
