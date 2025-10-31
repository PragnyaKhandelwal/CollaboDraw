// Sidebar toggle behavior for pages with a left nav sidebar
(function () {
  function bindSidebarToggle() {
    const sidebar = document.getElementById('sidebar');
    const toggleBtn = document.getElementById('toggleBtn');
    if (!sidebar || !toggleBtn) return; // nothing to wire

    function setChevron() {
      // Show › when collapsed (to expand), ‹ when expanded (to collapse)
      toggleBtn.innerHTML = sidebar.classList.contains('collapsed') ? '›' : '‹';
      toggleBtn.setAttribute(
        'aria-label',
        sidebar.classList.contains('collapsed') ? 'Expand sidebar' : 'Collapse sidebar'
      );
    }

    function toggle() {
      sidebar.classList.toggle('collapsed');
      setChevron();
    }

    // Click + keyboard support
    toggleBtn.addEventListener('click', function (e) {
      e.preventDefault();
      toggle();
    });
    toggleBtn.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ' || e.code === 'Space') {
        e.preventDefault();
        toggle();
      }
    });

    // Auto-collapse on small screens
    function checkMobile() {
      if (window.innerWidth <= 700) {
        if (!sidebar.classList.contains('collapsed')) sidebar.classList.add('collapsed');
      } else {
        if (sidebar.classList.contains('collapsed')) sidebar.classList.remove('collapsed');
      }
      setChevron();
    }
    window.addEventListener('resize', checkMobile);
    checkMobile();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bindSidebarToggle);
  } else {
    bindSidebarToggle();
  }

  // Fallback: delegate in case binding missed for any reason
  document.addEventListener('click', function (e) {
    const btn = e.target.closest && e.target.closest('#toggleBtn');
    const sidebar = document.getElementById('sidebar');
    if (btn && sidebar) {
      e.preventDefault();
      sidebar.classList.toggle('collapsed');
      btn.innerHTML = sidebar.classList.contains('collapsed') ? '›' : '‹';
      btn.setAttribute('aria-label', sidebar.classList.contains('collapsed') ? 'Expand sidebar' : 'Collapse sidebar');
    }
  });

  // Optional: click effects to any generic ".card" elements (non-breaking)
  try {
    document.querySelectorAll('.card').forEach((card) => {
      card.addEventListener('click', () => {
        // placeholder effect; safe to keep for dev
        // console.log('Card clicked');
      });
    });
  } catch {}
})();
 