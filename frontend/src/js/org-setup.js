import { fetchData } from './api.js';

document.addEventListener('DOMContentLoaded', () => {
  // Navigation elements
  const tabs = document.querySelectorAll('.org-tab[data-tab]');
  const tabContents = document.querySelectorAll('.tab-content');
  const addNewBtn = document.getElementById('btn-add-item');
  
  // Modal elements
  const modal = document.getElementById('setup-modal');
  const modalTitle = document.getElementById('modal-title');
  const modalFields = document.getElementById('modal-fields-container');
  const closeBtn = document.getElementById('modal-close-btn');
  const cancelBtn = document.getElementById('modal-cancel-btn');
  const setupForm = document.getElementById('setup-form');
  
  // State
  let activeTab = 'departments';
  let departments = [];
  let categories = [];
  let employees = [];
  let editingId = null;

  // Retrieve current user role for Admin checks
  const currentUserRole = localStorage.getItem('assetflow_user_role') || 'User';
  const isAdmin = currentUserRole.toLowerCase() === 'admin';

  // 1. Tab Navigation Toggles
  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      
      activeTab = tab.getAttribute('data-tab');
      tabContents.forEach(content => {
        if (content.id === `tab-${activeTab}`) {
          content.classList.add('active');
        } else {
          content.classList.remove('active');
        }
      });
      
      // Control Add New button visibility (employees are added via sign-up)
      if (activeTab === 'employees') {
        addNewBtn.style.display = 'none';
      } else {
        addNewBtn.style.display = 'flex';
      }

      loadActiveTabData();
    });
  });

  // 2. Fetch Data functions
  async function loadActiveTabData() {
    try {
      if (activeTab === 'departments') {
        departments = await fetchData('/departments', 'GET');
        renderDepartments();
      } else if (activeTab === 'categories') {
        categories = await fetchData('/categories', 'GET');
        renderCategories();
      } else if (activeTab === 'employees') {
        employees = await fetchData('/employees', 'GET');
        // Fetch departments as well to populate employee departments in future if needed
        departments = await fetchData('/departments', 'GET');
        renderEmployees();
      }
    } catch (err) {
      console.error(`Error loading data for ${activeTab}:`, err);
    }
  }

  // 3. Renderers
  function renderDepartments() {
    const listBody = document.getElementById('departments-list-body');
    listBody.innerHTML = '';
    
    if (departments.length === 0) {
      listBody.innerHTML = `<tr><td colspan="5" class="text-center text-muted">No departments found.</td></tr>`;
      return;
    }
    
    departments.forEach(dept => {
      const parent = departments.find(d => d.id === dept.parent_id);
      const parentName = parent ? parent.name : '--';
      const statusClass = dept.status.toLowerCase() === 'active' ? 'success' : 'danger';
      
      const tr = document.createElement('tr');
      tr.className = 'table-row-glass';
      tr.innerHTML = `
        <td class="font-mono text-sm">${dept.id}</td>
        <td class="font-semibold text-primary-color">${escapeHtml(dept.name)}</td>
        <td class="text-secondary">${escapeHtml(parentName)}</td>
        <td>
          <span class="status-chip ${statusClass}">
            <span class="dot"></span> ${dept.status}
          </span>
        </td>
        <td>
          <div class="org-actions-cell">
            <button class="btn btn-secondary btn-sm btn-edit" data-id="${dept.id}">
              <span class="material-symbols-outlined" style="font-size:16px;">edit</span> Edit
            </button>
            <button class="btn btn-secondary btn-sm btn-deactivate" data-id="${dept.id}" data-status="${dept.status}">
              <span class="material-symbols-outlined" style="font-size:16px;">${dept.status.toLowerCase() === 'active' ? 'block' : 'check_circle'}</span> 
              ${dept.status.toLowerCase() === 'active' ? 'Deactivate' : 'Activate'}
            </button>
          </div>
        </td>
      `;
      listBody.appendChild(tr);
    });

    // Attach Event Listeners to Edit and Deactivate Buttons
    listBody.querySelectorAll('.btn-edit').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = parseInt(btn.getAttribute('data-id'));
        openEditModal(id);
      });
    });

    listBody.querySelectorAll('.btn-deactivate').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = parseInt(btn.getAttribute('data-id'));
        const currentStatus = btn.getAttribute('data-status');
        const nextStatus = currentStatus.toLowerCase() === 'active' ? 'Inactive' : 'Active';
        
        const dept = departments.find(d => d.id === id);
        if (dept) {
          try {
            const res = await fetchData('/departments', 'POST', {
              id: dept.id,
              name: dept.name,
              parent_id: dept.parent_id,
              status: nextStatus
            });
            if (res.success) {
              loadActiveTabData();
            } else {
              alert(res.message);
            }
          } catch (e) {
            alert('Failed to update status.');
          }
        }
      });
    });
  }

  function renderCategories() {
    const listBody = document.getElementById('categories-list-body');
    listBody.innerHTML = '';
    
    if (categories.length === 0) {
      listBody.innerHTML = `<tr><td colspan="4" class="text-center text-muted">No categories found.</td></tr>`;
      return;
    }
    
    categories.forEach(cat => {
      const warranty = cat.warranty_period ? `${cat.warranty_period} M` : '--';
      
      const tr = document.createElement('tr');
      tr.className = 'table-row-glass';
      tr.innerHTML = `
        <td class="font-mono text-sm">${cat.id}</td>
        <td class="font-semibold text-primary-color">${escapeHtml(cat.name)}</td>
        <td class="font-mono">${warranty}</td>
        <td>
          <div class="org-actions-cell">
            <button class="btn btn-secondary btn-sm btn-edit-cat" data-id="${cat.id}">
              <span class="material-symbols-outlined" style="font-size:16px;">edit</span> Edit
            </button>
          </div>
        </td>
      `;
      listBody.appendChild(tr);
    });

    listBody.querySelectorAll('.btn-edit-cat').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = parseInt(btn.getAttribute('data-id'));
        openEditModal(id);
      });
    });
  }

  function renderEmployees() {
    const listBody = document.getElementById('employees-list-body');
    listBody.innerHTML = '';
    
    if (employees.length === 0) {
      listBody.innerHTML = `<tr><td colspan="5" class="text-center text-muted">No employees found.</td></tr>`;
      return;
    }
    
    employees.forEach(emp => {
      const tr = document.createElement('tr');
      tr.className = 'table-row-glass';
      
      let roleColumnHTML = `<span>${emp.role}</span>`;
      
      // If user is Admin, show dropdown picker to change roles (promoting/updating roles)
      if (isAdmin) {
        roleColumnHTML = `
          <select class="glass-select emp-role-select" data-id="${emp.id}" style="padding: 4px 10px; width: 150px; font-size:13px;">
            <option value="User" ${emp.role === 'User' ? 'selected' : ''}>User</option>
            <option value="Admin" ${emp.role === 'Admin' ? 'selected' : ''}>Admin</option>
            <option value="Department Head" ${emp.role === 'Department Head' ? 'selected' : ''}>Department Head</option>
            <option value="Asset Manager" ${emp.role === 'Asset Manager' ? 'selected' : ''}>Asset Manager</option>
          </select>
        `;
      }

      tr.innerHTML = `
        <td class="font-mono text-sm">${emp.id}</td>
        <td class="font-semibold text-primary-color">${escapeHtml(emp.name)}</td>
        <td class="text-secondary">${escapeHtml(emp.email)}</td>
        <td>${escapeHtml(emp.department_name)}</td>
        <td>${roleColumnHTML}</td>
      `;
      listBody.appendChild(tr);
    });

    // Bind dropdown change handlers for admins
    if (isAdmin) {
      listBody.querySelectorAll('.emp-role-select').forEach(select => {
        select.addEventListener('change', async () => {
          const id = parseInt(select.getAttribute('data-id'));
          const role = select.value;
          
          try {
            const res = await fetchData('/employees', 'POST', { id, role });
            if (res.success) {
              console.log(`Role updated successfully for employee ID ${id} to ${role}`);
            } else {
              alert(res.message);
              loadActiveTabData(); // Reset view
            }
          } catch (e) {
            alert('Failed to promote user role.');
            loadActiveTabData();
          }
        });
      });
    }
  }

  // 4. Modal actions
  addNewBtn.addEventListener('click', () => {
    editingId = null;
    modalTitle.textContent = `Create New ${activeTab === 'departments' ? 'Department' : 'Asset Category'}`;
    renderModalFields();
    modal.classList.add('open');
  });

  function openEditModal(id) {
    editingId = id;
    modalTitle.textContent = `Edit ${activeTab === 'departments' ? 'Department' : 'Asset Category'}`;
    renderModalFields();
    
    // Populate fields
    if (activeTab === 'departments') {
      const dept = departments.find(d => d.id === id);
      if (dept) {
        document.getElementById('field-name').value = dept.name;
        document.getElementById('field-parent').value = dept.parent_id || '';
        document.getElementById('field-status').value = dept.status;
      }
    } else if (activeTab === 'categories') {
      const cat = categories.find(c => c.id === id);
      if (cat) {
        document.getElementById('field-name').value = cat.name;
        document.getElementById('field-warranty').value = cat.warranty_period || '';
      }
    }
    
    modal.classList.add('open');
  }

  function renderModalFields() {
    modalFields.innerHTML = '';
    
    if (activeTab === 'departments') {
      // Build options for parent department (exclude editingId to prevent loop)
      let parentOptions = `<option value="">None (Top Level)</option>`;
      departments.forEach(d => {
        if (editingId === null || d.id !== editingId) {
          parentOptions += `<option value="${d.id}">${escapeHtml(d.name)}</option>`;
        }
      });

      modalFields.innerHTML = `
        <div class="form-group" style="margin-bottom: var(--spacing-md);">
          <label class="form-label" for="field-name">Department Name</label>
          <input type="text" class="glass-input" id="field-name" placeholder="e.g. Engineering" required>
        </div>
        <div class="form-group" style="margin-bottom: var(--spacing-md);">
          <label class="form-label" for="field-parent">Parent Department</label>
          <select class="glass-select" id="field-parent">${parentOptions}</select>
        </div>
        <div class="form-group">
          <label class="form-label" for="field-status">Status</label>
          <select class="glass-select" id="field-status">
            <option value="Active">Active</option>
            <option value="Inactive">Inactive</option>
          </select>
        </div>
      `;
    } else if (activeTab === 'categories') {
      modalFields.innerHTML = `
        <div class="form-group" style="margin-bottom: var(--spacing-md);">
          <label class="form-label" for="field-name">Category Name</label>
          <input type="text" class="glass-input" id="field-name" placeholder="e.g. Laptops" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="field-warranty">Warranty Period (Months)</label>
          <input type="number" class="glass-input" id="field-warranty" placeholder="e.g. 24" min="0">
        </div>
      `;
    }
  }

  function closeModal() {
    modal.classList.remove('open');
    setupForm.reset();
  }

  closeBtn.addEventListener('click', closeModal);
  cancelBtn.addEventListener('click', closeModal);

  // 5. Submit Form (Save Changes)
  setupForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    
    let payload = {};
    const name = document.getElementById('field-name').value.trim();
    
    if (activeTab === 'departments') {
      const parentVal = document.getElementById('field-parent').value;
      const status = document.getElementById('field-status').value;
      
      payload = {
        name,
        parent_id: parentVal ? parseInt(parentVal) : null,
        status
      };
    } else if (activeTab === 'categories') {
      const warrantyVal = document.getElementById('field-warranty').value;
      
      payload = {
        name,
        warranty_period: warrantyVal ? parseInt(warrantyVal) : null
      };
    }

    if (editingId !== null) {
      payload.id = editingId;
    }

    try {
      const endpoint = activeTab === 'departments' ? '/departments' : '/categories';
      const res = await fetchData(endpoint, 'POST', payload);
      
      if (res.success) {
        closeModal();
        loadActiveTabData();
      } else {
        alert(res.message || 'Operation failed.');
      }
    } catch (err) {
      alert(err.message || 'Error saving changes.');
    }
  });

  // Utility to prevent XSS injection
  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#039;');
  }

  // Load initial tab data (Departments)
  loadActiveTabData();
});
