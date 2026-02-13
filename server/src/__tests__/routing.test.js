import { exampleGraph } from '../routing/sampleGraph.js';
import { runDijkstra } from '../routing/dijkstra.js';
import { runAStar } from '../routing/astar.js';

const modes = ['shortest', 'fastest', 'accessible'];

describe('Routing algorithms comparison', () => {
  test.each(modes)('Dijkstra vs A* (%s)', (mode) => {
    const startId = 'gate';
    const goalId = 'stopB';
    const options = { walkSpeedMps: exampleGraph.meta.avgWalkSpeedMps, busEtaSec: 240 };

    const d = runDijkstra(exampleGraph, startId, goalId, mode, options);
    const a = runAStar(exampleGraph, startId, goalId, mode, options);

    expect(d.totalCost).not.toBe(Infinity);
    expect(a.totalCost).not.toBe(Infinity);
    expect(d.pathNodeIds.length).toBeGreaterThan(1);
    expect(a.pathNodeIds.length).toBeGreaterThan(1);

    // A* should visit fewer or equal nodes than Dijkstra on average
    expect(a.visitedCount).toBeLessThanOrEqual(d.visitedCount);
  });
});
