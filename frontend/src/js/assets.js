import { fetchData } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
  // DOM Elements
  const tableBody = document.getElementById('assets-table-body');
  const searchInput = document.getElementById('asset-search');
  const filterCategory = document.getElementById('filter-category');
  const filterStatus = document.getElementById('filter-status');
  
  const registerModal = document.getElementById('register-modal');
  const registerForm = document.getElementById('register-asset-form');
  const openModalBtn = document.getElementById('register-asset-btn');
  const cancelModalBtn = document.getElementById('cancel-modal-btn');
  const cancelModalBtn2 = document.getElementById('cancel-modal-btn-2');
  
  const entriesInfo = document.getElementById('entries-info');
  const paginationContainer = document.getElementById('pagination-container');

  // State
  let assets = [];
  let categories = [];
  let currentPage = 1;
  const itemsPerPage = 8;

  // 1. Fetch Init Data (Categories & Assets)
  async function init() {
    try {
      // Fetch Master Categories to populate dropdown lists dynamically
      categories = await fetchData('/categories', 'GET');
      populateCategoryDropdowns();
      
      // Fetch Assets Directory
      await loadAssets();
    } catch (err) {
      console.error('Error initializing asset directory:', err);
    }
  }

  async function loadAssets() {
    try {
      assets = await fetchData('/assets', 'GET');
      renderDirectory();
    } catch (err) {
      console.error('Error fetching assets:', err);
    }
  }

  // 2. Populate category filters and register selectors
  function populateCategoryDropdowns() {
    // Populate directory filter dropdown
    if (filterCategory) {
      filterCategory.innerHTML = '<option value="">All Categories</option>';
      categories.forEach(cat => {
        filterCategory.innerHTML += `<option value="${cat.id}">${escapeHtml(cat.name)}</option>`;
      });
    }

    // Populate modal register dropdown
    const modalCategory = document.getElementById('new-asset-category');
    if (modalCategory) {
      modalCategory.innerHTML = '';
      categories.forEach(cat => {
        modalCategory.innerHTML += `<option value="${cat.id}">${escapeHtml(cat.name)}</option>`;
      });
    }
  }

  // 3. Render Directory with Filters and Pagination
  function renderDirectory() {
    if (!tableBody) return;
    tableBody.innerHTML = '';

    // Apply Filter & Search Rules
    const query = searchInput ? searchInput.value.trim().toLowerCase() : '';
    const selectedCat = filterCategory ? filterCategory.value : '';
    const selectedStatus = filterStatus ? filterStatus.value.toLowerCase() : '';

    const filtered = assets.filter(asset => {
      // Search matches Tag, Name, Location or Serial No
      const matchesSearch = !query || 
        asset.tag.toLowerCase().includes(query) ||
        asset.name.toLowerCase().includes(query) ||
        asset.serial_number.toLowerCase().includes(query) ||
        asset.expected_location.toLowerCase().includes(query);

      // Category filter match
      const matchesCategory = !selectedCat || asset.category_id === parseInt(selectedCat);

      // Status filter match
      const matchesStatus = !selectedStatus || 
        (selectedStatus === 'active' && asset.status.toLowerCase() === 'allocated') ||
        (selectedStatus === 'active' && asset.status.toLowerCase() === 'available') ||
        asset.status.toLowerCase() === selectedStatus;

      return matchesSearch && matchesCategory && matchesStatus;
    });

    // Pagination calculations
    const totalItems = filtered.length;
    const totalPages = Math.ceil(totalItems / itemsPerPage) || 1;
    if (currentPage > totalPages) currentPage = totalPages;

    const startIndex = (currentPage - 1) * itemsPerPage;
    const endIndex = Math.min(startIndex + itemsPerPage, totalItems);
    const paginated = filtered.slice(startIndex, endIndex);

    // Update entries info text
    if (entriesInfo) {
      if (totalItems === 0) {
        entriesInfo.textContent = 'Showing 0 entries';
      } else {
        entriesInfo.textContent = `Showing ${startIndex + 1} to ${endIndex} of ${totalItems} entries`;
      }
    }

    // Render rows
    if (paginated.length === 0) {
      tableBody.innerHTML = `<tr><td colspan="6" class="text-center text-muted">No assets found.</td></tr>`;
      renderPagination(totalPages);
      return;
    }

    paginated.forEach(asset => {
      // Resolve colored status chip classes
      let statusClass = 'info';
      if (asset.status.toLowerCase() === 'available') statusClass = 'success';
      if (asset.status.toLowerCase() === 'maintenance') statusClass = 'warning';
      if (asset.status.toLowerCase() === 'retired') statusClass = 'danger';

      const tr = document.createElement('tr');
      tr.className = 'table-row-glass';
      tr.innerHTML = `
        <td class="font-mono text-sm font-bold text-primary-color">${asset.tag}</td>
        <td>
          <div class="font-semibold text-primary-color">${escapeHtml(asset.name)}</div>
          <div class="text-xs text-secondary font-mono">S/N: ${escapeHtml(asset.serial_number)}</div>
        </td>
        <td>${escapeHtml(asset.category_name)}</td>
        <td>
          <span class="status-chip ${statusClass}">
            <span class="dot"></span> ${asset.status}
          </span>
        </td>
        <td class="text-secondary">
          <div>${escapeHtml(asset.expected_location)}</div>
          <div class="text-xs text-muted">User: ${escapeHtml(asset.allocated_employee_name)}</div>
        </td>
        <td style="text-align: right;">
          <button class="btn btn-secondary btn-sm btn-edit-asset" data-id="${asset.id}" style="padding:4px 8px; font-size:11px;">Edit</button>
        </td>
      `;
      tableBody.appendChild(tr);
    });

    renderPagination(totalPages);
  }

  // 4. Render Pagination Buttons
  function renderPagination(totalPages) {
    if (!paginationContainer) return;
    paginationContainer.innerHTML = '';

    // Previous Page Button
    const prevBtn = document.createElement('button');
    prevBtn.className = `btn btn-secondary btn-sm ${currentPage === 1 ? 'disabled' : ''}`;
    prevBtn.innerHTML = '<span class="material-symbols-outlined" style="font-size: 16px;">chevron_left</span>';
    if (currentPage > 1) {
      prevBtn.addEventListener('click', () => {
        currentPage--;
        renderDirectory();
      });
    }
    paginationContainer.appendChild(prevBtn);

    // Page Number Buttons
    for (let i = 1; i <= totalPages; i++) {
      const pageBtn = document.createElement('button');
      pageBtn.className = `btn ${currentPage === i ? 'btn-primary' : 'btn-secondary'} btn-sm`;
      pageBtn.textContent = i;
      pageBtn.style.padding = '4px 10px';
      pageBtn.addEventListener('click', () => {
        currentPage = i;
        renderDirectory();
      });
      paginationContainer.appendChild(pageBtn);
    }

    // Next Page Button
    const nextBtn = document.createElement('button');
    nextBtn.className = `btn btn-secondary btn-sm ${currentPage === totalPages ? 'disabled' : ''}`;
    nextBtn.innerHTML = '<span class="material-symbols-outlined" style="font-size: 16px;">chevron_right</span>';
    if (currentPage < totalPages) {
      nextBtn.addEventListener('click', () => {
        currentPage++;
        renderDirectory();
      });
    }
    paginationContainer.appendChild(nextBtn);
  }

  // 5. Filter/Search Event Listeners
  if (searchInput) searchInput.addEventListener('input', () => { currentPage = 1; renderDirectory(); });
  if (filterCategory) filterCategory.addEventListener('change', () => { currentPage = 1; renderDirectory(); });
  if (filterStatus) filterStatus.addEventListener('change', () => { currentPage = 1; renderDirectory(); });

  // 6. Modal triggers
  if (openModalBtn) {
    openModalBtn.addEventListener('click', () => {
      registerForm.reset();
      // Auto-tag placeholder is handled on backend insert, display auto-gen notice
      const tagInput = document.getElementById('new-asset-id');
      if (tagInput) tagInput.value = 'Auto-Generated (e.g. AF-0005)';
      registerModal.classList.add('open');
    });
  }

  function closeModal() {
    registerModal.classList.remove('open');
    registerForm.reset();
  }

  if (cancelModalBtn) cancelModalBtn.addEventListener('click', closeModal);
  if (cancelModalBtn2) cancelModalBtn2.addEventListener('click', closeModal);

  // 7. Save New Asset Submit handler
  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      const name = document.getElementById('new-asset-name').value.trim();
      const categoryId = document.getElementById('new-asset-category').value;
      const status = document.getElementById('new-asset-status').value;
      const location = document.getElementById('new-asset-location').value.trim();
      const serial = document.getElementById('new-asset-serial').value.trim();

      if (!name || !location || !serial) {
        alert('Please fill out all registration fields.');
        return;
      }

      const submitBtn = registerForm.querySelector('button[type="submit"]');
      const originalHTML = submitBtn.innerHTML;
      submitBtn.disabled = true;
      submitBtn.innerHTML = `<span class="material-symbols-outlined rotating" style="font-size: 16px;">sync</span> Registering...`;

      try {
        const res = await fetchData('/assets', 'POST', {
          name,
          category_id: categoryId ? parseInt(categoryId) : null,
          status,
          expected_location: location,
          serial_number: serial
        });

        if (res.success) {
          alert(`Asset registered successfully! Assigned Tag: ${res.tag}`);
          closeModal();
          await loadAssets(); // Reload grid
        } else {
          alert(res.message || 'Failed to register asset.');
          submitBtn.disabled = false;
          submitBtn.innerHTML = originalHTML;
        }
      } catch (error) {
        alert('Error communicating with backend to register asset.');
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalHTML;
      }
    });
  }

  // HTML escaping utility
  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;')
              .replace(/'/g, '&#039;');
  }

  // Run initializer
  await init();
});
