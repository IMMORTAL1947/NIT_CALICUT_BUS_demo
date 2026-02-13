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
  const result = algo === 'astar'
    ? runAStar(graph, startId, goalId, mode, options)
    : runDijkstra(graph, startId, goalId, mode, options);
  const tMs = Date.now() - t0;

  let pathIds = result.pathNodeIds;
  if (!pathIds || pathIds.length === 0) {
    return { error: 'no path found between user and selected stop' };
  }

  const nodes = pathIds.map(id => {
    const n = graph.nodes.find(x => x.id === id);
    return { id, lat: n?.lat, lng: n?.lng, name: n?.name };
  });

  const totalDistance = result.totalDistance;
  const estTime = Math.round(estimatePathTimeSeconds(totalDistance, options.walkSpeedMps));
  const reason = chooseReason(mode);
  const steps = generateSteps(graph, pathIds);

  return {
    nodes,
    totalDistanceMeters: Math.round(totalDistance),
    estimatedTimeSeconds: estTime,
    totalDistance: Math.round(totalDistance),
    estimatedTime: estTime,
    reason,
    steps,
    performance: { algo, nodesVisited: result.visitedCount, timeMs: tMs }
  };
}
