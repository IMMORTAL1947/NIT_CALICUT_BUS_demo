// Prefer same-origin API when admin UI is served by the backend; allow override via ?api= URL param
const apiParam = new URLSearchParams(location.search).get('api');
const base = (apiParam && apiParam.trim().length) ? apiParam.trim() : window.location.origin;
const API_BASE = base.replace(/\/$/, '') + '/api';

const map = L.map('map').setView([11.3215, 75.9342], 15);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap'
}).addTo(map);

let markers = [];
let searchMarkers = [];
let stops = [];
let routes = [];
let buses = [];
let schedules = [];
let requests = [];

const $ = (id) => document.getElementById(id);

// Auto-load default college on page load
window.addEventListener('DOMContentLoaded', () => {
  const defaultCode = 'NITC'; // Default college code
  $('collegeCode').value = defaultCode;
  $('collegeName').value = 'NIT CALICUT';
  loadCollege(defaultCode);
});

function renderStopsList() {
  const ul = $('stopsList');
  ul.innerHTML = '';
  stops.forEach((s, idx) => {
    const li = document.createElement('li');
    li.style.display = 'flex';
    li.style.alignItems = 'center';
    li.style.gap = '8px';
    li.style.marginBottom = '8px';
    li.style.paddingBottom = '8px';
    li.style.borderBottom = '1px solid #e6e6e6';

    const idSpan = document.createElement('span');
    idSpan.innerHTML = `<b>${s.id}</b>`;

    const input = document.createElement('input');
    input.value = s.name;
    input.dataset.idx = idx;
    input.className = 'stopName';
    input.style.flex = '1';

    const coordsSpan = document.createElement('span');
    coordsSpan.textContent = `(${s.lat.toFixed(6)}, ${s.lng.toFixed(6)})`;
    coordsSpan.className = 'coords';

    const deleteBtn = document.createElement('button');
    deleteBtn.textContent = 'Delete';
    deleteBtn.className = 'danger-btn deleteStop';
    deleteBtn.dataset.idx = idx;
    deleteBtn.style.backgroundColor = '#f44336';
    deleteBtn.style.color = 'white';
    deleteBtn.style.border = '1px solid #d32f2f';
    deleteBtn.style.padding = '6px 12px';
    deleteBtn.style.cursor = 'pointer';
    deleteBtn.style.borderRadius = '4px';
    deleteBtn.style.fontSize = '0.9rem';
    deleteBtn.style.flexShrink = '0';

    li.appendChild(idSpan);
    li.appendChild(input);
    li.appendChild(coordsSpan);
    li.appendChild(deleteBtn);
    ul.appendChild(li);
  });
  ul.querySelectorAll('.stopName').forEach(inp => {
    inp.addEventListener('input', (e) => {
      const idx = Number(e.target.dataset.idx);
      stops[idx].name = e.target.value;
    });
  });
  ul.querySelectorAll('.deleteStop').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const idx = Number(e.target.dataset.idx);
      stops.splice(idx, 1);
      refreshMarkers();
      renderStopsList();
    });
  });
}

function refreshMarkers() {
  markers.forEach(m => m.remove());
  markers = stops.map(s => L.marker([s.lat, s.lng]).addTo(map).bindPopup(s.name));
}

map.on('click', (e) => {
  const id = 's' + (stops.length + 1);
  const stop = { id, name: 'Stop ' + (stops.length + 1), lat: e.latlng.lat, lng: e.latlng.lng };
  stops.push(stop);
  refreshMarkers();
  renderStopsList();
});

// Create College (from create section)
const btnCreate = $('btnCreateCollege');
if (btnCreate) {
  btnCreate.onclick = async () => {
    const code = $('collegeCode').value.trim();
    const name = $('collegeName').value.trim();
    if (!code) return alert('Enter college code');
    const res = await fetch(`${API_BASE}/colleges`, { method: 'POST', headers: { 'Content-Type':'application/json' }, body: JSON.stringify({ code, name }) });
    const json = await res.json();
    $('status').textContent = `Saved college ${json.code}`;
    await loadCollege(json.code);
  };
}


