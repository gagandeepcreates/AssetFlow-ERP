/**
 * audit.js — Audit Cycle Controller for AssetFlow ERP
 * Communicates with AuditServlet (/audit) on Tomcat 9.
 *
 * Views:
 *   - Cycle List      — all audit cycles (Open & Closed) with stats
 *   - Cycle Detail    — items in a cycle; auditor can mark Verified/Missing/Damaged
 *   - Report Panel    — parsed JSON report from a Closed cycle
 */

import { fetchData } from './api.js';

const BASE = '/audit';

// ── State ────────────────────────────────────────────────────────
let cycles       = [];
let activeCycleId = null;
let activeItems  = [];

// ── DOM refs ─────────────────────────────────────────────────────
let cycleListSection;
let cycleDetailSection;
let reportSection;

// ════════════════════════════════════════════════════════════════
// BOOT
// ════════════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {
  cycleListSection   = document.getElementById('cycle-list-section');
  cycleDetailSection = document.getElementById('cycle-detail-section');
  reportSection      = document.getElementById('report-section');

  // Wiring
  document.getElementById('new-cycle-btn')?.addEventListener('click',
    () => document.getElementById('new-cycle-modal')?.classList.add('open'));
  document.getElementById('cancel-cycle-btn')?.addEventListener('click', closeAllModals);
  document.getElementById('cancel-cycle-btn-2')?.addEventListener('click', closeAllModals);
  document.getElementById('new-cycle-form')?.addEventListener('submit', handleCreateCycle);

  document.getElementById('back-to-cycles-btn')?.addEventListener('click', showCycleList);
  document.getElementById('close-cycle-btn')?.addEventListener('click', handleCloseCycle);
  document.getElementById('back-from-report-btn')?.addEventListener('click', showCycleList);

  document.getElementById('audit-search')?.addEventListener('input', () => renderCycleList());

  loadCycles();
});

// ════════════════════════════════════════════════════════════════
// DATA LOADING
// ════════════════════════════════════════════════════════════════
async function loadCycles() {
  try {
    const data = await fetchData(`${BASE}?action=cycles`, 'GET');
    if (data.success) {
      cycles = data.cycles || [];
      renderCycleList();
    } else {
      showToast('Failed to load audit cycles: ' + (data.message || ''), 'error');
    }
  } catch (err) {
    console.error('Audit load error:', err);
    showToast('Cannot connect to server.', 'error');
  }
}

async function loadItems(cycleId) {
  try {
    const data = await fetchData(`${BASE}?action=items&cycle_id=${cycleId}`, 'GET');
    if (data.success) {
      activeItems = data.items || [];
      renderItemTable();
    } else {
      showToast('Failed to load items: ' + data.message, 'error');
    }
  } catch (err) {
    showToast('Cannot load items: ' + err.message, 'error');
  }
}

// ════════════════════════════════════════════════════════════════
// VIEWS — show/hide sections
// ════════════════════════════════════════════════════════════════
function showCycleList() {
  setVisible(cycleListSection, true);
  setVisible(cycleDetailSection, false);
  setVisible(reportSection, false);
  activeCycleId = null;
  loadCycles(); // refresh
}

function showCycleDetail(cycle) {
  activeCycleId = cycle.id;
  setVisible(cycleListSection, false);
  setVisible(cycleDetailSection, true);
  setVisible(reportSection, false);

  // Populate header
  document.getElementById('detail-cycle-title').textContent = cycle.title;
  document.getElementById('detail-auditor').textContent     = cycle.auditor_name || '—';
  document.getElementById('detail-scope').textContent       = cycle.scope_description || '—';
  document.getElementById('detail-status-badge').textContent = cycle.status;
  document.getElementById('detail-status-badge').className =
    'status-chip ' + (cycle.status === 'Open' ? 'success' : 'muted');

  // Show/hide close button for Open cycles only
  const closeBtn = document.getElementById('close-cycle-btn');
  if (closeBtn) closeBtn.style.display = cycle.status === 'Open' ? 'inline-flex' : 'none';

  loadItems(cycle.id);
}

