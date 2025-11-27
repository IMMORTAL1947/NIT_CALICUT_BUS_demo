import express from 'express';
import cors from 'cors';
import { nanoid } from 'nanoid';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const app = express();
app.use(cors());
app.use(express.json({ limit: '2mb' }));

// Resolve paths robustly across layouts:
// Local (repo root /server/src -> need '..','..') and Docker build (WORKDIR /app with src directly under it -> need '..').
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename); // e.g. /app/src or <repo>/server/src

function firstExisting(paths) {
  for (const p of paths) {
    if (fs.existsSync(p)) return p;
  }
  return null;
}

// Candidate project roots
const ROOT_DIR = firstExisting([
  path.resolve(__dirname, '..', '..'), // repo root when running from server/src locally
  path.resolve(__dirname, '..')        // container root when server contents copied directly
]) || path.resolve(__dirname, '..');

// Determine admin dashboard directory (may be absent in some Docker builds)
const ADMIN_DIR = firstExisting([
  path.join(ROOT_DIR, 'admin-dashboard'),                 // repo root placement
  path.resolve(__dirname, '..', 'admin-dashboard'),       // /app/admin-dashboard
  path.resolve(__dirname, 'admin-dashboard')              // server/admin-dashboard when build context = server
]) || null;

const DATA_DIR = firstExisting([
  path.resolve(__dirname, '..', 'data'),            // local layout
  path.join(ROOT_DIR, 'data')                       // container root if data copied there
]) || path.resolve(__dirname, '..', 'data');
const DATA_FILE = path.join(DATA_DIR, 'data.json');
fs.mkdirSync(DATA_DIR, { recursive: true });
if (!fs.existsSync(DATA_FILE)) {
  fs.writeFileSync(DATA_FILE, JSON.stringify({ colleges: {} }, null, 2));
}

function readDb() {
  return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
}
function writeDb(db) {
  fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2));
}

// Create or update college meta
app.post('/api/colleges', (req, res) => {
  const { code, name } = req.body;
  if (!code) return res.status(400).json({ error: 'code required' });
  const db = readDb();
  db.colleges[code] = db.colleges[code] || { code, name: name || code, stops: [], routes: [], buses: [] };
  if (name) db.colleges[code].name = name;
  writeDb(db);
  res.json(db.colleges[code]);
});

// List colleges (for search/autocomplete)
app.get('/api/colleges', (req, res) => {
  const db = readDb();
  const list = Object.values(db.colleges).map(c => ({ code: c.code, name: c.name }));
  const q = (req.query.q || '').toString().trim().toLowerCase();
  const filtered = q ? list.filter(c => c.code.toLowerCase().includes(q) || (c.name || '').toLowerCase().includes(q)) : list;
  res.json(filtered);
});

// Upsert stops
app.post('/api/colleges/:code/stops', (req, res) => {
  const code = req.params.code;
  const { stops } = req.body; // [{id?, name, lat, lng}]
  const db = readDb();
  const college = db.colleges[code];
  if (!college) return res.status(404).json({ error: 'college not found' });
  const withIds = (stops || []).map(s => ({ id: s.id || nanoid(8), ...s }));
  college.stops = withIds;
  writeDb(db);
  res.json({ stops: college.stops });
});

// Upsert routes (supports optional stopTimes per route)
app.post('/api/colleges/:code/routes', (req, res) => {
  const code = req.params.code;
  const { routes } = req.body; // [{id?, name, color, stopIds: [], stopTimes?: { [stopId]: HH:mm }}]
  const db = readDb();
  const college = db.colleges[code];
  if (!college) return res.status(404).json({ error: 'college not found' });
  const withIds = (routes || []).map(r => ({
    id: r.id || nanoid(8),
    name: r.name,
    color: r.color || '#2196f3',
    stopIds: r.stopIds || [],
    stopTimes: r.stopTimes || {}
  }));
  college.routes = withIds;
  writeDb(db);
  res.json({ routes: college.routes });
});

// Upsert buses
app.post('/api/colleges/:code/buses', (req, res) => {
  const code = req.params.code;
  const { buses } = req.body; // [{id?, name, routeId}]
  const db = readDb();
  const college = db.colleges[code];
  if (!college) return res.status(404).json({ error: 'college not found' });
  const withIds = (buses || []).map(b => ({ id: b.id || nanoid(8), name: b.name, routeId: b.routeId }));
  college.buses = withIds;
  writeDb(db);
  res.json({ buses: college.buses });
});

// Read config for app
app.get('/api/colleges/:code/config', (req, res) => {
  const db = readDb();
  const college = db.colleges[req.params.code];
  if (!college) return res.status(404).json({ error: 'college not found' });
  res.json(college);
});

// Serve Admin Dashboard statically at root if available
if (ADMIN_DIR && fs.existsSync(ADMIN_DIR)) {
  app.use(express.static(ADMIN_DIR));
}

// Root helper
app.get('/', (req, res) => {
  if (ADMIN_DIR && fs.existsSync(path.join(ADMIN_DIR, 'index.html'))) {
    return res.sendFile(path.join(ADMIN_DIR, 'index.html'));
  }
  return res.status(200).json({
    message: 'Admin dashboard not bundled in this deployment.',
    hint: 'Include admin-dashboard/ in build context or switch Render root directory to repo root if you want the UI.'
  });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Server listening on http://localhost:${PORT}`));
