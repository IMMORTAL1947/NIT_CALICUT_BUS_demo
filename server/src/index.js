import express from 'express';
import cors from 'cors';
import { nanoid } from 'nanoid';
import fs from 'fs';
import path from 'path';
import https from 'https';
import { fileURLToPath } from 'url';
import { computeRoute } from './routing/index.js';

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

const DATA_DIR = process.env.DATA_DIR || (firstExisting([
  path.resolve(__dirname, '..', 'data'),            // local layout
  path.join(ROOT_DIR, 'data')                       // container root if data copied there
]) || path.resolve(__dirname, '..', 'data'));
const DATA_FILE = path.join(DATA_DIR, 'data.json');
fs.mkdirSync(DATA_DIR, { recursive: true });
if (!fs.existsSync(DATA_FILE)) {
  fs.writeFileSync(DATA_FILE, JSON.stringify({ colleges: {} }, null, 2));
}

function readDb() {
  return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
}
async function writeDb(db) {
  const jsonText = JSON.stringify(db, null, 2);
  fs.writeFileSync(DATA_FILE, jsonText);
  // Fire-and-forget GitHub sync if configured
  try {
    const gh = getGithubConfig();
    if (gh && gh.token && gh.repo) {
      attemptGithubSync(jsonText, gh);
    }
  } catch (e) {
    console.warn('GitHub sync error:', e);
  }
}

function ghRequest(method, url, bodyObj, token) {
  return new Promise((resolve, reject) => {
    const opts = new URL(url);
    const req = https.request({
      method,
      hostname: opts.hostname,
      path: opts.pathname + (opts.search || ''),
      headers: {
        'Authorization': `Bearer ${token}`,
        'User-Agent': 'campus-transit-server',
        'Accept': 'application/vnd.github+json',
        'Content-Type': 'application/json'
      }
    }, (res) => {
      let data = '';
      res.on('data', (d) => data += d);
      res.on('end', () => {
        const status = res.statusCode || 0;
        if (status >= 200 && status < 300) {
          try { resolve(JSON.parse(data || '{}')); } catch { resolve({}); }
        } else {
          reject(new Error(`GitHub ${method} ${url} failed: ${status} ${data}`));
        }
      });
    });
    req.on('error', reject);
    if (bodyObj) req.write(JSON.stringify(bodyObj));
    req.end();
  });
}

function getGithubConfig() {
  const token = process.env.DATA_GITHUB_TOKEN || process.env.GITHUB_TOKEN;
  const repo = process.env.DATA_GITHUB_REPO || process.env.GITHUB_REPO; // owner/name
  const branch = process.env.DATA_GITHUB_BRANCH || process.env.GITHUB_BRANCH || 'main';
  const filePath = process.env.DATA_GITHUB_FILE_PATH || process.env.GITHUB_FILE_PATH || 'data.json';
  if (!repo || !token) return null;
  return { token, repo, branch, filePath };
}

// Simple in-memory failure backoff to avoid log spam when token lacks permission
let githubSyncDisabled = false;
let githubSyncFailureCount = 0;
const GITHUB_SYNC_MAX_FAILURES = Number(process.env.GITHUB_SYNC_MAX_FAILURES || 6);

function attemptGithubSync(jsonText, gh) {
  if (githubSyncDisabled) return;
  commitDbToGitHub(jsonText, gh).catch((e) => {
    githubSyncFailureCount += 1;
    console.warn('GitHub sync failed:', e.message);
    // Disable further attempts if persistent failure (e.g., 403 permission)
    if (githubSyncFailureCount >= GITHUB_SYNC_MAX_FAILURES) {
      githubSyncDisabled = true;
      console.warn(`GitHub sync disabled after ${githubSyncFailureCount} consecutive failures. Set GITHUB_SYNC_MAX_FAILURES or fix token permissions to re-enable (restart).`);
    }
  });
}

async function commitDbToGitHub(jsonText, gh) {
  const base = `https://api.github.com/repos/${gh.repo}/contents/${encodeURIComponent(gh.filePath)}`;
  const getUrl = `${base}?ref=${encodeURIComponent(gh.branch)}`;
  let sha = undefined;
  try {
    const meta = await ghRequest('GET', getUrl, undefined, gh.token);
    sha = meta && meta.sha;
  } catch (e) {
    if (!/404/.test(String(e))) {
      throw e;
    }
  }
  const contentB64 = Buffer.from(jsonText, 'utf8').toString('base64');
  const body = {
    message: `chore(data): update data.json via admin dashboard`,
    content: contentB64,
    branch: gh.branch,
    sha,
    committer: {
      name: process.env.GITHUB_COMMIT_NAME || 'Campus Transit Bot',
      email: process.env.GITHUB_COMMIT_EMAIL || 'bot@example.com'
    }
  };
  await ghRequest('PUT', base, body, gh.token);
}

