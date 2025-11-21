import express from 'express';
import cors from 'cors';
import { nanoid } from 'nanoid';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const app = express();
app.use(cors());
app.use(express.json({ limit: '2mb' }));

// Resolve paths robustly regardless of where node is launched from
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename); // .../server/src
const ROOT_DIR = path.resolve(__dirname, '..', '..'); // project root
const ADMIN_DIR = path.join(ROOT_DIR, 'admin-dashboard');
const DATA_DIR = path.resolve(__dirname, '..', 'data');
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

// Upsert routes
app.post('/api/colleges/:code/routes', (req, res) => {
  const code = req.params.code;
  const { routes } = req.body; // [{id?, name, color, stopIds: []}]
  const db = readDb();
  const college = db.colleges[code];
  if (!college) return res.status(404).json({ error: 'college not found' });
  const withIds = (routes || []).map(r => ({ id: r.id || nanoid(8), name: r.name, color: r.color || '#2196f3', stopIds: r.stopIds || [] }));
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

// Serve Admin Dashboard statically at root
if (fs.existsSync(ADMIN_DIR)) {
  app.use(express.static(ADMIN_DIR));
}

// Root helper
app.get('/', (req, res) => {
  const idx = path.join(ADMIN_DIR, 'index.html');
  if (fs.existsSync(idx)) return res.sendFile(idx);
  return res
    .status(200)
    .send('Admin Dashboard not found. Ensure admin-dashboard/ exists at project root.');
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Server listening on http://localhost:${PORT}`));