function showReport(cycle) {
  setVisible(cycleListSection, false);
  setVisible(cycleDetailSection, false);
  setVisible(reportSection, true);

  let report;
  try { report = JSON.parse(cycle.report); } catch (e) { report = null; }

  if (!report) {
    document.getElementById('report-body').innerHTML =
      '<p class="text-muted">No report data available.</p>';
    return;
  }

  document.getElementById('report-cycle-title').textContent  = report.cycle_title || cycle.title;
  document.getElementById('report-closed-at').textContent    = report.closed_at || cycle.closed_at || '—';
  document.getElementById('report-total').textContent        = report.total_assets || 0;
  document.getElementById('report-verified').textContent     = report.verified     || 0;
  document.getElementById('report-missing').textContent      = report.missing      || 0;
  document.getElementById('report-damaged').textContent      = report.damaged      || 0;
  document.getElementById('report-accuracy').textContent     = (report.accuracy_pct || 0) + '%';

  const tbody = document.getElementById('report-items-body');
  if (!tbody) return;
  tbody.innerHTML = '';
  (report.items || []).forEach(item => {
    const statusClass = { Verified: 'success', Missing: 'danger', Damaged: 'warning' }[item.audit_status] || '';
    tbody.insertAdjacentHTML('beforeend', `
      <tr class="table-row-glass">
        <td class="font-mono">${item.tag}</td>
        <td>${item.name}</td>
        <td><span class="status-chip ${statusClass}">${item.audit_status}</span></td>
        <td class="text-muted" style="font-size:12px;">${item.notes || '—'}</td>
      </tr>
    `);
  });
}

function setVisible(el, visible) {
  if (el) el.style.display = visible ? '' : 'none';
}

// ════════════════════════════════════════════════════════════════
// RENDER — Cycle List
// ════════════════════════════════════════════════════════════════
function renderCycleList() {
  const container = document.getElementById('cycles-grid');
  if (!container) return;

  const query = (document.getElementById('audit-search')?.value || '').toLowerCase().trim();
  const filtered = cycles.filter(c =>
    !query ||
    c.title.toLowerCase().includes(query) ||
    (c.auditor_name || '').toLowerCase().includes(query) ||
    (c.scope_description || '').toLowerCase().includes(query)
  );

  if (filtered.length === 0) {
    container.innerHTML = `
      <div class="glass-card" style="text-align:center;padding:48px;grid-column:1/-1;">
        <span class="material-symbols-outlined" style="font-size:48px;color:var(--text-muted);">fact_check</span>
        <p class="text-muted" style="margin-top:12px;">No audit cycles found. Start a new one.</p>
      </div>`;
    return;
  }

  container.innerHTML = '';
  filtered.forEach(cycle => {
    const total    = cycle.total_items   || 0;
    const verified = cycle.verified_count || 0;
    const missing  = cycle.missing_count  || 0;
    const damaged  = cycle.damaged_count  || 0;
    const pending  = cycle.pending_count  || 0;
    const pct      = total > 0 ? Math.round((verified / total) * 100) : 0;
    const isOpen   = cycle.status === 'Open';

    const card = document.createElement('div');
    card.className = 'glass-card audit-cycle-card';
    card.innerHTML = `
      <div class="audit-cycle-header">
        <div>
          <div class="audit-cycle-title">${cycle.title}</div>
          <div class="audit-cycle-meta">${cycle.scope_description || '—'}</div>
        </div>
        <span class="status-chip ${isOpen ? 'success' : 'muted'}">${cycle.status}</span>
      </div>
      <div class="audit-cycle-auditor">
        <span class="material-symbols-outlined" style="font-size:14px;">person</span>
        ${cycle.auditor_name || 'Unknown'}
        <span style="margin-left:8px;color:var(--text-muted);font-size:11px;">
          ${cycle.created_at ? cycle.created_at.slice(0, 10) : ''}
        </span>
      </div>
      <div class="audit-stats-row">
        <div class="audit-stat verified"><span>${verified}</span><small>Verified</small></div>
        <div class="audit-stat missing"><span>${missing}</span><small>Missing</small></div>
        <div class="audit-stat damaged"><span>${damaged}</span><small>Damaged</small></div>
        <div class="audit-stat pending"><span>${pending}</span><small>Pending</small></div>
      </div>
      <div class="audit-progress-bar-wrap">
        <div class="audit-progress-bar" style="width:${pct}%"></div>
      </div>
      <div class="audit-progress-label">${pct}% verified (${total} assets)</div>
      <div class="audit-cycle-actions">
        ${isOpen
          ? `<button class="btn btn-primary audit-open-btn" style="font-size:12px;padding:7px 14px;">
               <span class="material-symbols-outlined" style="font-size:14px;">edit_note</span>
               Review Items
             </button>`
          : `<button class="btn btn-secondary audit-report-btn" style="font-size:12px;padding:7px 14px;border-color:rgba(255,255,255,.15);">
               <span class="material-symbols-outlined" style="font-size:14px;">summarize</span>
               View Report
             </button>`
        }
      </div>
    `;

    card.querySelector('.audit-open-btn')?.addEventListener('click', () => showCycleDetail(cycle));
    card.querySelector('.audit-report-btn')?.addEventListener('click', () => showReport(cycle));

    container.appendChild(card);
  });
}