// Toggle create vs update flow
const hintLink = document.querySelector('.hint-link');
if (hintLink) {
  hintLink.addEventListener('click', (e) => {
    e.preventDefault();
    const createSec = $('createCollege');
    if (createSec) createSec.style.display = 'block';
    const typed = $('searchCollege').value.trim();
    if (typed) $('collegeCode').value = typed;
  });
}

const searchInp = $('searchCollege');
if (searchInp) {
  searchInp.addEventListener('input', (e) => {
    const createSec = $('createCollege');
    if (createSec) createSec.style.display = 'none';
  });
}

// Search colleges
$('btnSearch').onclick = async () => {
  const q = $('searchCollege').value.trim();
  const res = await fetch(`${API_BASE}/colleges?q=${encodeURIComponent(q)}`);
  const list = await res.json();
  const sel = $('searchResults');
  sel.innerHTML = '';
  list.forEach(c => {
    const opt = document.createElement('option');
    opt.value = c.code; opt.textContent = `${c.code} — ${c.name || ''}`;
    sel.appendChild(opt);
  });
  if (list.length) {
    sel.value = list[0].code;
    $('collegeCode').value = list[0].code;
    $('collegeName').value = list[0].name || '';
    await loadCollege(list[0].code);
  }
};

$('searchResults').onchange = async (e) => {
  const code = e.target.value;
  $('collegeCode').value = code;
  await loadCollege(code);
};

async function loadCollege(code) {
  try {
    const res = await fetch(`${API_BASE}/colleges/${code}/config`);
    if (!res.ok) throw new Error('Not found');
    const cfg = await res.json();
    stops = cfg.stops || [];
    routes = cfg.routes || [];
    buses = cfg.buses || [];
    schedules = cfg.schedules || [];
    refreshMarkers();
    renderStopsList();
    renderRoutesList();
    renderBusesList();
    renderCrowdConfigList();
    renderSchedulesList();
    updateScheduleDropdowns();
    $('status').textContent = `Loaded ${code}`;
  } catch (e) {
    $('status').textContent = `Load failed: ${e.message}`;
  }
}

function updateScheduleDropdowns() {
  const routeSelect = $('scheduleRouteId');
  const busSelect = $('scheduleBusId');
  
  if (!routeSelect || !busSelect) {
    console.error('Schedule dropdown elements not found');
    return;
  }
  
  // Populate routes
  routeSelect.innerHTML = '<option value="">Select Route</option>';
  if (routes && routes.length > 0) {
    routes.forEach(r => {
      const opt = document.createElement('option');
      opt.value = r.id;
      opt.textContent = r.name || r.id;
      routeSelect.appendChild(opt);
    });
  } else {
    const opt = document.createElement('option');
    opt.value = '';
    opt.textContent = '(No routes available)';
    routeSelect.appendChild(opt);
  }
  
  // Populate buses
  busSelect.innerHTML = '<option value="">Select Bus</option>';
  if (buses && buses.length > 0) {
    buses.forEach(b => {
      const opt = document.createElement('option');
      opt.value = b.id;
      opt.textContent = b.name || b.id;
      busSelect.appendChild(opt);
    });
  } else {
    const opt = document.createElement('option');
    opt.value = '';
    opt.textContent = '(No buses available)';
    busSelect.appendChild(opt);
  }
}

