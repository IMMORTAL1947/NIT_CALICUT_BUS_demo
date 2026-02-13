// Generate simple step-by-step directions from node sequence and graph edges
function nodeById(graph, id) {
  return graph.nodes.find(n => n.id === id);
}

function edgeBetween(graph, fromId, toId) {
  return graph.edges.find(e => e.from === fromId && e.to === toId);
}

import { haversineMeters } from './haversine.js';

function isGenericName(nameOrId) {
  const s = String(nameOrId || '');
  return s.startsWith('n_');
}

export function generateSteps(graph, pathNodeIds) {
  if (!Array.isArray(pathNodeIds) || pathNodeIds.length < 2) return [];
  const steps = [];
  let fromId = pathNodeIds[0];
  let fromNode = nodeById(graph, fromId);
  let accDist = 0;

  const pushStep = (from, to, distMeters, isFirst = false) => {
    const isGenericFrom = isGenericName(from?.name || from?.id);
    const fromLabel = isFirst && isGenericFrom ? 'Your Location' : (from?.name || from?.id);
    const toLabel = isGenericName(to?.name || to?.id) ? (to?.name || to?.id) : (to?.name || to?.id);
    const distText = ` (~${Math.round(distMeters)} m)`;
    steps.push(`Walk from ${fromLabel} to ${toLabel}${distText}`);
  };

  for (let i = 0; i < pathNodeIds.length - 1; i++) {
    const aId = pathNodeIds[i];
    const bId = pathNodeIds[i + 1];
    const a = nodeById(graph, aId);
    const b = nodeById(graph, bId);
    const e = edgeBetween(graph, aId, bId);
    const segDist = e && typeof e.distance === 'number'
      ? e.distance
      : haversineMeters({ lat: a?.lat, lng: a?.lng }, { lat: b?.lat, lng: b?.lng });
    accDist += (segDist || 0);

    const isBoundary = !isGenericName(b?.name || bId) || (i + 1 === pathNodeIds.length - 1);
    if (isBoundary) {
      const isFirst = (fromId === pathNodeIds[0]);
      pushStep(fromNode, b, accDist, isFirst);
      fromId = bId;
      fromNode = b;
      accDist = 0;
    }
  }
  return steps;
}
