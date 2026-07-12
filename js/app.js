document.addEventListener('DOMContentLoaded', () => {
  // Mobile sidebar navigation drawer toggle
  const menuBtn = document.querySelector('.menu-toggle');
  const sidebar = document.querySelector('.app-sidebar');
  
  if (menuBtn && sidebar) {
    menuBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      sidebar.classList.toggle('open');
    });

    document.addEventListener('click', (e) => {
      if (sidebar.classList.contains('open') && !sidebar.contains(e.target) && e.target !== menuBtn) {
        sidebar.classList.remove('open');
      }
    });
  }

  // Highlight active link based on current path
  const currentPath = window.location.pathname;
  const navLinks = document.querySelectorAll('.sidebar-menu-item');
  
  navLinks.forEach(item => {
    const link = item.querySelector('a');
    if (link) {
      const href = link.getAttribute('href');
      // If href matches or currentPath ends with href (or if both are root/index)
      if (href && (currentPath.endsWith(href) || (currentPath === '/' && href === 'index.html') || (currentPath.endsWith('/') && href === 'index.html'))) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    }
  });

  // Global header search interactions
  const searchInput = document.querySelector('.header-search-input');
  if (searchInput) {
    searchInput.addEventListener('keypress', (e) => {
      if (e.key === 'Enter') {
        const query = searchInput.value.trim();
        if (query) {
          console.log(`Global search initiated: ${query}`);
          // If we are on assets page, forward query
          if (window.location.pathname.includes('assets.html')) {
            const assetSearch = document.getElementById('asset-search');
            if (assetSearch) {
              assetSearch.value = query;
              assetSearch.dispatchEvent(new Event('input'));
            }
          } else {
            // Redirect to assets page with query param
            window.location.href = `assets.html?search=${encodeURIComponent(query)}`;
          }
        }
      }
    });
  }

  // Close overdue alerts
  const alertBanners = document.querySelectorAll('.alert-banner');
  alertBanners.forEach(banner => {
    const closeBtn = banner.querySelector('.alert-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        banner.style.opacity = '0';
        banner.style.transform = 'translateY(-10px)';
        banner.style.transition = 'all 0.3s ease';
        setTimeout(() => banner.classList.add('hide'), 300);
      });
    }
  });

  // Global Logout Trigger Handler
  const logoutLinks = document.querySelectorAll('.sidebar-menu a, .sidebar-footer a');
  logoutLinks.forEach(link => {
    const text = link.textContent.toLowerCase();
    if (text.includes('log out') || text.includes('logout')) {
      link.addEventListener('click', (e) => {
        e.preventDefault();
        
        // Clear all session key values in local storage
        localStorage.removeItem('assetflow_user_role');
        localStorage.removeItem('assetflow_user_name');
        localStorage.removeItem('assetflow_user_email');
        
        // Redirect user back to the login portal (index.html)
        window.location.replace('index.html');
      });
    }
  });
});
