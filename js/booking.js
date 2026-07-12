import { fetchData } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
  // DOM Elements
  const resourceSelect = document.getElementById('resource-select');
  const timelineContainer = document.getElementById('bookings-timeline');
  const bookSlotBtn = document.getElementById('book-slot-btn');
  
  // Modal Elements
  const bookingModal = document.getElementById('booking-modal');
  const bookingForm = document.getElementById('booking-form');
  const bookingResourceTag = document.getElementById('booking-resource-tag');
  const bookingStart = document.getElementById('booking-start');
  const bookingEnd = document.getElementById('booking-end');
  const modalCloseBtn = document.getElementById('booking-modal-close');
  const modalCancelBtn = document.getElementById('booking-modal-cancel');

  // State
  let assets = [];
  let currentBookings = [];
  
  // Set mock static employee user ID for demo purposes
  const currentUserId = 1; // Default Admin user (Aditi Rao)

  // 1. Initializer
  async function init() {
    try {
      // Load assets (resources) list
      assets = await fetchData('/assets', 'GET');
      populateResourcesSelect();
      
      // Auto-select first asset and load its timeline
      if (resourceSelect && resourceSelect.options.length > 1) {
        resourceSelect.selectedIndex = 1;
        await loadBookingsTimeline();
      } else {
        renderEmptyTimeline();
      }
    } catch (e) {
      console.error('Error initializing bookings page:', e);
    }
  }

  // 2. Populate Resource selection
  function populateResourcesSelect() {
    if (!resourceSelect) return;
    resourceSelect.innerHTML = '<option value="">Choose Resource...</option>';
    assets.forEach(asset => {
      // Only hardware/monitors/laptops are bookable
      resourceSelect.innerHTML += `<option value="${asset.id}">${asset.tag} - ${escapeHtml(asset.name)} (${escapeHtml(asset.category_name)})</option>`;
    });
  }

  // 3. Load and Render Bookings Timeline
  if (resourceSelect) {
    resourceSelect.addEventListener('change', loadBookingsTimeline);
  }

  async function loadBookingsTimeline() {
    const assetId = resourceSelect.value;
    if (!assetId) {
      renderEmptyTimeline();
      return;
    }

    try {
      // Fetch bookings for the selected asset resource
      currentBookings = await fetchData(`/bookings?resource_id=${assetId}`, 'GET');
      renderTimeline();
    } catch (err) {
      console.error('Failed to load bookings:', err);
      renderEmptyTimeline();
    }
  }

  function renderEmptyTimeline() {
    if (timelineContainer) {
      timelineContainer.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--text-muted);">Please select a resource asset to view schedules.</div>';
    }
  }

  function renderTimeline() {
    if (!timelineContainer) return;
    timelineContainer.innerHTML = '';

    // Standard hourly blocks for the display
    const hours = [
      { label: '09:00 AM', hourNum: 9 },
      { label: '10:00 AM', hourNum: 10 },
      { label: '11:00 AM', hourNum: 11 },
      { label: '12:00 PM', hourNum: 12 },
      { label: '01:00 PM', hourNum: 13 },
      { label: '02:00 PM', hourNum: 14 },
      { label: '03:00 PM', hourNum: 15 },
      { label: '04:00 PM', hourNum: 16 },
      { label: '05:00 PM', hourNum: 17 }
    ];

    hours.forEach(slot => {
      const row = document.createElement('div');
      row.className = 'timeline-row';
      
      const label = document.createElement('div');
      label.className = 'time-label';
      label.textContent = slot.label;
      row.appendChild(label);
      
      const content = document.createElement('div');
      content.className = 'slot-content';
      
      // Match active bookings that overlap this specific hour block on today's date
      const matched = currentBookings.filter(b => {
        const start = new Date(b.start_time.replace(' ', 'T'));
        const end = new Date(b.end_time.replace(' ', 'T'));
        
        // Match hour (basic visualization mapping)
        const startHour = start.getHours();
        const endHour = end.getHours();
        
        return slot.hourNum >= startHour && slot.hourNum < endHour;
      });

      matched.forEach(b => {
        const start = new Date(b.start_time.replace(' ', 'T'));
        const end = new Date(b.end_time.replace(' ', 'T'));
        const timeStr = `${formatTime(start)} - ${formatTime(end)}`;
        
        const bar = document.createElement('div');
        bar.className = 'booking-bar';
        bar.innerHTML = `
          <span class="material-symbols-outlined" style="font-size: 14px;">bookmark</span>
          <strong>Reserved: ${escapeHtml(b.user_name)}</strong> (${timeStr})
        `;
        content.appendChild(bar);
      });

      row.appendChild(content);
      timelineContainer.appendChild(row);
    });
  }

  function formatTime(date) {
    let hours = date.getHours();
    let minutes = date.getMinutes();
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12;
    hours = hours ? hours : 12; // the hour '0' should be '12'
    minutes = minutes < 10 ? '0' + minutes : minutes;
    return `${hours}:${minutes} ${ampm}`;
  }

  // 4. Modal triggers
  if (bookSlotBtn) {
    bookSlotBtn.addEventListener('click', () => {
      const assetId = resourceSelect.value;
      if (!assetId) {
        alert('Please choose a resource first.');
        return;
      }

      const asset = assets.find(a => a.id === parseInt(assetId));
      if (!asset) return;

      bookingResourceTag.value = `${asset.tag} - ${asset.name}`;
      
      // Autofill date picker to current local time slot
      const now = new Date();
      const localString = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
      const oneHourLater = new Date(now.getTime() + 60 * 60 * 1000);
      const endLocalString = new Date(oneHourLater.getTime() - oneHourLater.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
      
      bookingStart.value = localString;
      bookingEnd.value = endLocalString;

      bookingModal.classList.add('open');
    });
  }

  function closeModal() {
    bookingModal.classList.remove('open');
    bookingForm.reset();
  }

  if (modalCloseBtn) modalCloseBtn.addEventListener('click', closeModal);
  if (modalCancelBtn) modalCancelBtn.addEventListener('click', closeModal);

  // 5. Submit Booking (with Client-Side Overlap Validation)
  if (bookingForm) {
    bookingForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      const assetId = resourceSelect.value;
      const startVal = bookingStart.value; // e.g. "2026-07-12T09:30"
      const endVal = bookingEnd.value;     // e.g. "2026-07-12T10:30"

      if (!assetId || !startVal || !endVal) return;

      const reqStart = new Date(startVal);
      const reqEnd = new Date(endVal);

      if (reqEnd <= reqStart) {
        alert('Ending time must be after starting time.');
        return;
      }

      // CLIENT-SIDE OVERLAP VALIDATION
      const hasOverlap = currentBookings.some(b => {
        const bStart = new Date(b.start_time.replace(' ', 'T'));
        const bEnd = new Date(b.end_time.replace(' ', 'T'));
        // Overlap Math: (s1 < e2) AND (e1 > s2)
        return (reqStart < bEnd) && (reqEnd > bStart);
      });

      if (hasOverlap) {
        alert('Overlap Conflict: The selected time slot is already booked for this resource. Please select a different time.');
        return;
      }

      const submitBtn = bookingForm.querySelector('button[type="submit"]');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Reserving...';

      try {
        const res = await fetchData('/bookings', 'POST', {
          resource_id: parseInt(assetId),
          user_id: currentUserId,
          start_time: startVal,
          end_time: endVal
        });

        if (res.success) {
          alert('Resource reserved successfully!');
          closeModal();
          await loadBookingsTimeline();
        } else {
          alert(res.message || 'Booking failed.');
          submitBtn.disabled = false;
          submitBtn.textContent = 'Confirm Reservation';
        }
      } catch (err) {
        alert('Error connecting to resource booking servlet.');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Confirm Reservation';
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