// ════════════════════════════════════════════════════════════════
// RENDER — Item Table (inside detail view)
// ════════════════════════════════════════════════════════════════
function renderItemTable() {
  const tbody = document.getElementById('items-tbody');
  if (!tbody) return;

  tbody.innerHTML = '';
  activeItems.forEach(item => {
    const statusClass = {
      Verified: 'success',
      Missing:  'danger',
      Damaged:  'warning',
      Pending:  'muted'
    }[item.audit_status] || '';

    const cycle = cycles.find(c => c.id === activeCycleId);
    const isOpen = cycle ? cycle.status === 'Open' : false;

    const row = document.createElement('tr');
    row.className = 'table-row-glass';
    row.innerHTML = `
      <td class="font-mono">${item.asset_tag}</td>
      <td>${item.asset_name}</td>
      <td style="color:var(--text-muted);font-size:12px;">${item.expected_location || '—'}</td>
      <td>
        ${isOpen
          ? `<select class="audit-status-select glass-select" data-item-id="${item.id}" style="font-size:12px;padding:4px 8px;">
               <option value="Pending"  ${item.audit_status === 'Pending'  ? 'selected' : ''}>Pending</option>
               <option value="Verified" ${item.audit_status === 'Verified' ? 'selected' : ''}>Verified</option>
               <option value="Missing"  ${item.audit_status === 'Missing'  ? 'selected' : ''}>Missing</option>
               <option value="Damaged"  ${item.audit_status === 'Damaged'  ? 'selected' : ''}>Damaged</option>
             </select>`
          : `<span class="status-chip ${statusClass}">${item.audit_status}</span>`
        }
      </td>
      <td>
        ${isOpen
          ? `<input type="text" class="glass-input audit-notes-input" data-item-id="${item.id}"
                    value="${item.notes || ''}" placeholder="Add notes..."
                    style="font-size:12px;padding:4px 8px;min-width:180px;">`
          : `<span class="text-muted" style="font-size:12px;">${item.notes || '—'}</span>`
        }
      </td>
      <td style="font-size:11px;color:var(--text-muted);">${item.audited_at ? item.audited_at.slice(0,16) : '—'}</td>
      <td>
        ${isOpen
          ? `<button class="btn-save-item btn" data-item-id="${item.id}"
                     style="font-size:11px;padding:5px 12px;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.15);">
               Save
             </button>`
          : ''
        }
      </td>
    `;

    // Wire Save button
    row.querySelector('.btn-save-item')?.addEventListener('click', async () => {
      const select = row.querySelector('.audit-status-select');
      const notes  = row.querySelector('.audit-notes-input');
      if (!select) return;

      await saveItem(item.id, select.value, notes?.value || '');
    });

    tbody.appendChild(row);
  });

  updatePendingBanner();
}

function updatePendingBanner() {
  const pendingCount = activeItems.filter(i => i.audit_status === 'Pending').length;
  const banner = document.getElementById('pending-banner');
  if (banner) {
    banner.style.display = pendingCount > 0 ? '' : 'none';
    document.getElementById('pending-banner-count').textContent = pendingCount;
  }
}

// ════════════════════════════════════════════════════════════════
// ACTIONS
// ════════════════════════════════════════════════════════════════