function renderSchedulesList() {
  const container = $('schedulesList');
  container.innerHTML = '';
  
  if (schedules.length === 0) {
    container.innerHTML = '<p style="padding: 16px; color: #999;">No schedules added yet</p>';
    return;
  }
  
  const table = document.createElement('table');
  table.style.width = '100%';
  table.style.borderCollapse = 'collapse';
  table.style.fontSize = '0.9em';
  
  // Header
  const headerRow = document.createElement('tr');
  headerRow.style.backgroundColor = '#f5f5f5';
  headerRow.style.borderBottom = '2px solid #ddd';
  ['Bus', 'Route', 'Departure', 'Days', 'Actions'].forEach(col => {
    const th = document.createElement('th');
    th.textContent = col;
    th.style.padding = '10px';
    th.style.textAlign = 'left';
    th.style.fontWeight = 'bold';
    th.style.borderBottom = '2px solid #ddd';
    headerRow.appendChild(th);
  });
  table.appendChild(headerRow);
  
  // Rows
  schedules.forEach((sch) => {
    const bus = buses.find(b => b.id === sch.busId);
    const route = routes.find(r => r.id === sch.routeId);
    if (!bus || !route) return; // Skip orphaned schedules
    
    const row = document.createElement('tr');
    row.style.borderBottom = '1px solid #eee';
    row.dataset.scheduleId = sch.id;
    
    const busName = bus.name || sch.busId;
    const routeName = route.name || sch.routeId;
    const depTime = sch.departureTime;
    const days = (sch.activeDays || []).join(', ');
    
    const cells = [busName, routeName, depTime, days];
    cells.forEach(cellText => {
      const td = document.createElement('td');
      td.textContent = cellText;
      td.style.padding = '10px';
      td.style.borderBottom = '1px solid #eee';
      row.appendChild(td);
    });
    
    // Actions
    const actionsTd = document.createElement('td');
    actionsTd.style.padding = '10px';
    
    const editBtn = document.createElement('button');
    editBtn.textContent = 'Edit';
    editBtn.style.backgroundColor = '#2196f3';
    editBtn.style.color = 'white';
    editBtn.style.padding = '4px 8px';
    editBtn.style.border = 'none';
    editBtn.style.borderRadius = '3px';
    editBtn.style.cursor = 'pointer';
    editBtn.style.marginRight = '4px';
    editBtn.style.fontSize = '0.85em';
    editBtn.onclick = () => editSchedule(sch);
    
    const deleteBtn = document.createElement('button');
    deleteBtn.textContent = 'Delete';
    deleteBtn.style.backgroundColor = '#f44336';
    deleteBtn.style.color = 'white';
    deleteBtn.style.padding = '4px 8px';
    deleteBtn.style.border = 'none';
    deleteBtn.style.borderRadius = '3px';
    deleteBtn.style.cursor = 'pointer';
    deleteBtn.style.fontSize = '0.85em';
    deleteBtn.onclick = () => deleteSchedule(sch.id);
    
    actionsTd.appendChild(editBtn);
    actionsTd.appendChild(deleteBtn);
    row.appendChild(actionsTd);
    table.appendChild(row);
  });
  
  container.appendChild(table);
}

function editSchedule(schedule) {
  $('scheduleRouteId').value = schedule.routeId;
  $('scheduleBusId').value = schedule.busId;
  $('scheduleDepartureTime').value = schedule.departureTime;
  $('scheduleActiveDays').value = (schedule.activeDays || []).join(',');
  $('scheduleId').value = schedule.id;
  $('addScheduleBtn').textContent = 'Update Schedule';
}

