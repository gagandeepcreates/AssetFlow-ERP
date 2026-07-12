/**
 * maintenance/kanban.js — Live Maintenance Kanban Controller
 * Reads from and writes to MaintenanceServlet (/maintenance) on Tomcat 9.
 *
 * Status lifecycle:
 *   Employee → raise  → Pending
 *   Manager  → approve            → Approved   (asset → Maintenance)
 *   Manager  → assign_technician  → In Progress
 *   Manager  → resolve            → Resolved   (asset → Available)
 */

import { fetchData } from './api.js';

const BASE = '/maintenance';

// ── Status → column mapping ──────────────────────────────────────
const STATUS_MAP = {
  'Pending':     'pending',
  'Approved':    'approved',
  'In Progress': 'inprogress',
  'Resolved':    'resolved'
};

// Priority colour class
const PRIORITY_CLASS = {
  NORMAL:   'priority-normal',
  HIGH:     'priority-high',
  CRITICAL: 'priority-critical'
};

// ── State ────────────────────────────────────────────────────────
let tickets = [];

// ── DOM references (populated in DOMContentLoaded) ───────────────
let colEls       = {};
let countEls     = {};
let searchInput  = null;

// ── Utility: get logged-in user from localStorage ────────────────
function getCurrentUser() {
  return {
    id:   parseInt(localStorage.getItem('userId') || '1', 10),
    name: localStorage.getItem('userName') || 'You',
    role: localStorage.getItem('userRole') || 'User'
  };
}

// ── Utility: is the user an Asset Manager or Admin? ──────────────
function isManager() {
  const role = getCurrentUser().role;
  return role === 'Admin' || role === 'Asset Manager' || role === 'Department Head';
}

// ════════════════════════════════════════════════════════════════
// LOAD — fetch all tickets from the backend
// ════════════════════════════════════════════════════════════════
async function loadTickets() {
  try {
    const data = await fetchData(BASE, 'GET');
    if (data.success) {
      tickets = data.tickets || [];
      renderBoard();
    } else {
      showToast('Failed to load tickets: ' + (data.message || 'Unknown error'), 'error');
    }
  } catch (err) {
    console.error('Kanban load error:', err);
    showToast('Could not connect to server. Showing cached data.', 'error');
  }
}

// ════════════════════════════════════════════════════════════════
// RENDER — paint all tickets into their columns
// ════════════════════════════════════════════════════════════════
function renderBoard() {
  // Clear columns
  Object.values(colEls).forEach(el => { if (el) el.innerHTML = ''; });

  const counts = { pending: 0, approved: 0, inprogress: 0, resolved: 0 };
  const query  = searchInput ? searchInput.value.toLowerCase().trim() : '';

  tickets.forEach(ticket => {
    const colKey = STATUS_MAP[ticket.status] || 'pending';
    const colEl  = colEls[colKey];
    if (!colEl) return;

    // Search filter
    const matchSearch = !query ||
      (ticket.issue_title  || '').toLowerCase().includes(query) ||
      (ticket.asset_tag    || '').toLowerCase().includes(query) ||
      (ticket.description  || '').toLowerCase().includes(query) ||
      (ticket.reporter_name|| '').toLowerCase().includes(query);
    if (!matchSearch) return;

    counts[colKey]++;
    colEl.appendChild(buildCard(ticket));
  });

  // Update count badges
  Object.keys(countEls).forEach(k => {
    if (countEls[k]) countEls[k].textContent = counts[k];
  });
}

