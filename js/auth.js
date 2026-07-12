import { loginUser } from './api.js';

document.addEventListener('DOMContentLoaded', () => {
  const loginForm = document.getElementById('login-form');
  
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      // Prevent standard browser form submit reload
      e.preventDefault();
      
      const emailInput = document.getElementById('email');
      const passwordInput = document.getElementById('password');
      
      const email = emailInput.value.trim();
      const password = passwordInput.value;
      
      if (!email || !password) {
        alert('Please enter both your email address and password.');
        return;
      }
      
      // Disable form inputs and show loading state on button if possible
      const submitButton = loginForm.querySelector('button[type="submit"]');
      const originalButtonHTML = submitButton.innerHTML;
      submitButton.disabled = true;
      submitButton.innerHTML = `<span class="material-symbols-outlined rotating" style="font-size: 20px;">sync</span> Authenticating...`;
      
      try {
        // Send request to Java Servlet backend
        const response = await loginUser(email, password);
        
        if (response.success) {
          // Store user name and role in local storage
          localStorage.setItem('assetflow_user_name', response.name);
          localStorage.setItem('assetflow_user_role', response.role);
          localStorage.setItem('assetflow_user_email', email);
          
          // Redirect authenticated user to the main dashboard
          window.location.replace('dashboard.html');
        } else {
          // Show error message returned by servlet
          alert(response.message || 'Login failed. Please check your credentials.');
          submitButton.disabled = false;
          submitButton.innerHTML = originalButtonHTML;
        }
      } catch (error) {
        // Show network or server connection errors
        alert(error.message || 'Network error: Unable to reach the Tomcat backend server.');
        submitButton.disabled = false;
        submitButton.innerHTML = originalButtonHTML;
      }
    });
  }
});
