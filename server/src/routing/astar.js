import { computeEdgeCost } from './scoring.js';
import { haversineMeters } from './haversine.js';

function buildAdj(graph) {
  const adj = new Map();
  for (const n of graph.nodes) adj.set(n.id, []);
  for (const e of graph.edges) {
    if (adj.has(e.from)) adj.get(e.from).push(e);
  }
  return adj;
}

function nodeById(graph, id) {
  return graph.nodes.find(n => n.id === id);
}

function heuristic(graph, nodeId, goalId, mode, options) {
  const a = nodeById(graph, nodeId);
  const b = nodeById(graph, goalId);
  if (!a || !b) return 0;
  const d = haversineMeters({ lat: a.lat, lng: a.lng }, { lat: b.lat, lng: b.lng });
  if (mode === 'fastest') {
    const speed = options.walkSpeedMps || 1.4;
    return d / speed; // seconds estimate
  }
  return d; // meters
}

export function runAStar(graph, startId, goalId, mode, options = {}) {
  const adj = buildAdj(graph);
  const open = new Set([startId]);
  const cameFrom = new Map(); // v -> {id:u, edge}
  const gScore = new Map();   // cost from start
  const fScore = new Map();   // g + h

  for (const n of graph.nodes) { gScore.set(n.id, Infinity); fScore.set(n.id, Infinity); }
  gScore.set(startId, 0);
  fScore.set(startId, heuristic(graph, startId, goalId, mode, options));

  let visitedCount = 0;

  while (open.size > 0) {
    // Select node in open with lowest fScore
    let current = null; let minF = Infinity;
    for (const n of open) {
      const f = fScore.get(n);
      if (f < minF) { minF = f; current = n; }
    }
    if (current === goalId) {
      // reconstruct
      const path = [];
      let cur = current;
      let totalDistance = 0;
      path.push(cur);
      while (cameFrom.has(cur)) {
        const info = cameFrom.get(cur);
        totalDistance += info.edge.distance || 0;
        cur = info.id;
        path.push(cur);
      }
      path.reverse();
      return { pathNodeIds: path, visitedCount, totalCost: gScore.get(goalId), totalDistance };
    }

    open.delete(current);
    visitedCount++;
    for (const e of adj.get(current) || []) {
      const neighbor = e.to;
      const tentativeG = gScore.get(current) + computeEdgeCost(e, mode, options);
      if (tentativeG < gScore.get(neighbor)) {
        cameFrom.set(neighbor, { id: current, edge: e });
        gScore.set(neighbor, tentativeG);
        const f = tentativeG + heuristic(graph, neighbor, goalId, mode, options);
        fScore.set(neighbor, f);
        open.add(neighbor);
      }
    }
  }
  return { pathNodeIds: [], visitedCount, totalCost: Infinity, totalDistance: 0 };
}
