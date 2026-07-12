/**
 * AssetFlow ERP - Authentication Guard
 * Prevents unauthenticated users from accessing protected ERP views.
 * Should be loaded in the <head> of protected pages to block rendering before redirect.
 */
(function() {
  const userRole = localStorage.getItem('assetflow_user_role');
  const userName = localStorage.getItem('assetflow_user_name');
  
  // If no authentication keys are found, redirect to login page (index.html)
  if (!userRole || !userName) {
    console.warn('Access denied. Redirecting to authentication portal...');
    window.location.replace('index.html');
  }
})();
