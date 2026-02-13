import { computeEdgeCost } from './scoring.js';

function buildAdj(graph) {
  const adj = new Map();
  for (const n of graph.nodes) adj.set(n.id, []);
  for (const e of graph.edges) {
    if (adj.has(e.from)) adj.get(e.from).push(e);
  }
  return adj;
}

export function runDijkstra(graph, startId, goalId, mode, options = {}) {
  const adj = buildAdj(graph);
  const dist = new Map();
  const prev = new Map();
  const visited = new Set();
  const pq = new Map();

  for (const n of graph.nodes) dist.set(n.id, Infinity);
  dist.set(startId, 0);
  pq.set(startId, 0);

  let visitedCount = 0;

  while (pq.size > 0) {
    // Find min key in pq (simple, small graphs; replace with binary heap for scale)
    let u = null;
    let minVal = Infinity;
    for (const [k, v] of pq.entries()) {
      if (v < minVal) { minVal = v; u = k; }
    }
    pq.delete(u);
    if (visited.has(u)) continue;
    visited.add(u);
    visitedCount++;
    if (u === goalId) break;

    for (const e of adj.get(u) || []) {
      const v = e.to;
      if (visited.has(v)) continue;
      const w = computeEdgeCost(e, mode, options);
      const alt = dist.get(u) + w;
      if (alt < dist.get(v)) {
        dist.set(v, alt);
        prev.set(v, { id: u, edge: e });
        pq.set(v, alt);
      }
    }
  }

  // Reconstruct path
  const path = [];
  let cur = goalId;
  if (!prev.has(cur) && startId !== goalId) {
    return { pathNodeIds: [], visitedCount, totalCost: Infinity, totalDistance: 0 };
  }
  path.push(cur);
  let totalDistance = 0;
  while (prev.has(cur)) {
    const info = prev.get(cur);
    totalDistance += info.edge.distance || 0;
    cur = info.id;
    path.push(cur);
  }
  path.reverse();
  return { pathNodeIds: path, visitedCount, totalCost: dist.get(goalId), totalDistance };
}
