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

const $ = (id) => document.getElementById(id);

function renderStopsList() {
  const ul = $('stopsList');
  ul.innerHTML = '';
  stops.forEach((s, idx) => {
    const li = document.createElement('li');
    li.innerHTML = `ID: <b>${s.id}</b> | <input value="${s.name}" data-idx="${idx}" class="stopName" /> (${s.lat.toFixed(6)}, ${s.lng.toFixed(6)})`;
    ul.appendChild(li);
  });
  ul.querySelectorAll('.stopName').forEach(inp => {
    inp.addEventListener('input', (e) => {
      const idx = Number(e.target.dataset.idx);
      stops[idx].name = e.target.value;
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

$('saveCollege').onclick = async () => {
  const code = $('collegeCode').value.trim();
  const name = $('collegeName').value.trim();
  if (!code) return alert('Enter college code');
  const res = await fetch(`${API_BASE}/colleges`, { method: 'POST', headers: { 'Content-Type':'application/json' }, body: JSON.stringify({ code, name }) });
  const json = await res.json();
  $('status').textContent = `Saved college ${json.code}`;
};

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
    refreshMarkers();
    renderStopsList();
    renderRoutesList();
    renderBusesList();
    $('status').textContent = `Loaded ${code}`;
  } catch (e) {
    $('status').textContent = `Load failed: ${e.message}`;
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
  const stopTimes = collectTimes(stopIds);
  const busName = $('routeBusName').value.trim();
  // If busName provided, ensure buses array has mapping to this route
  if (busName) {
    const busId = busName.toLowerCase().replace(/\s+/g,'-');
    const bIdx = buses.findIndex(b => b.id === busId);
    const bus = { id: busId, name: busName, routeId: id };
    if (bIdx >= 0) buses[bIdx] = bus; else buses.push(bus);
  }
  const route = { id, name, color, stopIds, stopTimes };
  if (existingIdx >= 0) routes[existingIdx] = route; else routes.push(route);
  renderRoutesList();
};

function renderRoutesList() {
  const ul = $('routesList');
  ul.innerHTML = '';
  routes.forEach(r => {
    const li = document.createElement('li');
    const bus = (buses.find(b => b.routeId === r.id) || {}).name || '';
    const times = r.stopTimes || {};
    const ss = r.stopIds.map(id => `${id}${times[id] ? ' @'+times[id] : ''}`).join(' -> ');
    li.textContent = `${r.name}${bus ? ' • '+bus : ''} (${r.id}) - ${ss}`;
    li.style.borderLeft = `8px solid ${r.color}`;
    li.style.paddingLeft = '8px';
    ul.appendChild(li);
  });
}

$('saveRoutes').onclick = async () => {
  const code = $('collegeCode').value.trim();
  if (!code) return alert('Enter college code first');
  const res = await fetch(`${API_BASE}/colleges/${code}/routes`, { method:'POST', headers:{ 'Content-Type':'application/json' }, body: JSON.stringify({ routes }) });
  const json = await res.json();
  $('status').textContent = `Saved ${json.routes.length} routes.`;
};

function collectTimes(stopIds) {
  const container = $('routeTimes');
  const inputs = container.querySelectorAll('input[data-stop]');
  const out = {};
  inputs.forEach(inp => { const sid = inp.dataset.stop; const v = inp.value.trim(); if (v) out[sid] = v; });
  // ensure only for current stopIds
  Object.keys(out).forEach(k => { if (!stopIds.includes(k)) delete out[k]; });
  return out;
}

$('genTimes').onclick = () => {
  const ids = $('routeStopIds').value.split(',').map(s => s.trim()).filter(Boolean);
  const container = $('routeTimes');
  container.innerHTML = '';
  if (ids.length === 0) { container.textContent = 'Enter stop IDs first'; return; }
  const table = document.createElement('div');
  ids.forEach(id => {
    const row = document.createElement('div');
    row.style.margin = '4px 0';
    row.innerHTML = `<label style="display:inline-block;width:80px;">${id}</label> <input type="time" data-stop="${id}" />`;
    table.appendChild(row);
  });
  container.appendChild(table);
};

// Buses
function renderBusesList() {
  const ul = $('busesList');
  if (!ul) return;
  ul.innerHTML = '';
  buses.forEach(b => {
    const li = document.createElement('li');
    const tokenHtml = b.driverToken ? ` &nbsp; <small>token: ${b.driverToken}</small>` : '';
    const copyBtn = ` &nbsp; <button data-busid="${b.id}" class="copyLink">Copy Driver Link</button>`;
    const openLink = b.driverToken
      ? ` &nbsp; <a target="_blank" href="/driver.html?api=${encodeURIComponent(window.location.origin)}&code=${encodeURIComponent($('collegeCode').value.trim())}&busId=${encodeURIComponent(b.id)}&token=${encodeURIComponent(b.driverToken)}">Open Driver Page</a>`
      : '';
    li.innerHTML = `${b.name} (<b>${b.id}</b>) → ${b.routeId || ''}${tokenHtml}` +
      ` &nbsp; <button data-busid="${b.id}" class="genToken">Generate Token</button>` +
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
      try { await navigator.clipboard.writeText(deepLink); $('status').textContent = 'Driver link copied'; }
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
  $('status').textContent = `Saved ${json.buses.length} buses.`;
};

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
