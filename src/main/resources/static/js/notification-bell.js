/**
 * notification-bell.js - wires a header notification bell button + dropdown to real data
 * from /api/notifications/recent, shared across every page's header. Previously each page
 * either had no handler at all, or a handler that only ever displayed a hardcoded
 * "No new notifications" string.
 */
(function () {
  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function timeAgo(iso) {
    if (!iso) return '';
    const ts = Date.parse(iso);
    if (Number.isNaN(ts)) return '';
    const sec = Math.max(1, Math.floor((Date.now() - ts) / 1000));
    if (sec < 60) return sec + 's ago';
    const min = Math.floor(sec / 60);
    if (min < 60) return min + 'm ago';
    const hr = Math.floor(min / 60);
    if (hr < 24) return hr + 'h ago';
    return Math.floor(hr / 24) + 'd ago';
  }

  function render(listEl, badgeEl, items) {
    if (!items || !items.length) {
      listEl.innerHTML =
        '<div style="padding:12px 16px; font-size:14px;">' +
        '<div style="margin-bottom:8px;">No new notifications</div>' +
        '<div style="color:var(--gray,#6b7280);">You\'re all caught up.</div>' +
        '</div>';
      if (badgeEl) badgeEl.style.display = 'none';
      return;
    }

    listEl.innerHTML = items.map((n) => {
      const unreadBg = n.read ? '' : 'background:var(--light-gray,#f0fdf9);';
      const dot = n.read ? '' : '<span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:var(--main-green,#20b97c);margin-right:6px;"></span>';
      return (
        '<div class="notif-item" data-id="' + n.id + '" style="padding:10px 16px; border-bottom:1px solid var(--border,#f3f4f6); cursor:pointer; color:var(--text-dark,#111827); ' + unreadBg + '">' +
        '<div style="font-size:13.5px; font-weight:600;">' + dot + escapeHtml(n.title || 'Notification') + '</div>' +
        (n.message ? '<div style="font-size:12.5px; color:var(--gray,#4b5563); margin-top:2px;">' + escapeHtml(n.message) + '</div>' : '') +
        '<div style="font-size:11px; color:var(--gray,#9ca3af); margin-top:4px;">' + escapeHtml(timeAgo(n.createdAt)) + '</div>' +
        '</div>'
      );
    }).join('');

    const unreadCount = items.filter((n) => !n.read).length;
    if (badgeEl) badgeEl.style.display = unreadCount > 0 ? 'inline-block' : 'none';

    listEl.querySelectorAll('.notif-item').forEach((el) => {
      el.addEventListener('click', () => {
        const id = el.getAttribute('data-id');
        const item = items.find((n) => String(n.id) === id);
        fetch(`/api/notifications/${id}/read`, { method: 'POST', credentials: 'include' }).catch(() => {});
        if (item && item.linkUrl) {
          window.location.href = item.linkUrl;
        } else {
          el.style.background = '';
          const dotEl = el.querySelector('span');
          if (dotEl) dotEl.remove();
        }
      });
    });
  }

  async function refresh(listEl, badgeEl) {
    try {
      const res = await fetch('/api/notifications/recent', { credentials: 'include' });
      if (!res.ok) return;
      const data = await res.json();
      if (data && data.success) render(listEl, badgeEl, data.items || []);
    } catch (_) { /* leave existing content on transient failure */ }
  }

  /**
   * @param {string} btnId - the bell button's id
   * @param {string} dropdownId - the dropdown container's id
   * @param {string} listId - id of the element inside the dropdown that holds the list
   * @param {string} [badgeId] - optional unread-count badge element id on the button
   */
  window.NotificationBell = {
    init(btnId, dropdownId, listId, badgeId) {
      const btn = document.getElementById(btnId);
      const dropdown = document.getElementById(dropdownId);
      const list = document.getElementById(listId);
      const badge = badgeId ? document.getElementById(badgeId) : null;
      if (!btn || !dropdown || !list) return;

      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const opening = dropdown.style.display === 'none' || !dropdown.style.display;
        dropdown.style.display = opening ? 'block' : 'none';
        if (opening) refresh(list, badge);
      });

      document.addEventListener('click', (e) => {
        if (dropdown.style.display !== 'none' && !dropdown.contains(e.target) && e.target !== btn) {
          dropdown.style.display = 'none';
        }
      });

      refresh(list, badge);
      setInterval(() => refresh(list, badge), 60000);
    }
  };
})();
