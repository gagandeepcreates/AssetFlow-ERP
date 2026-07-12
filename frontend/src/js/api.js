/**
 * AssetFlow ERP Frontend Integration Utility
 * Handles communication between Vite Frontend (localhost:5173) and Java Servlet Backend (localhost:8080)
 */

// Base URL pointing to the Apache Tomcat servlet app context
const BASE_URL = 'http://localhost:8080/AssetFlow';

/**
 * Generic asynchronous function to communicate with the Java backend servlets.
 * 
 * @param {string} endpoint - API resource endpoint (e.g., '/login', '/register')
 * @param {string} method - HTTP Verb (GET, POST, PUT, DELETE)
 * @param {Object} [data=null] - Request payload data to pass in body
 * @returns {Promise<Object>} JSON response from server
 */
export async function fetchData(endpoint, method = 'GET', data = null) {
  const url = `${BASE_URL}${endpoint.startsWith('/') ? endpoint : '/' + endpoint}`;
  
  const options = {
    method: method.toUpperCase(),
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    },
    // Required to send cookies/session tokens across port origins (5173 -> 8080)
    credentials: 'include'
  };

  if (data && options.method !== 'GET') {
    options.body = JSON.stringify(data);
  }

  try {
    const response = await fetch(url, options);
    
    if (!response.ok) {
      const errorText = await response.text();
      let errorJSON;
      try {
        errorJSON = JSON.parse(errorText);
      } catch (e) {
        errorJSON = { message: errorText || `HTTP error! status: ${response.status}` };
      }
      throw new Error(errorJSON.message || errorJSON.error || 'Server error occurred');
    }

    return await response.json();
  } catch (error) {
    console.error(`API Fetch Error [${method} ${endpoint}]:`, error);
    throw error;
  }
}

/**
 * Sends a POST request to authenticate the user.
 */
export async function loginUser(email, password) {
  return await fetchData('/login', 'POST', { email, password });
}

/**
 * Sends a POST request to register a new user in the database.
 */
export async function registerUser(name, email, password, department) {
  return await fetchData('/register', 'POST', { name, email, password, department });
}