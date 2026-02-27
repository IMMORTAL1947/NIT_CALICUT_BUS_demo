import { loadGraphForCollege } from './graphLoader.js';
import { runDijkstra } from './dijkstra.js';
import { runAStar } from './astar.js';
import { haversineMeters } from './haversine.js';
import { estimatePathTimeSeconds, chooseReason } from './scoring.js';
import { generateSteps } from './steps.js';

function nearestNodeId(graph, lat, lng) {
  let best = null; let bestDist = Infinity;
  for (const n of graph.nodes) {
    const d = haversineMeters({ lat, lng }, { lat: n.lat, lng: n.lng });
    if (d < bestDist) { bestDist = d; best = n.id; }
  }
  return best;
}

function nearestNodeCandidates(graph, lat, lng, limit = 8) {
  const ranked = [];
  for (const n of graph.nodes) {
    const d = haversineMeters({ lat, lng }, { lat: n.lat, lng: n.lng });
    ranked.push({ id: n.id, dist: d });
  }
  ranked.sort((a, b) => a.dist - b.dist);
  return ranked.slice(0, limit).map(x => x.id);
}

function buildUndirectedAdj(graph) {
  const adj = new Map();
  for (const n of graph.nodes) adj.set(n.id, []);
  for (const e of graph.edges) {
    if (adj.has(e.from) && adj.has(e.to)) {
      adj.get(e.from).push(e.to);
      adj.get(e.to).push(e.from);
    }
  }
  return adj;
}

function connectedComponentNodes(graph, seedId) {
  if (!seedId) return new Set();
  const adj = buildUndirectedAdj(graph);
  const seen = new Set([seedId]);
  const q = [seedId];
  while (q.length) {
    const u = q.shift();
    for (const v of (adj.get(u) || [])) {
      if (!seen.has(v)) {
        seen.add(v);
        q.push(v);
      }
    }
  }
  return seen;
}

function nearestInSet(graph, lat, lng, allowedIds) {
  let best = null;
  let bestDist = Infinity;
  for (const n of graph.nodes) {
    if (!allowedIds.has(n.id)) continue;
    const d = haversineMeters({ lat, lng }, { lat: n.lat, lng: n.lng });
    if (d < bestDist) {
      bestDist = d;
      best = n.id;
    }
  }
  return best;
}

function findStopNode(graph, stopId) {
  const sid = String(stopId);
  const numMatch = sid.match(/^s(\d+)$/);
  const nameCandidate = numMatch ? `Stop ${numMatch[1]}` : null;
  const idsToTry = [sid, `stop_${sid}`, `bus_stop_${sid}`];
  return graph.nodes.find(n => (
    (n.id && idsToTry.includes(n.id)) ||
    (n.stopId && idsToTry.includes(n.stopId)) ||
    (nameCandidate && n.name === nameCandidate) ||
    (n.type === 'bus_stop' && (n.id && idsToTry.includes(n.id) || n.name === sid || (nameCandidate && n.name === nameCandidate)))
  ));
}