$('addScheduleBtn').onclick = async () => {
  const code = $('collegeCode').value.trim();
  if (!code) return alert('Enter college code first');
  
  const routeId = $('scheduleRouteId').value.trim();
  const busId = $('scheduleBusId').value.trim();
  const departureTime = $('scheduleDepartureTime').value.trim();
  const activeDaysStr = $('scheduleActiveDays').value.trim();
  const activeDays = activeDaysStr ? activeDaysStr.split(',').map(d => d.trim().toUpperCase()) : ['MON', 'TUE', 'WED', 'THU', 'FRI'];
  const scheduleId = $('scheduleId').value.trim();
  
  if (!routeId || !busId || !departureTime) {
    return alert('Select route, bus, and departure time');
  }
  
  try {
    let res;
    if (scheduleId) {
      // Update
      res = await fetch(`${API_BASE}/colleges/${code}/schedules/${scheduleId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ routeId, busId, departureTime, activeDays })
      });
    } else {
      // Create
      res = await fetch(`${API_BASE}/colleges/${code}/schedules`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ routeId, busId, departureTime, activeDays })
      });
    }
    
    if (!res.ok) throw new Error(`Failed: ${res.status}`);
    const sch = await res.json();
    
    if (scheduleId) {
      const idx = schedules.findIndex(s => s.id === scheduleId);
      if (idx >= 0) schedules[idx] = sch;
    } else {
      schedules.push(sch);
    }
    
    renderSchedulesList();
    $('scheduleRouteId').value = '';
    $('scheduleBusId').value = '';
    $('scheduleDepartureTime').value = '';
    $('scheduleActiveDays').value = '';
    $('scheduleId').value = '';
    $('addScheduleBtn').textContent = 'Add Schedule';
    $('status').textContent = `Schedule ${scheduleId ? 'updated' : 'added'}`;
  } catch (e) {
    $('status').textContent = `Error: ${e.message}`;
  }
};

async function deleteSchedule(scheduleId) {
  if (!confirm('Delete this schedule?')) return;
  const code = $('collegeCode').value.trim();
  if (!code) return;
  
  try {
    const res = await fetch(`${API_BASE}/colleges/${code}/schedules/${scheduleId}`, {
      method: 'DELETE'
    });
    if (!res.ok) throw new Error(`Failed: ${res.status}`);
    
    const idx = schedules.findIndex(s => s.id === scheduleId);
    if (idx >= 0) schedules.splice(idx, 1);
    renderSchedulesList();
    $('status').textContent = 'Schedule deleted';
  } catch (e) {
    $('status').textContent = `Delete failed: ${e.message}`;
  }
}

$('saveStops').onclick = async () => {
  const code = $('collegeCode').value.trim();
  if (!code) return alert('Enter college code first');
  const res = await fetch(`${API_BASE}/colleges/${code}/stops`, { method:'POST', headers:{ 'Content-Type':'application/json' }, body: JSON.stringify({ stops }) });
  const json = await res.json();
  $('status').textContent = `Saved ${json.stops.length} stops.`;
};

$('addRoute').onclick = () => {
  const name = $('routeName').value.trim();
  const color = $('routeColor').value;
  const stopIds = $('routeStopIds').value.split(',').map(s => s.trim()).filter(Boolean);
  if (!name || stopIds.length === 0) return alert('Enter route name and stop IDs');
  const id = name.toLowerCase().replace(/\s+/g,'-');
  const existingIdx = routes.findIndex(r => r.id === id);
  const route = { id, name, color, stopIds };
  if (existingIdx >= 0) routes[existingIdx] = route; else routes.push(route);
  renderRoutesList();
};

function renderRoutesList() {
  const ul = $('routesList');
  ul.innerHTML = '';
  routes.forEach(r => {
    const li = document.createElement('li');
    li.style.display = 'flex';
    li.style.alignItems = 'center';
    li.style.gap = '8px';
    li.style.marginBottom = '8px';
    li.style.paddingBottom = '8px';
    li.style.borderBottom = '1px solid #e6e6e6';
    const bus = (buses.find(b => b.routeId === r.id) || {}).name || '';
    const times = r.stopTimes || {};
    const ss = r.stopIds.map(id => `${id}${times[id] ? ' @'+times[id] : ''}`).join(' -> ');
    li.style.borderLeft = `8px solid ${r.color}`;
    li.style.paddingLeft = '8px';

    const textSpan = document.createElement('span');
    textSpan.innerHTML = `<b>${r.name}</b>${bus ? ' • '+bus : ''} <small>(${r.id}) - ${ss}</small>`;
    textSpan.style.flex = '1';

    const deleteBtn = document.createElement('button');
    deleteBtn.textContent = 'Delete';
    deleteBtn.className = 'danger-btn deleteRoute';
    deleteBtn.dataset.idx = routes.indexOf(r);
    deleteBtn.style.backgroundColor = '#f44336';
    deleteBtn.style.color = 'white';
    deleteBtn.style.border = '1px solid #d32f2f';
    deleteBtn.style.padding = '6px 12px';
    deleteBtn.style.cursor = 'pointer';
    deleteBtn.style.borderRadius = '4px';
    deleteBtn.style.fontSize = '0.9rem';
    deleteBtn.style.flexShrink = '0';

    li.appendChild(textSpan);
    li.appendChild(deleteBtn);
    ul.appendChild(li);
  });
  ul.querySelectorAll('.deleteRoute').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const idx = Number(e.target.dataset.idx);
      routes.splice(idx, 1);
      renderRoutesList();
    });
  });
}

$('saveRoutes').onclick = async () => {
  const code = $('collegeCode').value.trim();
  if (!code) return alert('Enter college code first');
  const res = await fetch(`${API_BASE}/colleges/${code}/routes`, { method:'POST', headers:{ 'Content-Type':'application/json' }, body: JSON.stringify({ routes }) });
  const json = await res.json();
  $('status').textContent = `Saved ${json.routes.length} routes.`;
};

// Buses
function renderBusesList() {
  const ul = $('busesList');
  if (!ul) return;
  ul.innerHTML = '';
  buses.forEach(b => {
    const li = document.createElement('li');
    const last4 = b.driverToken ? b.driverToken.slice(-4) : '';
    const tokenHtml = b.driverToken ? ` &nbsp; <small class="token" title="last 4: ${last4}">Driver Token: •••••••• (active)</small>` : '';
    const copyBtn = ` &nbsp; <button data-busid="${b.id}" class="copyLink">Copy Driver Link</button>`;
    const openLink = b.driverToken
      ? ` &nbsp; <a target="_blank" href="/driver.html?api=${encodeURIComponent(window.location.origin)}&code=${encodeURIComponent($('collegeCode').value.trim())}&busId=${encodeURIComponent(b.id)}&token=${encodeURIComponent(b.driverToken)}">Open Driver Tracking Page</a>`
      : '';
    li.innerHTML = `${b.name} (<b>${b.id}</b>) → ${b.routeId || ''}${tokenHtml}` +
      ` &nbsp; <button data-busid="${b.id}" class="genToken">Generate Driver Token</button>` +
      copyBtn +
      openLink;
    ul.appendChild(li);
  });
  ul.querySelectorAll('.genToken').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      const code = $('collegeCode').value.trim();
      const busId = e.target.getAttribute('data-busid');
      if (!code || !busId) return alert('Select college and bus first');
      try {
        const res = await fetch(`${API_BASE}/colleges/${code}/buses/${busId}/driver-token`, { method:'POST' });
        const json = await res.json();
        const idx = buses.findIndex(b => b.id === busId);
        if (idx >= 0) buses[idx].driverToken = json.driverToken;
        try { await navigator.clipboard.writeText(json.deepLink || ''); $('status').textContent = 'Driver token generated; link copied'; }
        catch { $('status').textContent = 'Driver token generated'; }
        renderBusesList();
      } catch (err) {
        $('status').textContent = 'Token generate failed';
      }
    });
  });
  ul.querySelectorAll('.copyLink').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      const code = $('collegeCode').value.trim();
      const busId = e.target.getAttribute('data-busid');
      const b = buses.find(x => x.id === busId);
      if (!code) { alert('Enter college code first'); return; }
      if (!b) { alert('Select a bus first'); return; }
      if (!b.driverToken) { alert('Generate token first'); return; }
      const deepLink = `campus-transit://driver?code=${encodeURIComponent(code)}&busId=${encodeURIComponent(busId)}&token=${encodeURIComponent(b.driverToken)}&api=${encodeURIComponent(window.location.origin)}`;
      try { await navigator.clipboard.writeText(deepLink); $('status').textContent = 'Driver tracking link copied'; }
      catch { $('status').textContent = deepLink; }
    });
  });
}