async function saveItem(itemId, status, notes) {
  try {
    await postAction({ action: 'update_item', item_id: itemId, audit_status: status, notes });

    // Update local state to avoid full reload
    const idx = activeItems.findIndex(i => i.id === itemId);
    if (idx !== -1) {
      activeItems[idx].audit_status = status;
      activeItems[idx].notes        = notes;
      activeItems[idx].audited_at   = new Date().toISOString();
    }
    renderItemTable();
    showToast(`Item #${itemId} marked as ${status}.`, 'success');
  } catch (err) {
    showToast(err.message || 'Failed to save item.', 'error');
  }
}

async function handleCreateCycle(e) {
  e.preventDefault();

  const title    = document.getElementById('cycle-title')?.value.trim() || '';
  const scope    = document.getElementById('cycle-scope')?.value.trim() || '';
  const auditorId= parseInt(document.getElementById('cycle-auditor-id')?.value || '1', 10);
  const assetTagsRaw = (document.getElementById('cycle-asset-tags')?.value || '').trim();

  if (!title || !assetTagsRaw) {
    showToast('Title and at least one asset tag are required.', 'error');
    return;
  }

  // Resolve asset tags → asset IDs
  const tags = assetTagsRaw.split(',').map(t => t.trim().toUpperCase()).filter(Boolean);
  let assetIds = [];
  try {
    const assetsData = await fetchData('/assets', 'GET');
    const allAssets  = assetsData.assets || [];
    tags.forEach(tag => {
      const found = allAssets.find(a => (a.tag || '').toUpperCase() === tag);
      if (found) assetIds.push(found.id);
      else showToast(`Asset tag '${tag}' not found — skipped.`, 'error');
    });
  } catch (err) {
    showToast('Could not resolve asset tags: ' + err.message, 'error');
    return;
  }

  if (assetIds.length === 0) {
    showToast('No valid asset tags provided.', 'error');
    return;
  }

  try {
    const result = await postAction({
      action:            'create_cycle',
      title,
      scope_description: scope,
      auditor_id:        auditorId,
      asset_ids:         assetIds
    });
    closeAllModals();
    document.getElementById('new-cycle-form')?.reset();
    showToast(result.message || 'Audit cycle created!', 'success');
    loadCycles();
  } catch (err) {
    showToast(err.message || 'Failed to create cycle.', 'error');
  }
}

async function handleCloseCycle() {
  if (!activeCycleId) return;

  const pendingLeft = activeItems.filter(i => i.audit_status === 'Pending').length;
  if (pendingLeft > 0) {
    showToast(`${pendingLeft} item(s) still Pending. Review all assets first.`, 'error');
    return;
  }

  if (!confirm('Close this audit cycle? This will lock all items and mark Missing assets as Lost.')) return;

  try {
    const result = await postAction({ action: 'close_cycle', cycle_id: activeCycleId });
    showToast(result.message || 'Cycle closed.', 'success');

    // Reload cycles and switch to report view
    await loadCycles();
    const closedCycle = cycles.find(c => c.id === activeCycleId);
    if (closedCycle) showReport(closedCycle);
    else showCycleList();
  } catch (err) {
    showToast(err.message || 'Failed to close cycle.', 'error');
  }
}

// ── Generic POST helper ──────────────────────────────────────────
async function postAction(payload) {
  const data = await fetchData(BASE, 'POST', payload);
  if (!data.success) throw new Error(data.message || 'Server error.');
  return data;
}

function closeAllModals() {
  document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('open'));
}

// ════════════════════════════════════════════════════════════════
// TOAST
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
    display:flex;align-items:center;gap:10px;padding:12px 18px;border-radius:10px;
    font-size:13px;font-weight:500;color:#fff;max-width:360px;
    box-shadow:0 8px 24px rgba(0,0,0,.35);
    background:${type === 'error'
      ? 'linear-gradient(135deg,#ef4444,#dc2626)'
      : 'linear-gradient(135deg,#10b981,#059669)'};
    animation:slideInToast .25s ease;
  `;
  const icon = type === 'error' ? 'error' : 'check_circle';
  toast.innerHTML = `<span class="material-symbols-outlined" style="font-size:18px;">${icon}</span><span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 5000);
}
