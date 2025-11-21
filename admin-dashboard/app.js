const API_BASE = ((new URLSearchParams(location.search).get('api')) || 'http://localhost:3000').replace(/\/$/, '') + '/api';

const map = L.map('map').setView([11.3215, 75.9342], 15);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap'
}).addTo(map);

let markers = [];
let stops = [];
let routes = [];

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
    li.textContent = `${r.name} (${r.id}) - ${r.stopIds.join(' -> ')}`;
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