$('addBus').onclick = () => {
  const name = $('busName').value.trim();
  const routeId = $('busRouteId').value.trim();
  if (!name || !routeId) return alert('Enter bus name and route id');
  const id = name.toLowerCase().replace(/\s+/g,'-');
  const idx = buses.findIndex(b => b.id === id);
  const bus = { id, name, routeId };
  if (idx >= 0) buses[idx] = bus; else buses.push(bus);
  renderBusesList();
};

$('saveBuses').onclick = async () => {
  const code = $('collegeCode').value.trim();
  if (!code) return alert('Enter college code first');
  const res = await fetch(`${API_BASE}/colleges/${code}/buses`, { method:'POST', headers:{ 'Content-Type':'application/json' }, body: JSON.stringify({ buses }) });
  const json = await res.json();
  $('status').textContent = `Saved bus settings for ${json.buses.length} bus(es).`;
};

// ===== Live Crowd Monitoring =====
function renderCrowdConfigList() {
  const ul = $('crowdConfigList');
  if (!ul) return;
  ul.innerHTML = '';
  buses.forEach(b => {
    const li = document.createElement('li');
    li.style.border = '1px solid #ddd';
    li.style.borderRadius = '4px';
    li.style.padding = '12px';
    li.style.marginBottom = '10px';
    li.style.backgroundColor = '#f9f9f9';
    
    const h4 = document.createElement('h4');
    h4.textContent = `${b.name} (${b.id})`;
    h4.style.margin = '0 0 10px 0';
    
    const form = document.createElement('div');
    form.style.display = 'grid';
    form.style.gridTemplateColumns = '1fr 1fr';
    form.style.gap = '10px';
    
    // API URL field
    const apiLabel = document.createElement('label');
    apiLabel.textContent = 'Crowd API URL:';
    const apiInput = document.createElement('input');
    apiInput.type = 'text';
    apiInput.value = b.crowdApiUrl || '';
    apiInput.placeholder = 'e.g. https://api.example.com/bus/id';
    apiInput.style.gridColumn = '1 / -1';
    apiInput.dataset.busId = b.id;
    apiInput.className = 'crowdApiUrl';
    
    // Capacity field
    const capLabel = document.createElement('label');
    capLabel.textContent = 'Bus Capacity:';
    const capInput = document.createElement('input');
    capInput.type = 'number';
    capInput.value = b.busCapacity || 40;
    capInput.min = '1';
    capInput.dataset.busId = b.id;
    capInput.className = 'busCapacity';
    
    // Polling interval field
    const intervalLabel = document.createElement('label');
    intervalLabel.textContent = 'Poll Interval (seconds):';
    const intervalInput = document.createElement('input');
    intervalInput.type = 'number';
    intervalInput.value = b.pollingInterval || 10;
    intervalInput.min = '5';
    intervalInput.max = '300';
    intervalInput.dataset.busId = b.id;
    intervalInput.className = 'pollingInterval';
    
    // Polling toggle
    const toggleLabel = document.createElement('label');
    toggleLabel.style.gridColumn = '1 / -1';
    toggleLabel.style.display = 'flex';
    toggleLabel.style.alignItems = 'center';
    toggleLabel.style.gap = '8px';
    const toggleCheckbox = document.createElement('input');
    toggleCheckbox.type = 'checkbox';
    toggleCheckbox.checked = b.pollingEnabled !== false;
    toggleCheckbox.data = b.id;
    toggleCheckbox.className = 'pollingEnabled';
    toggleCheckbox.dataset.busId = b.id;
    toggleLabel.appendChild(toggleCheckbox);
    toggleLabel.appendChild(document.createTextNode('Enable Polling'));
    
    // Save button
    const saveBtn = document.createElement('button');
    saveBtn.textContent = 'Save Crowd Config';
    saveBtn.style.gridColumn = '1 / -1';
    saveBtn.style.backgroundColor = '#2196F3';
    saveBtn.style.color = 'white';
    saveBtn.style.border = 'none';
    saveBtn.style.padding = '8px 16px';
    saveBtn.style.borderRadius = '4px';
    saveBtn.style.cursor = 'pointer';
    saveBtn.className = 'saveCrowdConfig';
    saveBtn.dataset.busId = b.id;
    
    form.appendChild(apiLabel);
    form.appendChild(apiInput);
    form.appendChild(capLabel);
    form.appendChild(capInput);
    form.appendChild(intervalLabel);
    form.appendChild(intervalInput);
    form.appendChild(toggleLabel);
    form.appendChild(saveBtn);
    
    li.appendChild(h4);
    li.appendChild(form);
    ul.appendChild(li);
  });
  
  // Attach event listeners
  ul.querySelectorAll('.saveCrowdConfig').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      const code = $('collegeCode').value.trim();
      const busId = e.target.dataset.busId;
      if (!code) return alert('Select college first');
      
      // Gather form data
      const apiInput = ul.querySelector(`.crowdApiUrl[data-bus-id="${busId}"]`);
      const capInput = ul.querySelector(`.busCapacity[data-bus-id="${busId}"]`);
      const intervalInput = ul.querySelector(`.pollingInterval[data-bus-id="${busId}"]`);
      const enabledCheckbox = ul.querySelector(`.pollingEnabled[data-bus-id="${busId}"]`);
      
      const payload = {
        crowdApiUrl: apiInput?.value || '',
        busCapacity: parseInt(capInput?.value) || 40,
        pollingInterval: parseInt(intervalInput?.value) || 10,
        pollingEnabled: enabledCheckbox?.checked !== false
      };
      
      try {
        const res = await fetch(`${API_BASE}/colleges/${code}/buses/${busId}/crowd-config`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const json = await res.json();
        if (json.ok) {
          $('status').textContent = `Crowd config saved for ${busId}`;
          // Update local bus data
          const bus = buses.find(b => b.id === busId);
          if (bus) {
            Object.assign(bus, payload);
          }
        } else {
          $('status').textContent = json.error || 'Save failed';
        }
      } catch (err) {
        $('status').textContent = `Error: ${err.message}`;
      }
    });
  });
}