async function fetchDbFromGitHub(gh) {
  const url = `https://api.github.com/repos/${gh.repo}/contents/${encodeURIComponent(gh.filePath)}?ref=${encodeURIComponent(gh.branch)}`;
  try {
    const meta = await ghRequest('GET', url, undefined, gh.token);
    if (!meta || !meta.content) throw new Error('No content in GitHub response');
    const text = Buffer.from(meta.content, 'base64').toString('utf8');
    fs.writeFileSync(DATA_FILE, text);
    return true;
  } catch (e) {
    // If not found, optionally bootstrap an initial file in the data repo
    if (/404/.test(String(e))) {
      const shouldBootstrap = String(process.env.DATA_BOOTSTRAP_REPO || 'false').toLowerCase() === 'true';
      if (shouldBootstrap) {
        const initial = JSON.stringify({ colleges: {} }, null, 2);
        await commitDbToGitHub(initial, gh);
        fs.writeFileSync(DATA_FILE, initial);
        return true;
      }
      // No bootstrap: leave local file as-is and return false
      return false;
    }
    throw e;
  }
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
  const { buses } = req.body; // [{id?, name, routeId, driverToken?}]
  const db = readDb();
  const college = db.colleges[code];
  if (!college) return res.status(404).json({ error: 'college not found' });
  const existing = college.buses || [];
  const withIds = (buses || []).map(b => {
    const id = b.id || nanoid(8);
    const prev = existing.find(x => x.id === id) || {};
    return { id, name: b.name, routeId: b.routeId, driverToken: b.driverToken || prev.driverToken };
  });
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

// ===== Routing to bus stop =====
function parseTimeToTodaySeconds(hm) {
  // hm = 'HH:mm'
  if (!hm || typeof hm !== 'string') return null;
  const [hStr, mStr] = hm.split(':');
  const h = Number(hStr); const m = Number(mStr);
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 3600 + m * 60;
}

function estimateBusEtaSeconds(college, stopId) {
  // Very simple schedule-based ETA: find earliest upcoming stop time today among all routes that include stopId
  try {
    const now = new Date();
    const nowSec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
    let bestDelta = null;
    for (const r of (college.routes || [])) {
      const hm = r.stopTimes && r.stopTimes[stopId];
      const sec = parseTimeToTodaySeconds(hm);
      if (sec == null) continue;
      let delta = sec - nowSec;
      if (delta < 0) delta += 24 * 3600; // wrap to next day
      if (bestDelta == null || delta < bestDelta) bestDelta = delta;
    }
    return bestDelta == null ? undefined : bestDelta;
  } catch {
    return undefined;
  }
}

// GET /api/colleges/:code/route-to-stop
// Query: userLat, userLng, stopId, mode=shortest|fastest|accessible, algo=dijkstra|astar, optional busEtaSeconds
app.get('/api/colleges/:code/route-to-stop', async (req, res) => {
  try {
    const code = req.params.code;
    const { userLat, userLng, stopId } = req.query;
    let { mode, algo } = req.query;
    mode = (mode || 'shortest').toString();
    algo = (algo || 'dijkstra').toString();
    const busEtaSeconds = req.query.busEtaSeconds ? Number(req.query.busEtaSeconds) : undefined;

    if (!userLat || !userLng || !stopId) {
      return res.status(400).json({ error: 'userLat, userLng, stopId are required' });
    }
    const db = readDb();
    const college = db.colleges[code];
    if (!college) return res.status(404).json({ error: 'college not found' });

    const eta = typeof busEtaSeconds === 'number' ? busEtaSeconds : estimateBusEtaSeconds(college, String(stopId));
    // Try to resolve stop coordinates from college config if not present in graph directly
    let stopLat, stopLng;
    try {
      const stopMeta = (college.stops || []).find(s => String(s.id) === String(stopId));
      if (stopMeta) { stopLat = Number(stopMeta.lat); stopLng = Number(stopMeta.lng); }
    } catch {}
    // Temporary override: when using Dijkstra, force a fixed origin point
    const algoLower = (algo || '').toLowerCase();
    const useFixedOriginForDijkstra = algoLower === 'dijkstra';
    const effectiveUserLat = useFixedOriginForDijkstra ? 11.321071 : Number(userLat);
    const effectiveUserLng = useFixedOriginForDijkstra ? 75.934531 : Number(userLng);

    const result = await computeRoute({
      baseDir: ROOT_DIR,
      collegeCode: code,
      userLat: effectiveUserLat,
      userLng: effectiveUserLng,
      stopId: String(stopId),
      stopLat,
      stopLng,
      mode,
      algo,
      busEtaSeconds: eta
    });
    if (result && result.error) return res.status(400).json(result);
    res.json(result);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
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

// ===== Live tracking state =====
const liveState = {}; // { [collegeCode]: { [busId]: { lat, lng, speed, heading, ts } } }

function setLiveLocation(code, busId, payload) {
  liveState[code] = liveState[code] || {};
  liveState[code][busId] = payload;
}

// Generate or rotate driver token for a bus
app.post('/api/colleges/:code/buses/:busId/driver-token', (req, res) => {
  const code = req.params.code; const busId = req.params.busId;
  const db = readDb();
  const college = db.colleges[code];
  if (!college) return res.status(404).json({ error: 'college not found' });
  const idx = (college.buses || []).findIndex(b => b.id === busId);
  if (idx < 0) return res.status(404).json({ error: 'bus not found' });
  const token = nanoid(24);
  college.buses[idx].driverToken = token;
  writeDb(db);
  const apiBase = `${req.protocol}://${req.get('host')}`;
  const deepLink = `campus-transit://driver?code=${encodeURIComponent(code)}&busId=${encodeURIComponent(busId)}&token=${encodeURIComponent(token)}&api=${encodeURIComponent(apiBase)}`;
  res.json({ busId, driverToken: token, deepLink });
});

// Driver publishes location
app.post('/api/colleges/:code/live/buses/:busId/loc', (req, res) => {
  const code = req.params.code; const busId = req.params.busId;
  const { lat, lng, speed, heading, ts } = req.body || {};
  if (typeof lat !== 'number' || typeof lng !== 'number') return res.status(400).json({ error: 'lat,lng required' });
  const db = readDb();
  const college = db.colleges[code];
  if (!college) return res.status(404).json({ error: 'college not found' });
  const bus = (college.buses || []).find(b => b.id === busId);
  if (!bus) return res.status(404).json({ error: 'bus not found' });
  const token = req.headers['x-driver-token'] || req.headers['X-Driver-Token'] || req.headers['x-driver-token'];
  if (!bus.driverToken || !token || token !== bus.driverToken) return res.status(401).json({ error: 'unauthorized' });
  const payload = { lat, lng, speed: speed || null, heading: heading || null, ts: ts || Date.now() };
  setLiveLocation(code, busId, payload);
  res.json({ ok: true });
});

// Riders fetch live bus locations (polling)
app.get('/api/colleges/:code/live', (req, res) => {
  const code = req.params.code;
  const state = liveState[code] || {};
  const buses = Object.entries(state).map(([busId, loc]) => ({ busId, ...loc }));
  res.json({ buses });
});

const PORT = process.env.PORT || 3000;
// Optionally sync data.json from GitHub data repo on startup
(async () => {
  try {
    const gh = getGithubConfig();
    const shouldSync = String(process.env.DATA_SYNC_ON_START || 'true').toLowerCase() === 'true';
    if (gh && shouldSync) {
      const ok = await fetchDbFromGitHub(gh);
      if (ok) {
        console.log('Data synced from GitHub on startup');
      } else {
        console.log('Data repo missing file; startup sync skipped');
      }
    }
  } catch (e) {
    console.warn('Startup data sync failed:', e.message);
  } finally {
    app.listen(PORT, () => console.log(`Server listening on http://localhost:${PORT}`));
  }
})();

// Optional admin endpoint to trigger sync from GitHub 
app.post('/api/admin/sync-from-github', async (req, res) => {
  const token = process.env.ADMIN_TOKEN;
  if (token && req.headers['x-admin-token'] !== token) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  const gh = getGithubConfig();
  if (!gh) return res.status(400).json({ error: 'GitHub data repo not configured' });
  try {
    await fetchDbFromGitHub(gh);
    return res.json({ ok: true });
  } catch (e) {
    return res.status(500).json({ error: e.message });
  }
});
