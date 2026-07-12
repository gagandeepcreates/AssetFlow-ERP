import { fetchData } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
  // Action form elements
  const actionForm = document.getElementById('allocation-action-form');
  const assetSelect = document.getElementById('asset-select');
  const conflictAlert = document.getElementById('conflict-alert');
  const conflictAssigneeText = document.getElementById('conflict-assignee-text');
  
  // Section A: Direct Allocate
  const sectionAllocate = document.getElementById('section-direct-allocate');
  const allocateEmployee = document.getElementById('allocate-employee');
  const allocateReturnDate = document.getElementById('allocate-return-date');
  
  // Section B: Transfer Request
  const sectionTransfer = document.getElementById('section-transfer-request');
  const transferFromEmp = document.getElementById('transfer-from-emp');
  const transferToEmp = document.getElementById('transfer-to-emp');
  const transferReason = document.getElementById('transfer-reason');

  // List containers
  const pendingTransfersBody = document.getElementById('pending-transfers-body');
  const activeAllocationsBody = document.getElementById('active-allocations-body');

  // Modal elements (Return)
  const returnModal = document.getElementById('return-modal');
  const returnForm = document.getElementById('return-asset-form');
  const returnAllocationId = document.getElementById('return-allocation-id');
  const returnAssetTag = document.getElementById('return-asset-tag');
  const returnCondition = document.getElementById('return-condition');
  const returnCloseBtn = document.getElementById('return-modal-close');
  const returnCancelBtn = document.getElementById('return-modal-cancel');

  // State
  let assets = [];
  let employees = [];
  let allocations = [];
  let transfers = [];

  // 1. Initializer
  async function init() {
    try {
      // Set default return date to 30 days from today
      const thirtyDays = 30 * 24 * 60 * 60 * 1000;
      const defaultDate = new Date(Date.now() + thirtyDays).toISOString().split('T')[0];
      if (allocateReturnDate) allocateReturnDate.value = defaultDate;

      await loadInitialData();
    } catch (e) {
      console.error('Error initializing allocation page:', e);
    }
  }

  async function loadInitialData() {
    try {
      // Fetch Assets and Employees directories
      assets = await fetchData('/assets', 'GET');
      employees = await fetchData('/employees', 'GET');
      
      // Fetch Allocations state (history, transfers, overdue)
      const data = await fetchData('/allocations', 'GET');
      allocations = data.allocations || [];
      transfers = data.transfers || [];

      populateDropdowns();
      renderTransfers();
      renderActiveAllocations();
      handleAssetSelection(); // Refresh form state based on current select value
    } catch (e) {
      console.error('Error fetching baseline directories:', e);
    }
  }

  // 2. Populate employee and asset dropdowns
  function populateDropdowns() {
    // Populate Assets select
    if (assetSelect) {
      assetSelect.innerHTML = '<option value="">Choose Asset...</option>';
      assets.forEach(asset => {
        // Only list assets that are not retired
        if (asset.status.toLowerCase() !== 'retired') {
          const allocationText = asset.allocated_to ? `(Assigned: ${asset.allocated_employee_name})` : '(Available)';
          assetSelect.innerHTML += `<option value="${asset.id}">${asset.tag} - ${escapeHtml(asset.name)} ${allocationText}</option>`;
        }
      });
    }

    // Populate direct assign employee select
    if (allocateEmployee) {
      allocateEmployee.innerHTML = '<option value="">Select Assignee...</option>';
      employees.forEach(emp => {
        allocateEmployee.innerHTML += `<option value="${emp.id}">${escapeHtml(emp.name)} (${escapeHtml(emp.department_name)})</option>`;
      });
    }

    // Populate transfer employee select
    if (transferToEmp) {
      transferToEmp.innerHTML = '<option value="">Select Target Assignee...</option>';
      employees.forEach(emp => {
        transferToEmp.innerHTML += `<option value="${emp.id}">${escapeHtml(emp.name)} (${escapeHtml(emp.department_name)})</option>`;
      });
    }
  }

  // 3. Conflict Rule: Handle dynamic form swapping
  if (assetSelect) {
    assetSelect.addEventListener('change', handleAssetSelection);
  }

  function handleAssetSelection() {
    const selectedId = assetSelect.value;
    if (!selectedId) {
      hideAllForms();
      return;
    }

    const asset = assets.find(a => a.id === parseInt(selectedId));
    if (!asset) {
      hideAllForms();
      return;
    }

    // Check conflict: If asset has an assignee (allocated_to is not null)
    if (asset.allocated_to) {
      // Show conflict warning alert
      conflictAssigneeText.innerHTML = `<strong>Already Allocated to ${escapeHtml(asset.allocated_employee_name)}</strong>`;
      conflictAlert.style.display = 'flex';

      // Hide direct allocate form, show transfer request form
      sectionAllocate.classList.remove('active');
      sectionTransfer.classList.add('active');

      // Autofill 'From' text input field
      transferFromEmp.value = asset.allocated_employee_name;
      transferFromEmp.dataset.fromId = asset.allocated_to;
    } else {
      // Available asset: Hide conflict warning alert
      conflictAlert.style.display = 'none';

      // Show direct allocate form, hide transfer request form
      sectionAllocate.classList.add('active');
      sectionTransfer.classList.remove('active');
    }
  }

  function hideAllForms() {
    conflictAlert.style.display = 'none';
    sectionAllocate.classList.remove('active');
    sectionTransfer.classList.remove('active');
  }

  // 4. Render active allocations and pending transfers list
  function renderTransfers() {
    if (!pendingTransfersBody) return;
    pendingTransfersBody.innerHTML = '';

    const pending = transfers.filter(t => t.status.toLowerCase() === 'pending');

    if (pending.length === 0) {
      pendingTransfersBody.innerHTML = `<tr><td colspan="3" class="text-center text-muted">No pending transfer requests.</td></tr>`;
      return;
    }

    pending.forEach(t => {
      const tr = document.createElement('tr');
      tr.className = 'table-row-glass';
      tr.innerHTML = `
        <td>
          <div class="font-mono text-sm font-bold">${t.asset_tag}</div>
          <div class="text-xs text-secondary">${escapeHtml(t.asset_name)}</div>
        </td>
        <td>
          <div class="text-xs text-secondary">From: <strong style="color:var(--text-primary);">${escapeHtml(t.from_employee_name)}</strong></div>
          <div class="text-xs text-secondary">To: <strong style="color:var(--primary);">${escapeHtml(t.to_employee_name)}</strong></div>
          <div class="text-xs text-muted" style="margin-top:2px;">Reason: <em>"${escapeHtml(t.reason)}"</em></div>
        </td>
        <td style="text-align: right;">
          <button class="btn btn-primary btn-sm btn-approve" data-id="${t.id}" style="padding: 4px 10px; font-size:12px; background:var(--secondary); border-color:rgba(16,185,129,0.2);">Approve</button>
        </td>
      `;
      pendingTransfersBody.appendChild(tr);
    });

    pendingTransfersBody.querySelectorAll('.btn-approve').forEach(btn => {
      btn.addEventListener('click', async () => {
        const id = parseInt(btn.getAttribute('data-id'));
        btn.disabled = true;
        btn.textContent = 'Processing...';
        
        try {
          const res = await fetchData('/allocations', 'POST', {
            action: 'approve_transfer',
            transfer_id: id
          });

          if (res.success) {
            alert('Transfer approved successfully. Asset relocated.');
            await loadInitialData(); // Refresh UI
          } else {
            alert(res.message || 'Approval failed.');
            btn.disabled = false;
            btn.textContent = 'Approve';
          }
        } catch (err) {
          alert('Error communicating with approval endpoint.');
          btn.disabled = false;
          btn.textContent = 'Approve';
        }
      });
    });
  }

  function renderActiveAllocations() {
    if (!activeAllocationsBody) return;
    activeAllocationsBody.innerHTML = '';

    // Active allocations are those with status = 'Approved' and actual_return_date = null
    const active = allocations.filter(a => a.status.toLowerCase() === 'approved' && !a.actual_return_date);

    if (active.length === 0) {
      activeAllocationsBody.innerHTML = `<tr><td colspan="5" class="text-center text-muted">No active assignments.</td></tr>`;
      return;
    }

    active.forEach(a => {
      // Check if overdue
      const isOverdue = a.expected_return_date && new Date(a.expected_return_date) < new Date(new Date().toDateString());
      const dateColor = isOverdue ? 'color: var(--error); font-weight: bold;' : 'color: var(--text-secondary);';
      const badgeHTML = isOverdue ? `<span class="status-chip danger" style="padding: 2px 6px; font-size:10px;"><span class="dot"></span> OVERDUE</span>` : '';

      const tr = document.createElement('tr');
      tr.className = 'table-row-glass';
      tr.innerHTML = `
        <td class="font-mono text-sm font-bold">${a.asset_tag}</td>
        <td class="font-semibold text-primary-color">${escapeHtml(a.asset_name)}</td>
        <td class="text-secondary">${escapeHtml(a.employee_name)}</td>
        <td style="${dateColor}">
          <div>${a.expected_return_date || '--'}</div>
          <div>${badgeHTML}</div>
        </td>
        <td style="text-align: right;">
          <button class="btn btn-secondary btn-sm btn-return" data-id="${a.id}" data-tag="${a.asset_tag}" style="padding: 4px 8px; font-size:11px;">
            <span class="material-symbols-outlined" style="font-size:14px; vertical-align:middle; margin-right:2px;">assignment_return</span> Mark Returned
          </button>
        </td>
      `;
      activeAllocationsBody.appendChild(tr);
    });

    activeAllocationsBody.querySelectorAll('.btn-return').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        const tag = btn.getAttribute('data-tag');
        
        returnAllocationId.value = id;
        returnAssetTag.value = tag;
        returnCondition.value = '';
        returnModal.classList.add('open');
      });
    });
  }

  // 5. Submit Allocate or Transfer action
  actionForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const selectedAssetId = assetSelect.value;
    if (!selectedAssetId) return;

    const asset = assets.find(a => a.id === parseInt(selectedAssetId));
    if (!asset) return;

    let payload = {};

    if (asset.allocated_to) {
      // Transfer Mode
      const targetEmp = transferToEmp.value;
      const reason = transferReason.value.trim();

      if (!targetEmp || !reason) {
        alert('Please select the target employee and specify a reason.');
        return;
      }

      payload = {
        action: 'transfer',
        asset_id: asset.id,
        from_employee_id: asset.allocated_to,
        to_employee_id: parseInt(targetEmp),
        reason: reason
      };
    } else {
      // Direct Allocate Mode
      const targetEmp = allocateEmployee.value;
      const returnDate = allocateReturnDate.value;

      if (!targetEmp || !returnDate) {
        alert('Please select an employee and specify the expected return date.');
        return;
      }

      payload = {
        action: 'allocate',
        asset_id: asset.id,
        employee_id: parseInt(targetEmp),
        expected_return_date: returnDate
      };
    }

    const submitBtn = actionForm.querySelector('button[type="submit"]');
    const originalHTML = submitBtn.innerHTML;
    submitBtn.disabled = true;
    submitBtn.innerHTML = `<span class="material-symbols-outlined rotating" style="font-size:16px;">sync</span> Processing...`;

    try {
      const res = await fetchData('/allocations', 'POST', payload);
      if (res.success) {
        alert(res.message);
        actionForm.reset();
        await loadInitialData();
      } else {
        alert(res.message || 'Operation failed.');
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalHTML;
      }
    } catch (err) {
      alert(err.message || 'CORS or Connection failure.');
      submitBtn.disabled = false;
      submitBtn.innerHTML = originalHTML;
    }
  });

  // 6. Return Modal Close handlers
  function closeReturnModal() {
    returnModal.classList.remove('open');
    returnForm.reset();
  }
  if (returnCloseBtn) returnCloseBtn.addEventListener('click', closeReturnModal);
  if (returnCancelBtn) returnCancelBtn.addEventListener('click', closeReturnModal);

  // 7. Submit Return Asset
  if (returnForm) {
    returnForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      const allocId = returnAllocationId.value;
      const notes = returnCondition.value.trim();

      if (!allocId || !notes) return;

      const submitBtn = returnForm.querySelector('button[type="submit"]');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Processing...';

      try {
        const res = await fetchData('/allocations', 'POST', {
          action: 'return',
          allocation_id: parseInt(allocId),
          condition_notes: notes
        });

        if (res.success) {
          alert('Asset return processed. Status reset to Available.');
          closeReturnModal();
          await loadInitialData();
        } else {
          alert(res.message || 'Return processing failed.');
          submitBtn.disabled = false;
          submitBtn.textContent = 'Process Return';
        }
      } catch (err) {
        alert('Failed to connect to allocations endpoint.');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Process Return';
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

  // Run initial loading
  await init();
});