// ===== On-Demand Booking Requests =====

async function loadRequests() {
  const code = $('collegeCode').value.trim();
  if (!code) return;
  try {
    const res = await fetch(`${API_BASE}/colleges/${code}/requests`);
    const json = await res.json();
    requests = json.requests || [];
    renderRequestsList();
  } catch (e) {
    $('status').textContent = `Load requests failed: ${e.message}`;
  }
}

function renderRequestsList() {
  const container = $('requestsList');
  container.innerHTML = '';
  const statusFilter = $('requestStatusFilter').value;
  const filtered = statusFilter ? requests.filter(r => r.status === statusFilter) : requests;
  
  if (filtered.length === 0) {
    container.innerHTML = '<p style="padding: 16px; color: #999;">No requests found</p>';
    return;
  }
  
  const table = document.createElement('table');
  table.style.width = '100%';
  table.style.borderCollapse = 'collapse';
  table.style.fontSize = '0.9em';
  
  // Header
  const headerRow = document.createElement('tr');
  headerRow.style.backgroundColor = '#f5f5f5';
  headerRow.style.borderBottom = '2px solid #ddd';
  ['ID', 'Route', 'Pickup', 'Destination', 'DateTime', 'Passengers', 'Status', 'Actions'].forEach(col => {
    const th = document.createElement('th');
    th.textContent = col;
    th.style.padding = '8px';
    th.style.textAlign = 'left';
    th.style.fontWeight = 'bold';
    headerRow.appendChild(th);
  });
  table.appendChild(headerRow);
  
  // Rows
  filtered.forEach((req, idx) => {
    const row = document.createElement('tr');
    row.style.borderBottom = '1px solid #eee';
    row.dataset.idx = idx;
    
    const cells = [
      req.id,
      req.route || '-',
      `(${(req.pickupLat || 0).toFixed(4)}, ${(req.pickupLng || 0).toFixed(4)})`,
      req.destination || '-',
      new Date(req.dateTime).toLocaleString() || '-',
      req.passengers || '-',
      req.status || 'PENDING'
    ];
    
    cells.forEach(cellText => {
      const td = document.createElement('td');
      td.textContent = cellText;
      td.style.padding = '8px';
      td.style.borderBottom = '1px solid #eee';
      row.appendChild(td);
    });
    
    // Actions cell
    const actionsTd = document.createElement('td');
    actionsTd.style.padding = '8px';
    
    if (req.status === 'PENDING') {
      const approveBtn = document.createElement('button');
      approveBtn.textContent = 'Approve';
      approveBtn.style.backgroundColor = '#4caf50';
      approveBtn.style.color = 'white';
      approveBtn.style.padding = '4px 8px';
      approveBtn.style.border = 'none';
      approveBtn.style.borderRadius = '3px';
      approveBtn.style.cursor = 'pointer';
      approveBtn.style.marginRight = '4px';
      approveBtn.style.fontSize = '0.85em';
      approveBtn.dataset.requestId = req.id;
      approveBtn.onclick = (e) => updateRequestStatus(req.id, 'APPROVED');
      
      const rejectBtn = document.createElement('button');
      rejectBtn.textContent = 'Reject';
      rejectBtn.style.backgroundColor = '#f44336';
      rejectBtn.style.color = 'white';
      rejectBtn.style.padding = '4px 8px';
      rejectBtn.style.border = 'none';
      rejectBtn.style.borderRadius = '3px';
      rejectBtn.style.cursor = 'pointer';
      rejectBtn.style.fontSize = '0.85em';
      rejectBtn.dataset.requestId = req.id;
      rejectBtn.onclick = (e) => updateRequestStatus(req.id, 'REJECTED');
      
      actionsTd.appendChild(approveBtn);
      actionsTd.appendChild(rejectBtn);
    } else {
      const statusSpan = document.createElement('span');
      statusSpan.textContent = req.status;
      statusSpan.style.padding = '4px 8px';
      statusSpan.style.borderRadius = '3px';
      statusSpan.style.backgroundColor = req.status === 'APPROVED' ? '#e8f5e9' : '#ffebee';
      statusSpan.style.color = req.status === 'APPROVED' ? '#2e7d32' : '#c62828';
      statusSpan.style.fontSize = '0.85em';
      actionsTd.appendChild(statusSpan);
    }
    
    row.appendChild(actionsTd);
    table.appendChild(row);
  });
  
  container.appendChild(table);
}

