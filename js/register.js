import { registerUser } from './api.js';

document.addEventListener('DOMContentLoaded', () => {
  const registerForm = document.getElementById('register-form');
  
  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      // Prevent browser redirect on submit
      e.preventDefault();
      
      const name = document.getElementById('reg-name').value.trim();
      const email = document.getElementById('reg-email').value.trim();
      const password = document.getElementById('reg-password').value;
      const department = document.getElementById('reg-dept').value;
      
      // Client-side validation check
      if (!name || !email || !password || !department) {
        alert('All fields are required to register.');
        return;
      }
      
      const submitButton = registerForm.querySelector('button[type="submit"]');
      const originalHTML = submitButton.innerHTML;
      submitButton.disabled = true;
      submitButton.innerHTML = `<span class="material-symbols-outlined rotating" style="font-size: 20px;">sync</span> Registering...`;
      
      try {
        const response = await registerUser(name, email, password, department);
        
        if (response.success) {
          alert('User registered successfully! Redirecting to login portal...');
          // Redirect to sign in page
          window.location.replace('index.html');
        } else {
          alert(response.message || 'Registration failed. Please try again.');
          submitButton.disabled = false;
          submitButton.innerHTML = originalHTML;
        }
      } catch (error) {
        alert(error.message || 'Error connecting to the Tomcat backend server.');
        submitButton.disabled = false;
        submitButton.innerHTML = originalHTML;
      }
    });
  }
});
