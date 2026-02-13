// Minimal test runner to compare Dijkstra vs A* on sample graph
import { exampleGraph } from './sampleGraph.js';
import { runDijkstra } from './dijkstra.js';
import { runAStar } from './astar.js';
import { generateSteps } from './steps.js';
import { estimatePathTimeSeconds } from './scoring.js';

function run(mode) {
  const startId = 'gate';
  const goalId = 'stopB';
  const options = { walkSpeedMps: exampleGraph.meta.avgWalkSpeedMps, busEtaSec: 240 }; // 4 mins ETA

  const t0 = Date.now();
  const dRes = runDijkstra(exampleGraph, startId, goalId, mode, options);
  const dMs = Date.now() - t0;

  const t1 = Date.now();
  const aRes = runAStar(exampleGraph, startId, goalId, mode, options);
  const aMs = Date.now() - t1;

  const dSteps = generateSteps(exampleGraph, dRes.pathNodeIds);
  const aSteps = generateSteps(exampleGraph, aRes.pathNodeIds);

  console.log(`Mode: ${mode}`);
  console.log('Dijkstra:', {
    path: dRes.pathNodeIds,
    totalDistance: Math.round(dRes.totalDistance),
    estTimeSec: Math.round(estimatePathTimeSeconds(dRes.totalDistance, options.walkSpeedMps)),
    nodesVisited: dRes.visitedCount,
    timeMs: dMs,
    steps: dSteps
  });
  console.log('A*      :', {
    path: aRes.pathNodeIds,
    totalDistance: Math.round(aRes.totalDistance),
    estTimeSec: Math.round(estimatePathTimeSeconds(aRes.totalDistance, options.walkSpeedMps)),
    nodesVisited: aRes.visitedCount,
    timeMs: aMs,
    steps: aSteps
  });
}

run('shortest');
run('fastest');
run('accessible');
