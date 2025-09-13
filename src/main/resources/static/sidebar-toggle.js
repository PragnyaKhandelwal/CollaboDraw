
    function toggleSidebar() {
      document.getElementById('sidebar').classList.toggle('collapsed');
    }

    // Add click effects to cards
    document.querySelectorAll('.card').forEach(card => {
      card.addEventListener('click', () => {
        console.log('Card clicked');
      });
    });
 