export async function computeRoute({ baseDir, collegeCode, userLat, userLng, stopId, stopLat, stopLng, mode = 'shortest', algo = 'dijkstra', busEtaSeconds }) {
  const graph = await loadGraphForCollege(collegeCode, baseDir);
  const nodeMap = new Map(graph.nodes.map(n => [n.id, n]));
  const startId = nearestNodeId(graph, Number(userLat), Number(userLng));
  let stopNode = findStopNode(graph, String(stopId));
  let goalId = stopNode?.id;
  if (!goalId && typeof stopLat === 'number' && typeof stopLng === 'number') {
    goalId = nearestNodeId(graph, Number(stopLat), Number(stopLng));
  }
  if (!startId || !goalId) {
    return { error: 'start or stop not found in graph' };
  }

  const options = {
    walkSpeedMps: (graph.meta?.avgWalkSpeedMps) || 1.4,
    busEtaSec: typeof busEtaSeconds === 'number' ? busEtaSeconds : undefined
  };

  const t0 = Date.now();
  const startCandidates = nearestNodeCandidates(graph, Number(userLat), Number(userLng), 10);
  const goalCandidates = (() => {
    const ids = [];
    if (stopNode?.id) ids.push(stopNode.id);
    if (typeof stopLat === 'number' && typeof stopLng === 'number') {
      ids.push(...nearestNodeCandidates(graph, Number(stopLat), Number(stopLng), 10));
    }
    if (goalId) ids.push(goalId);
    return Array.from(new Set(ids));
  })();

  let bestResult = null;
  let bestGoalId = null;
  let pathIds = null;
  const startTry = [startId, ...startCandidates].filter(Boolean);
  const goalTry = [goalId, ...goalCandidates].filter(Boolean);

  outer:
  for (const sId of startTry) {
    for (const gId of goalTry) {
      const result = algo === 'astar'
        ? runAStar(graph, sId, gId, mode, options)
        : runDijkstra(graph, sId, gId, mode, options);
      if (result.pathNodeIds && result.pathNodeIds.length > 0) {
        const goalNode = nodeMap.get(gId);
        const connectorDist = (typeof stopLat === 'number' && typeof stopLng === 'number' && goalNode)
          ? haversineMeters({ lat: goalNode.lat, lng: goalNode.lng }, { lat: Number(stopLat), lng: Number(stopLng) })
          : 0;
        const score = result.totalDistance + connectorDist;
        const bestGoalNode = bestGoalId ? nodeMap.get(bestGoalId) : null;
        const bestConnectorDist = (typeof stopLat === 'number' && typeof stopLng === 'number' && bestGoalNode)
          ? haversineMeters({ lat: bestGoalNode.lat, lng: bestGoalNode.lng }, { lat: Number(stopLat), lng: Number(stopLng) })
          : 0;
        const bestScore = bestResult ? (bestResult.totalDistance + bestConnectorDist) : Infinity;
        if (!bestResult || score < bestScore) {
          bestResult = result;
          bestGoalId = gId;
          pathIds = result.pathNodeIds;
        }
        if (sId === startId && gId === goalId) break outer;
      }
    }
  }
  const tMs = Date.now() - t0;

  if (!pathIds || pathIds.length === 0) {
    // Last-resort fallback: snap start to nearest node within goal component.
    const goalSeed = goalTry[0] || goalId;
    const goalComponent = connectedComponentNodes(graph, goalSeed);
    if (goalComponent.size > 0) {
      const forcedStart = nearestInSet(graph, Number(userLat), Number(userLng), goalComponent);
      if (forcedStart) {
        for (const gId of goalTry) {
          const r = algo === 'astar'
            ? runAStar(graph, forcedStart, gId, mode, options)
            : runDijkstra(graph, forcedStart, gId, mode, options);
          if (r.pathNodeIds && r.pathNodeIds.length > 0) {
            bestResult = r;
            bestGoalId = gId;
            pathIds = r.pathNodeIds;
            break;
          }
        }
      }
    }
  }

  if (!pathIds || pathIds.length === 0) {
    return { error: 'no path found between user and selected stop' };
  }

  const nodes = pathIds.map(id => {
    const n = nodeMap.get(id);
    return { id, lat: n?.lat, lng: n?.lng, name: n?.name };
  });

  let totalDistance = bestResult?.totalDistance ?? 0;
  if (typeof stopLat === 'number' && typeof stopLng === 'number' && nodes.length > 0) {
    const last = nodes[nodes.length - 1];
    const connector = haversineMeters(
      { lat: Number(last.lat), lng: Number(last.lng) },
      { lat: Number(stopLat), lng: Number(stopLng) }
    );
    if (connector > 5) {
      nodes.push({
        id: `stop_${String(stopId)}`,
        lat: Number(stopLat),
        lng: Number(stopLng),
        name: `Stop ${String(stopId)}`
      });
      totalDistance += connector;
    }
  }
  const estTime = Math.round(estimatePathTimeSeconds(totalDistance, options.walkSpeedMps));
  const reason = chooseReason(mode);
  const steps = generateSteps(graph, pathIds);
  if (nodes.length >= 2) {
    const prev = nodes[nodes.length - 2];
    const last = nodes[nodes.length - 1];
    if (String(last.id).startsWith('stop_')) {
      const connector = haversineMeters(
        { lat: Number(prev.lat), lng: Number(prev.lng) },
        { lat: Number(last.lat), lng: Number(last.lng) }
      );
      steps.push(`Walk from ${prev.name || prev.id} to selected stop (~${Math.round(connector)} m)`);
    }
  }

  return {
    nodes,
    totalDistanceMeters: Math.round(totalDistance),
    estimatedTimeSeconds: estTime,
    totalDistance: Math.round(totalDistance),
    estimatedTime: estTime,
    reason,
    steps,
    performance: { algo, nodesVisited: bestResult?.visitedCount ?? 0, timeMs: tMs }
  };
}