async function updateRequestStatus(requestId, status) {
  const code = $('collegeCode').value.trim();
  if (!code) return alert('Enter college code first');
  
  try {
    const res = await fetch(`${API_BASE}/colleges/${code}/requests/${requestId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status })
    });
    if (!res.ok) throw new Error(`Request failed: ${res.status}`);
    const json = await res.json();
    const idx = requests.findIndex(r => r.id === requestId);
    if (idx >= 0) requests[idx] = json;
    renderRequestsList();
    $('status').textContent = `Request ${requestId} updated to ${status}`;
  } catch (e) {
    $('status').textContent = `Update failed: ${e.message}`;
  }
}

$('refreshRequests').onclick = loadRequests;
$('requestStatusFilter').onchange = renderRequestsList;

// Hook into loadCollege to also load requests
const originalLoadCollege = window.loadCollege;
if (originalLoadCollege) {
  window.loadCollege = async function(code) {
    await originalLoadCollege.call(this, code);
    loadRequests();
  };
}
// Map place search via Nominatim
async function searchPlaces(q) {
  const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&q=${encodeURIComponent(q)}`;
  const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
  if (!res.ok) throw new Error('Search failed');
  return await res.json();
}

function clearSearchMarkers() {
  searchMarkers.forEach(m => m.remove());
  searchMarkers = [];
}

$('btnPlaceSearch').onclick = async () => {
  const q = $('placeSearch').value.trim();
  if (!q) return;
  $('status').textContent = 'Searching...';
  try {
    const results = await searchPlaces(q);
    const sel = $('placeResults');
    sel.innerHTML = '';
    results.forEach(r => {
      const opt = document.createElement('option');
      opt.value = JSON.stringify({ lat: r.lat, lon: r.lon, display_name: r.display_name, bbox: r.boundingbox });
      opt.textContent = r.display_name;
      sel.appendChild(opt);
    });
    if (results.length) {
      sel.selectedIndex = 0;
      focusToSelectedPlace();
      $('status').textContent = `Found ${results.length} result(s)`;
    } else {
      $('status').textContent = 'No results';
    }
  } catch (e) {
    $('status').textContent = `Search error: ${e.message}`;
  }
};

function focusToSelectedPlace() {
  const sel = $('placeResults');
  if (!sel.value) return;
  const data = JSON.parse(sel.value);
  clearSearchMarkers();
  const lat = parseFloat(data.lat), lon = parseFloat(data.lon);
  const marker = L.marker([lat, lon]).addTo(map).bindPopup(data.display_name);
  searchMarkers.push(marker);
  if (data.bbox && data.bbox.length === 4) {
    // bbox order: south, north, west, east
    const south = parseFloat(data.bbox[0]);
    const north = parseFloat(data.bbox[1]);
    const west = parseFloat(data.bbox[2]);
    const east = parseFloat(data.bbox[3]);
    const bounds = L.latLngBounds([[south, west], [north, east]]);
    map.fitBounds(bounds, { padding: [20, 20] });
  } else {
    map.setView([lat, lon], 16);
  }
  marker.openPopup();
}

$('placeResults').onchange = () => {
  focusToSelectedPlace();
};
