// Lightweight notification service for toasts + dropdown history.
(function () {
  const STORAGE_KEY = 'collabodraw-notifications';
  const MAX_ITEMS = 30;

  function load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const arr = raw ? JSON.parse(raw) : [];
      return Array.isArray(arr) ? arr : [];
    } catch (_) {
      return [];
    }
  }

  function save(items) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_ITEMS)));
    } catch (_) {}
  }

  function formatTime(iso) {
    try {
      return new Date(iso).toLocaleTimeString();
    } catch (_) {
      return '';
    }
  }

  function renderDropdown() {
    const dropdown = document.getElementById('notifDropdown');
    if (!dropdown) return;

    const items = load();
    const listHost = document.getElementById('notifList');
    if (!listHost) return;

    if (!items.length) {
      listHost.innerHTML = '<div style="margin-bottom:8px">No new notifications</div><div style="color:var(--gray,#6b7280)">You\'re all caught up.</div>';
      return;
    }

    listHost.innerHTML = items.map((n) => {
      const color = n.type === 'error' ? '#ef4444' : (n.type === 'success' ? '#16a34a' : '#2563eb');
      return '<div style="padding:8px 0;border-bottom:1px solid var(--border,#f3f4f6)">'
        + '<div style="font-size:13px;color:var(--text-dark,#111827)"><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:' + color + ';margin-right:8px"></span>'
        + String(n.message || '') + '</div>'
        + '<div style="font-size:11px;color:var(--gray,#6b7280);margin-left:16px">' + formatTime(n.ts) + '</div>'
        + '</div>';
    }).join('');
  }

  function push(message, type) {
    const m = String(message || '').trim();
    if (!m) return;
    const items = load();
    items.unshift({
      message: m,
      type: type || 'info',
      ts: new Date().toISOString()
    });
    save(items);
    renderDropdown();
  }

  function clear() {
    save([]);
    renderDropdown();
  }

  window.NotificationService = {
    push,
    clear,
    render: renderDropdown
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', renderDropdown);
  } else {
    renderDropdown();
  }
})();