// ════════════════════════════════════════════════════════════════
// BUILD CARD — create DOM element for a single ticket
// ════════════════════════════════════════════════════════════════
function buildCard(ticket) {
  const card = document.createElement('div');
  card.className = 'kanban-card';
  if (ticket.status === 'In Progress') card.classList.add('in-progress-active');
  if (ticket.status === 'Resolved')    card.classList.add('resolved-done');

  const prioClass = PRIORITY_CLASS[ticket.priority] || 'priority-normal';

  // Badge HTML
  let badgeHTML;
  if (ticket.status === 'Resolved') {
    badgeHTML = `<span class="kanban-card-priority done-badge">
                   <span class="material-symbols-outlined" style="font-size:12px;">check</span>DONE
                 </span>`;
  } else {
    badgeHTML = `<span class="kanban-card-priority ${prioClass}">${ticket.priority}</span>`;
  }

  // Action button (role-gated)
  let actionBtn = '';
  if (isManager()) {
    if (ticket.status === 'Pending') {
      actionBtn = `<button class="kanban-action-btn btn-approve"
                     data-id="${ticket.id}" data-action="approve">
                     <span class="material-symbols-outlined" style="font-size:13px;">thumb_up</span>
                     Approve
                   </button>`;
    } else if (ticket.status === 'Approved') {
      actionBtn = `<button class="kanban-action-btn btn-assign"
                     data-id="${ticket.id}" data-action="assign_technician">
                     <span class="material-symbols-outlined" style="font-size:13px;">engineering</span>
                     Assign Tech
                   </button>`;
    } else if (ticket.status === 'In Progress') {
      actionBtn = `<button class="kanban-action-btn btn-resolve"
                     data-id="${ticket.id}" data-action="resolve">
                     <span class="material-symbols-outlined" style="font-size:13px;">task_alt</span>
                     Mark Resolved
                   </button>`;
    }
  }

  // Technician line (only shown when assigned)
  const techLine = ticket.technician_name
    ? `<div class="kanban-card-tech">
         <span class="material-symbols-outlined" style="font-size:13px;vertical-align:middle;">engineering</span>
         ${ticket.technician_name}
       </div>`
    : '';

  card.innerHTML = `
    <div class="kanban-card-top">
      <span class="kanban-card-id">#MR-${String(ticket.id).padStart(4,'0')}</span>
      ${badgeHTML}
    </div>
    <h3 class="kanban-card-title">${ticket.issue_title || 'Untitled'}</h3>
    <p class="kanban-card-desc">${ticket.description || ''}</p>
    ${techLine}
    <div class="kanban-card-footer">
      <span class="kanban-card-tag">${ticket.asset_tag || ''}</span>
      <div style="display:flex;align-items:center;gap:6px;">
        <span class="kanban-card-reporter">${ticket.reporter_name || 'Unknown'}</span>
      </div>
    </div>
    ${actionBtn ? `<div class="kanban-card-actions">${actionBtn}</div>` : ''}
  `;

  // Wire action button
  const btn = card.querySelector('.kanban-action-btn');
  if (btn) {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const action   = btn.dataset.action;
      const ticketId = parseInt(btn.dataset.id, 10);
      handleAction(action, ticketId);
    });
  }

  return card;
}

// ════════════════════════════════════════════════════════════════
// HANDLE ACTION — route to the appropriate backend call
// ════════════════════════════════════════════════════════════════
async function handleAction(action, ticketId) {
  try {
    if (action === 'approve') {
      await postAction({ action: 'approve', ticket_id: ticketId });
      showToast(`Ticket #MR-${String(ticketId).padStart(4,'0')} approved — asset placed Under Maintenance.`, 'success');

    } else if (action === 'assign_technician') {
      openAssignModal(ticketId);
      return; // modal handles the rest

    } else if (action === 'resolve') {
      openResolveModal(ticketId);
      return; // modal handles the rest
    }
    // Reload board after action
    await loadTickets();
  } catch (err) {
    showToast(err.message || 'Action failed.', 'error');
  }
}

// ── Assign Technician modal ──────────────────────────────────────
function openAssignModal(ticketId) {
  const modal   = document.getElementById('assign-modal');
  const form    = document.getElementById('assign-form');
  const input   = document.getElementById('assign-tech-name');
  const heading = document.getElementById('assign-modal-heading');

  if (!modal) return;
  if (heading) heading.textContent = `Assign Technician — #MR-${String(ticketId).padStart(4,'0')}`;
  if (input)   input.value = '';

  modal.classList.add('open');

  // Remove old submit listener to avoid duplicates
  const clone = form.cloneNode(true);
  form.parentNode.replaceChild(clone, form);

  clone.addEventListener('submit', async (e) => {
    e.preventDefault();
    const techName = document.getElementById('assign-tech-name').value.trim();
    if (!techName) { showToast('Technician name is required.', 'error'); return; }

    try {
      await postAction({ action: 'assign_technician', ticket_id: ticketId, technician_name: techName });
      modal.classList.remove('open');
      showToast(`Technician '${techName}' assigned. Ticket now In Progress.`, 'success');
      await loadTickets();
    } catch (err) {
      showToast(err.message || 'Failed to assign technician.', 'error');
    }
  });
}

// ── Resolve modal ────────────────────────────────────────────────
function openResolveModal(ticketId) {
  const modal   = document.getElementById('resolve-modal');
  const form    = document.getElementById('resolve-form');
  const input   = document.getElementById('resolve-notes');
  const heading = document.getElementById('resolve-modal-heading');

  if (!modal) return;
  if (heading) heading.textContent = `Resolve Ticket — #MR-${String(ticketId).padStart(4,'0')}`;
  if (input)   input.value = '';

  modal.classList.add('open');

  const clone = form.cloneNode(true);
  form.parentNode.replaceChild(clone, form);

  clone.addEventListener('submit', async (e) => {
    e.preventDefault();
    const notes = document.getElementById('resolve-notes').value.trim();
    if (!notes) { showToast('Resolution notes are required.', 'error'); return; }

    try {
      await postAction({ action: 'resolve', ticket_id: ticketId, resolution_notes: notes });
      modal.classList.remove('open');
      showToast('Ticket resolved! Asset is now Available.', 'success');
      await loadTickets();
    } catch (err) {
      showToast(err.message || 'Failed to resolve ticket.', 'error');
    }
  });
}

