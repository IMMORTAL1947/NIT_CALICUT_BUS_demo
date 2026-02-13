import fs from 'fs';
import path from 'path';
import { haversineMeters } from './haversine.js';

// Load graph JSON for a given college code.
export async function loadGraphForCollege(code, baseDir) {
  // Try multiple candidate graph directories to support both repo-root and server subfolder layouts
  const candidates = [
    path.join(baseDir, 'data', 'graphs'),                              // repo root data/graphs
    path.resolve(path.dirname(new URL(import.meta.url).pathname), '..', '..', 'data', 'graphs'), // server/data/graphs relative to src/routing
    path.resolve(path.dirname(new URL(import.meta.url).pathname), '..', 'data', 'graphs')        // /app/src -> /app/data/graphs in container
  ];

  let filePath = null;
  const codeStr = String(code || '').trim();
  const namesToTry = new Set([
    `${codeStr}.json`,
    `${codeStr.toLowerCase()}.json`,
    `${codeStr.toUpperCase()}.json`
  ]);

  for (const dir of candidates) {
    for (const name of namesToTry) {
      const p = path.join(dir, name);
      if (fs.existsSync(p)) { filePath = p; break; }
    }
    if (filePath) break;

    // Case-insensitive scan for Linux deployments where file casing differs from code casing
    if (fs.existsSync(dir)) {
      const matched = fs.readdirSync(dir).find(f => f.toLowerCase() === `${codeStr.toLowerCase()}.json`);
      if (matched) {
        filePath = path.join(dir, matched);
        break;
      }
    }
  }

  if (filePath && fs.existsSync(filePath)) {
    const json = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    validateGraph(json);
    return json;
  }

  throw new Error(`Graph file not found for college '${codeStr}'. Expected in one of: ${candidates.join(', ')}`);
}

function validateGraph(graph) {
  if (!graph || !Array.isArray(graph.nodes) || !Array.isArray(graph.edges)) {
    throw new Error('Invalid graph: expected nodes[] and edges[]');
  }
  const nodeIds = new Set(graph.nodes.map(n => n.id));
  const nodeById = new Map(graph.nodes.map(n => [n.id, n]));
  for (const e of graph.edges) {
    if (!nodeIds.has(e.from) || !nodeIds.has(e.to)) {
      throw new Error(`Invalid edge: ${e.from} -> ${e.to} references missing node`);
    }
    // Auto-compute distance/time if missing using node coordinates
    if (typeof e.distance !== 'number' || !(e.distance > 0)) {
      const a = nodeById.get(e.from);
      const b = nodeById.get(e.to);
      if (a && b && typeof a.lat === 'number' && typeof a.lng === 'number' && typeof b.lat === 'number' && typeof b.lng === 'number') {
        e.distance = haversineMeters({ lat: a.lat, lng: a.lng }, { lat: b.lat, lng: b.lng });
      } else {
        e.distance = 0; // fallback
      }
    }
    if (typeof e.time !== 'number' || !(e.time > 0)) {
      const walkMps = (graph.meta && graph.meta.avgWalkSpeedMps) ? graph.meta.avgWalkSpeedMps : 1.4;
      e.time = e.distance / walkMps;
    }
  }
}

