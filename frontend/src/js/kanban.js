// default sample tickets
const DEFAULT_TICKETS = [
  {
    id: '#TKT-8902',
    title: 'HVAC Compressor Failure',
    priority: 'CRITICAL',
    desc: 'Unit 4 on Sector B roof is reporting abnormal pressure drops. Immediate inspection required.',
    tag: 'ASSET-B04',
    status: 'pending',
    avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBOzk6d-XYXjeoNXTSIlXafA_cyEMrVZiU4H7UHjRQ8DAAIVwfTVILXRe5GQZZerMODaXWm1e_34aevpimcsoCURRrQ_truWJSab3URO2DuIXZsGfgnvIG3i3bP64QfIe62DqdjxqM9aqySNO0RIx2DxV8DUR0NuwjwHeD2WUcEtn7CpNBLTfDhJWRlmkSnTBS3aXbJZbH1XfGW7EbBgbLS86e-OBOBq94-xnevjaX3HQBQLXVggXe2'
  },
  {
    id: '#TKT-8905',
    title: 'Conveyor Belt Misalignment',
    priority: 'HIGH',
    desc: 'Line 2 showing uneven wear on left guide rollers.',
    tag: 'ASSET-L02',
    status: 'pending',
    avatar: ''
  },
  {
    id: '#TKT-8899',
    title: 'Quarterly Pump Calibration',
    priority: 'NORMAL',
    desc: 'Routine calibration for primary coolant pumps scheduled for this week.',
    tag: 'ASSET-P12',
    status: 'approved',
    avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuC7PpCl1az7I9NJAuk__TMYXERMxft2WHf5gdnGmUGe7teEw826XT1UxI79flsm7qJUpRkkEJh6h7zihMhZS86LZZHcy6UCZ1t2PpphDVOldI-rjY1us92JCsNYimpByqegR1a9w3QMODL_ivL37wgyz7UrbheqVMzgwTm-EBhxyU2V5TcpgrpdguTIkxjgSrlpq5_k0TOiBhoggiZroF6vEq68-Zi8XdFD2PAmlET_rCQwsGHJEPfK'
  },
  {
    id: '#TKT-8890',
    title: 'Replace Sensor Array Alpha',
    priority: 'HIGH',
    desc: 'Sensors returning erratic thermal readings. Parts ordered and arrived.',
    tag: 'ASSET-SA01',
    status: 'inprogress',
    avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDPd_7LCvYnpORz53TAC51bhbsovLTIjtv3SaG7YcUkXh9fVDN-u_DEBax0N2jEdazKkx6MymR36dnGYquG-n4BsqVrkhNHhTzZzGb7tg2WQBn9tkVGRDzBVQ6cxgozwSDv7J2-xNxkzsjyPA6g1B-oqLt0B81s8dMqOhgPFqxmDsCqFTJHCRvC4VvWKGrcrzrKdKpKFukIQXFxN4xhEQJccCpl-IHU6q5RjUsZS28lhHwrm0RZ1obI'
  },
  {
    id: '#TKT-8875',
    title: 'Server Rack Cooling Fan Replacement',
    priority: 'NORMAL',
    desc: 'Server rack SR99 fan replacement finished.',
    tag: 'ASSET-SR99',
    status: 'resolved',
    avatar: ''
  }
];

let tickets = [];