// ── Generic POST ─────────────────────────────────────────────────
async function postAction(payload) {
  const data = await fetchData(BASE, 'POST', payload);
  if (!data.success) throw new Error(data.message || 'Server error.');
  return data;
}

// ════════════════════════════════════════════════════════════════
// NEW TICKET FORM — employee raises a request
// ════════════════════════════════════════════════════════════════
function setupNewTicketForm() {
  const btn     = document.getElementById('new-ticket-btn');
  const modal   = document.getElementById('ticket-modal');
  const form    = document.getElementById('new-ticket-form');
  const cancelA = document.getElementById('cancel-ticket-btn');
  const cancelB = document.getElementById('cancel-ticket-btn-2');

  const closeModal = () => { modal?.classList.remove('open'); form?.reset(); };

  btn?.addEventListener('click', () => { modal?.classList.add('open'); });
  cancelA?.addEventListener('click', closeModal);
  cancelB?.addEventListener('click', closeModal);

  form?.addEventListener('submit', async (e) => {
    e.preventDefault();

    const assetTagRaw = (document.getElementById('new-ticket-tag')?.value || '').trim().toUpperCase();
    const issueTitle  = (document.getElementById('new-ticket-title')?.value || '').trim();
    const priority    = (document.getElementById('new-ticket-priority')?.value || 'NORMAL');
    const description = (document.getElementById('new-ticket-desc')?.value || '').trim();
    const user        = getCurrentUser();

    if (!assetTagRaw || !issueTitle || !description) {
      showToast('Please fill out all required fields.', 'error');
      return;
    }

    // Resolve asset tag → asset_id
    let assetId;
    try {
      const assetsData = await fetchData('/assets?tag=' + encodeURIComponent(assetTagRaw), 'GET');
      // The assets endpoint returns an `assets` array; find the matching tag
      const matched = (assetsData.assets || []).find(
        a => (a.tag || '').toUpperCase() === assetTagRaw
      );
      if (!matched) {
        showToast(`Asset tag '${assetTagRaw}' not found in the system.`, 'error');
        return;
      }
      assetId = matched.id;
    } catch (err) {
      showToast('Could not verify asset tag: ' + err.message, 'error');
      return;
    }

    try {
      await postAction({
        action:      'raise',
        asset_id:    assetId,
        reporter_id: user.id,
        issue_title: issueTitle,
        description: description,
        priority:    priority
      });
      closeModal();
      showToast('Maintenance request raised! Status: Pending.', 'success');
      await loadTickets();
    } catch (err) {
      showToast(err.message || 'Failed to raise request.', 'error');
    }
  });
}

// ════════════════════════════════════════════════════════════════
// TOAST NOTIFICATION
// ════════════════════════════════════════════════════════════════
function showToast(message, type = 'success') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.style.cssText =
      'position:fixed;bottom:24px;right:24px;z-index:9999;display:flex;flex-direction:column;gap:10px;';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.style.cssText = `
    display:flex; align-items:center; gap:10px;
    padding:12px 18px; border-radius:10px; font-size:13px; font-weight:500;
    color:#fff; max-width:340px; box-shadow:0 8px 24px rgba(0,0,0,.35);
    background: ${type === 'error'
      ? 'linear-gradient(135deg,#ef4444,#dc2626)'
      : 'linear-gradient(135deg,#10b981,#059669)'};
    animation: slideInToast .25s ease;
  `;
  const icon = type === 'error' ? 'error' : 'check_circle';
  toast.innerHTML =
    `<span class="material-symbols-outlined" style="font-size:18px;">${icon}</span>
     <span>${message}</span>`;

  container.appendChild(toast);
  setTimeout(() => toast.remove(), 4000);
}

// ════════════════════════════════════════════════════════════════
// BOOT
// ════════════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {
  colEls = {
    pending:    document.getElementById('col-pending'),
    approved:   document.getElementById('col-approved'),
    inprogress: document.getElementById('col-inprogress'),
    resolved:   document.getElementById('col-resolved')
  };
  countEls = {
    pending:    document.getElementById('count-pending'),
    approved:   document.getElementById('count-approved'),
    inprogress: document.getElementById('count-inprogress'),
    resolved:   document.getElementById('count-resolved')
  };
  searchInput = document.getElementById('ticket-search');

  if (searchInput) searchInput.addEventListener('input', renderBoard);

  // Close-button wiring for extra modals
  document.getElementById('cancel-assign-btn')?.addEventListener('click',
    () => document.getElementById('assign-modal')?.classList.remove('open'));
  document.getElementById('cancel-resolve-btn')?.addEventListener('click',
    () => document.getElementById('resolve-modal')?.classList.remove('open'));

  setupNewTicketForm();
  loadTickets();
});
