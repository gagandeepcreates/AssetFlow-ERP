import { fetchData } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
  const availableEl = document.getElementById('kpi-available');
  const allocatedEl = document.getElementById('kpi-allocated');
  const maintenanceEl = document.getElementById('kpi-maintenance');
  const transfersEl = document.getElementById('kpi-transfers');
  const overdueEl = document.getElementById('kpi-overdue');

  try {
    // Fetch counts from the Java Servlet backend
    const data = await fetchData('/dashboard', 'GET');

    if (data.success) {
      if (availableEl) availableEl.textContent = formatNumber(data.available);
      if (allocatedEl) allocatedEl.textContent = formatNumber(data.allocated);
      if (maintenanceEl) maintenanceEl.textContent = formatNumber(data.maintenance);
      if (transfersEl) transfersEl.textContent = formatNumber(data.transfers);
      if (overdueEl) overdueEl.textContent = formatNumber(data.overdue);
    } else {
      console.error('Failed to load dashboard metrics:', data.message);
      setFallbackValues();
    }
  } catch (error) {
    console.error('Error connecting to dashboard API:', error);
    setFallbackValues();
  }

  function formatNumber(num) {
    return new Intl.NumberFormat().format(num);
  }

  function setFallbackValues() {
    if (availableEl) availableEl.textContent = '--';
    if (allocatedEl) allocatedEl.textContent = '--';
    if (maintenanceEl) maintenanceEl.textContent = '--';
    if (transfersEl) transfersEl.textContent = '--';
    if (overdueEl) overdueEl.textContent = '--';
  }
});
