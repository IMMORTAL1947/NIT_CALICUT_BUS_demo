/**
 * GeoJSON → Graph converter for campus routing
 *
 * Usage:
 *   node scripts/geojsonToGraph.js <geojsonPath> [--code=<collegeCode>] [--name=<College Name>]
 *
 * Requirements implemented:
 * - Parses GeoJSON (FeatureCollection with LineString features) of walkable paths
 * - Converts coordinates to nodes and directed edges (both directions)
 * - Auto-generates stable node IDs from coordinate values (rounded)
 * - Computes edge distances via Haversine
 * - Assigns basic metadata defaults:
 *     slopePenalty=0, stairsPenalty=0, accessibilityCost=0, shadedPreference=0
 * - Snaps bus stops from college config (server/data/data.json) to nearest node (<15m)
 *   and adds a connecting edge (both directions)
 * - Validates non-empty graph and bus stop connectivity
 * - Writes backend-compatible graph JSON to server/data/graphs/<college_code>.json
 * - Prints summary: total nodes, total edges, snapped bus stops
 */

import { readFile, writeFile, mkdir, access } from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

// ---------- Utilities ----------
function toRad(deg) {
  return (deg * Math.PI) / 180;
}

function haversineMeters(a, b) {
  const R = 6371000; // meters
  const dLat = toRad(b.lat - a.lat);
  const dLon = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
  return R * c;
}

function roundCoord(value, decimals = 6) {
  const factor = Math.pow(10, decimals);
  return Math.round(value * factor) / factor;
}

function coordKey(lat, lng, decimals = 6) {
  const latR = roundCoord(lat, decimals).toFixed(decimals);
  const lngR = roundCoord(lng, decimals).toFixed(decimals);
  return `${latR},${lngR}`;
}

function nodeIdFromCoord(lat, lng, decimals = 6) {
  const latR = roundCoord(lat, decimals).toFixed(decimals);
  const lngR = roundCoord(lng, decimals).toFixed(decimals);
  // Stable, readable ID; avoid special chars
  return `n_${latR.replace(/\./g, 'p').replace(/-/g, 'm')}_${lngR.replace(/\./g, 'p').replace(/-/g, 'm')}`;
}

function parseArgs(argv) {
  const args = { file: null, code: 'nitc', name: null };
  const positional = [];
  for (const a of argv.slice(2)) {
    if (a.startsWith('--code=')) args.code = a.split('=')[1];
    else if (a.startsWith('--name=')) args.name = a.split('=')[1];
    else positional.push(a);
  }
  if (positional[0]) args.file = positional[0];
  if (!args.name) args.name = args.code;
  return args;
}

async function fileExists(p) {
  try { await access(p); return true; } catch { return false; }
}

// Brute-force nearest node finder (adequate for moderate graphs)
function findNearestNode(nodesArray, lat, lng) {
  let best = null; let bestDist = Infinity;
  for (const n of nodesArray) {
    const d = haversineMeters({ lat, lng }, { lat: n.lat, lng: n.lng });
    if (d < bestDist) { bestDist = d; best = n; }
  }
  return { node: best, distance: bestDist };
}

// ---------- Core conversion ----------
async function loadGeoJSON(geojsonPath) {
  const text = await readFile(geojsonPath, 'utf8');
  let json;
  try { json = JSON.parse(text); } catch (e) { throw new Error(`Invalid JSON: ${e.message}`); }
  if (!json || json.type !== 'FeatureCollection' || !Array.isArray(json.features)) {
    throw new Error('Expected a GeoJSON FeatureCollection with features[]');
  }
  return json;
}

function addNode(nodeMap, nodesArray, lat, lng) {
  const key = coordKey(lat, lng);
  if (!nodeMap.has(key)) {
    const id = nodeIdFromCoord(lat, lng);
    const node = { id, lat: roundCoord(lat), lng: roundCoord(lng) };
    nodeMap.set(key, node);
    nodesArray.push(node);
  }
  return nodeMap.get(key);
}

function addDirectedEdge(edgesArray, fromId, toId, distanceMeters) {
  edgesArray.push({
    from: fromId,
    to: toId,
    distance: Math.round(distanceMeters),
    slopePenalty: 0,
    stairsPenalty: 0,
    accessibilityCost: 0,
    shadedPreference: 0
  });
}

function convertGeoJSONToGraph(geojson) {
  const nodeMap = new Map(); // key -> node
  const nodesArray = [];     // [{id, lat, lng}]
  const edgesArray = [];     // directed edges

  for (const feat of geojson.features) {
    if (!feat || feat.type !== 'Feature') continue;
    const geom = feat.geometry;
    if (!geom) continue;
    if (geom.type === 'LineString' && Array.isArray(geom.coordinates)) {
      const coords = geom.coordinates; // [ [lng, lat], ... ]
      for (let i = 0; i < coords.length; i++) {
        const [lng, lat] = coords[i];
        addNode(nodeMap, nodesArray, lat, lng);
        if (i > 0) {
          const [prevLng, prevLat] = coords[i - 1];
          const a = addNode(nodeMap, nodesArray, prevLat, prevLng);
          const b = addNode(nodeMap, nodesArray, lat, lng);
          const dist = haversineMeters({ lat: a.lat, lng: a.lng }, { lat: b.lat, lng: b.lng });
          // Add directed edges both ways
          addDirectedEdge(edgesArray, a.id, b.id, dist);
          addDirectedEdge(edgesArray, b.id, a.id, dist);
        }
      }
    } else if (geom.type === 'MultiLineString' && Array.isArray(geom.coordinates)) {
      for (const line of geom.coordinates) {
        for (let i = 0; i < line.length; i++) {
          const [lng, lat] = line[i];
          addNode(nodeMap, nodesArray, lat, lng);
          if (i > 0) {
            const [prevLng, prevLat] = line[i - 1];
            const a = addNode(nodeMap, nodesArray, prevLat, prevLng);
            const b = addNode(nodeMap, nodesArray, lat, lng);
            const dist = haversineMeters({ lat: a.lat, lng: a.lng }, { lat: b.lat, lng: b.lng });
            addDirectedEdge(edgesArray, a.id, b.id, dist);
            addDirectedEdge(edgesArray, b.id, a.id, dist);
          }
        }
      }
    }
    // Other geometry types (Point, Polygon) are ignored for path building
  }

  return { nodesArray, edgesArray };
}