document.addEventListener('DOMContentLoaded', () => {
  // Load data from localStorage or seed defaults
  const storedTickets = localStorage.getItem('assetflow_tickets');
  if (storedTickets) {
    tickets = JSON.parse(storedTickets);
  } else {
    tickets = [...DEFAULT_TICKETS];
    localStorage.setItem('assetflow_tickets', JSON.stringify(tickets));
  }

  // DOM elements
  const columns = {
    pending: document.getElementById('col-pending'),
    approved: document.getElementById('col-approved'),
    inprogress: document.getElementById('col-inprogress'),
    resolved: document.getElementById('col-resolved')
  };

  const countLabels = {
    pending: document.getElementById('count-pending'),
    approved: document.getElementById('count-approved'),
    inprogress: document.getElementById('count-inprogress'),
    resolved: document.getElementById('count-resolved')
  };

  const searchInput = document.getElementById('ticket-search');
  const newTicketBtn = document.getElementById('new-ticket-btn');
  const modalOverlay = document.getElementById('ticket-modal');
  const cancelBtn = document.getElementById('cancel-ticket-btn');
  const ticketForm = document.getElementById('new-ticket-form');

  // Render tickets to board columns
  function renderBoard() {
    // Clear list containers
    Object.values(columns).forEach(col => {
      if (col) col.innerHTML = '';
    });

    // Counts mapping
    const counts = { pending: 0, approved: 0, inprogress: 0, resolved: 0 };
    const query = searchInput ? searchInput.value.toLowerCase().trim() : '';

    tickets.forEach(ticket => {
      // Filter by search query
      const matchesSearch = query === '' || 
                            ticket.title.toLowerCase().includes(query) ||
                            ticket.id.toLowerCase().includes(query) ||
                            ticket.desc.toLowerCase().includes(query) ||
                            ticket.tag.toLowerCase().includes(query);

      if (!matchesSearch) return;

      const colContainer = columns[ticket.status];
      if (!colContainer) return;

      counts[ticket.status]++;

      // Create card
      const card = document.createElement('div');
      card.className = 'kanban-card';
      card.setAttribute('draggable', 'true');
      card.setAttribute('id', ticket.id);
      
      // Visual accentuation classes
      if (ticket.status === 'inprogress') {
        card.classList.add('in-progress-active');
      }
      if (ticket.status === 'resolved') {
        card.classList.add('resolved-done');
      }

      // Priority class mapping
      let priorityClass = 'priority-normal';
      if (ticket.priority === 'CRITICAL') priorityClass = 'priority-critical';
      if (ticket.priority === 'HIGH') priorityClass = 'priority-high';

      // Badge layout
      let badgeHTML = `<span class="kanban-card-priority ${priorityClass}">${ticket.priority}</span>`;
      if (ticket.status === 'resolved') {
        badgeHTML = `<span class="kanban-card-priority done-badge"><span class="material-symbols-outlined" style="font-size:12px;">check</span> DONE</span>`;
      }

      // Avatar layout
      let avatarHTML = '';
      if (ticket.avatar) {
        avatarHTML = `<img class="kanban-card-avatar" src="${ticket.avatar}" alt="User Avatar">`;
      } else {
        avatarHTML = `<div class="kanban-card-avatar flex-center font-bold" style="background: rgba(255,255,255,0.08); color: var(--text-muted); font-size: 10px;">UN</div>`;
      }

      card.innerHTML = `
        <div class="kanban-card-top">
          <span class="kanban-card-id">${ticket.id}</span>
          ${badgeHTML}
        </div>
        <h3 class="kanban-card-title">${ticket.title}</h3>
        <p class="kanban-card-desc">${ticket.desc}</p>
        <div class="kanban-card-footer">
          <span class="kanban-card-tag">${ticket.tag}</span>
          ${avatarHTML}
        </div>
      `;

      // Drag Events
      card.addEventListener('dragstart', handleDragStart);
      card.addEventListener('dragend', handleDragEnd);

      colContainer.appendChild(card);
    });

    // Update count labels
    Object.keys(countLabels).forEach(key => {
      if (countLabels[key]) {
        countLabels[key].textContent = counts[key];
      }
    });
  }

  // Drag and drop mechanics
  let draggedCard = null;

  function handleDragStart(e) {
    draggedCard = this;
    this.classList.add('dragging');
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', this.id);
  }

  function handleDragEnd() {
    draggedCard = null;
    this.classList.remove('dragging');
    
    // Remove drag-over highlights from all columns
    Object.values(columns).forEach(col => {
      const colBox = col.closest('.kanban-column');
      if (colBox) {
        colBox.classList.remove('drag-over', 'active-target');
      }
    });
  }

  // Setup column drop listeners
  Object.keys(columns).forEach(status => {
    const listContainer = columns[status];
    if (!listContainer) return;

    const columnBox = listContainer.closest('.kanban-column');
    if (!columnBox) return;

    columnBox.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      columnBox.classList.add('drag-over');
      columnBox.classList.add('active-target');
    });

    columnBox.addEventListener('dragleave', () => {
      columnBox.classList.remove('drag-over');
      columnBox.classList.remove('active-target');
    });

    columnBox.addEventListener('drop', (e) => {
      e.preventDefault();
      columnBox.classList.remove('drag-over');
      columnBox.classList.remove('active-target');

      const ticketId = e.dataTransfer.getData('text/plain');
      const ticketIndex = tickets.findIndex(t => t.id === ticketId);
      
      if (ticketIndex !== -1 && tickets[ticketIndex].status !== status) {
        tickets[ticketIndex].status = status;
        localStorage.setItem('assetflow_tickets', JSON.stringify(tickets));
        renderBoard();
      }
    });
  });

  // Search input change handler
  if (searchInput) {
    searchInput.addEventListener('input', renderBoard);
  }

  // Modal open/close handlers
  if (newTicketBtn && modalOverlay) {
    newTicketBtn.addEventListener('click', () => {
      // Auto-generate ticket id
      const ticketIdInput = document.getElementById('new-ticket-id');
      if (ticketIdInput) {
        const nextNum = Math.floor(8906 + Math.random() * 1000);
        ticketIdInput.value = `#TKT-${nextNum}`;
      }
      modalOverlay.classList.add('open');
    });
  }

  if (cancelBtn && modalOverlay) {
    cancelBtn.addEventListener('click', () => {
      modalOverlay.classList.remove('open');
      if (ticketForm) ticketForm.reset();
    });
  }

  // Form submission handler
  if (ticketForm && modalOverlay) {
    ticketForm.addEventListener('submit', (e) => {
      e.preventDefault();

      const newTicket = {
        id: document.getElementById('new-ticket-id').value.trim() || '#TKT-NEW',
        title: document.getElementById('new-ticket-title').value.trim(),
        priority: document.getElementById('new-ticket-priority').value,
        desc: document.getElementById('new-ticket-desc').value.trim(),
        tag: document.getElementById('new-ticket-tag').value.trim().toUpperCase(),
        status: 'pending', // all new tickets start in pending
        avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBOzk6d-XYXjeoNXTSIlXafA_cyEMrVZiU4H7UHjRQ8DAAIVwfTVILXRe5GQZZerMODaXWm1e_34aevpimcsoCURRrQ_truWJSab3URO2DuIXZsGfgnvIG3i3bP64QfIe62DqdjxqM9aqySNO0RIx2DxV8DUR0NuwjwHeD2WUcEtn7CpNBLTfDhJWRlmkSnTBS3aXbJZbH1XfGW7EbBgbLS86e-OBOBq94-xnevjaX3HQBQLXVggXe2'
      };

      if (!newTicket.title || !newTicket.desc || !newTicket.tag) {
        alert('Please fill out all fields.');
        return;
      }

      tickets.unshift(newTicket);
      localStorage.setItem('assetflow_tickets', JSON.stringify(tickets));

      modalOverlay.classList.remove('open');
      ticketForm.reset();
      
      renderBoard();
    });
  }

  // Initial board render
  renderBoard();
});