// ---------- Bus stops snapping ----------
async function readCollegeConfig(rootDir, code) {
  const dataFile = path.join(rootDir, 'server', 'data', 'data.json');
  const text = await readFile(dataFile, 'utf8');
  const db = JSON.parse(text);
  const college = db.colleges && db.colleges[code];
  if (!college) throw new Error(`College config not found for code '${code}' in data.json`);
  const stops = Array.isArray(college.stops) ? college.stops : [];
  return { college, stops };
}

function snapStopsToGraph(stops, nodesArray, edgesArray, toleranceMeters = 70) {
  let snappedCount = 0;
  const stopNodeIds = [];

  for (const s of stops) {
    if (typeof s.lat !== 'number' || typeof s.lng !== 'number') continue;
    const nearest = findNearestNode(nodesArray, s.lat, s.lng);
    if (!nearest.node) continue;
    if (nearest.distance <= toleranceMeters+1) {
      // Create a node for the bus stop and connect to nearest node
      const stopId = `stop_${s.id || s.name || 'unknown'}`;
      const stopNode = { id: stopId, lat: roundCoord(s.lat), lng: roundCoord(s.lng), name: s.name };
      // Add stop node if not already present
      if (!nodesArray.find(n => n.id === stopId)) nodesArray.push(stopNode);
      // Connect both ways
      addDirectedEdge(edgesArray, stopNode.id, nearest.node.id, nearest.distance);
      addDirectedEdge(edgesArray, nearest.node.id, stopNode.id, nearest.distance);
      snappedCount += 1;
      stopNodeIds.push(stopNode.id);
    } else {
      console.warn(`Warn: bus stop '${s.name || s.id}' is ${Math.round(nearest.distance)}m from nearest path (> ${toleranceMeters}m); not snapped.`);
    }
  }

  return { snappedCount, stopNodeIds };
}

// ---------- Validation ----------
function validateGraph(nodesArray, edgesArray, stopNodeIds) {
  const errors = [];
  if (!nodesArray.length || !edgesArray.length) {
    errors.push('Graph is empty: no nodes or edges');
  }
  // Build adjacency from edges
  const adj = new Map();
  for (const n of nodesArray) adj.set(n.id, []);
  for (const e of edgesArray) {
    if (adj.has(e.from)) adj.get(e.from).push(e.to);
  }
  // Simple connectivity: each stop must have at least one outgoing edge
  for (const sid of stopNodeIds) {
    const outs = adj.get(sid) || [];
    if (!outs.length) errors.push(`Stop '${sid}' has no connecting edges`);
  }
  return errors;
}

// ---------- Main ----------
async function main() {
  const { file, code, name } = parseArgs(process.argv);
  if (!file) {
    console.error('Usage: node scripts/geojsonToGraph.js <geojsonPath> [--code=<collegeCode>] [--name=<College Name>]');
    process.exit(1);
  }

  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);
  const repoRoot = path.resolve(__dirname, '..');
  const geojsonPath = path.isAbsolute(file) ? file : path.join(repoRoot, file);
  const graphsDir = path.join(repoRoot, 'server', 'data', 'graphs');
  const outFile = path.join(graphsDir, `${code}.json`);

  // Load inputs
  const geojson = await loadGeoJSON(geojsonPath);
  const { nodesArray, edgesArray } = convertGeoJSONToGraph(geojson);
  let stopsInfo = { college: null, stops: [] };
  try { stopsInfo = await readCollegeConfig(repoRoot, code); } catch (e) { console.warn(e.message); }

  // Snap bus stops
  const { snappedCount, stopNodeIds } = snapStopsToGraph(stopsInfo.stops, nodesArray, edgesArray, 70);

  // Validate
  const errors = validateGraph(nodesArray, edgesArray, stopNodeIds);
  if (errors.length) {
    console.warn('Validation warnings/errors:');
    for (const err of errors) console.warn(` - ${err}`);
  }

  // Prepare backend-compatible output (array format like nitc.json)
  const graphJson = {
    meta: { code, name, avgWalkSpeedMps: 1.4 },
    nodes: nodesArray.map(n => ({ id: n.id, name: n.name || n.id, lat: n.lat, lng: n.lng, type: n.id.startsWith('stop_') ? 'bus_stop' : 'waypoint' })),
    edges: edgesArray
  };

  // Write output
  if (!(await fileExists(graphsDir))) await mkdir(graphsDir, { recursive: true });
  await writeFile(outFile, JSON.stringify(graphJson, null, 2), 'utf8');

  // Summary
  console.log('Graph build summary:');
  console.log(` - College code: ${code}`);
  console.log(` - Total nodes: ${nodesArray.length}`);
  console.log(` - Total edges: ${edgesArray.length}`);
  console.log(` - Bus stops snapped: ${snappedCount}`);
  console.log(` - Output: ${outFile}`);
}

main().catch((e) => {
  console.error('Error:', e.message);
  process.exit(1);
});
