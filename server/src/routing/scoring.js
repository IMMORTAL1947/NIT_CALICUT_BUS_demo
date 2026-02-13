// Edge cost scoring with campus-specific constraints and mode
// Modes: shortest | fastest | accessible

const DEFAULTS = {
  shadePreferenceWeight: 0.3, // prefer shaded paths optionally
  safetyWeightWeight: 0.4,    // prefer safer internal roads
  stairsPenaltyWeight: 2.0,   // penalize stairs for accessibility
  slopePenaltyWeight: 1.5,    // penalize steep slopes
  accessibilityCostWeight: 2.0, // explicit accessibility cost multiplier
  walkSpeedMps: 1.4
};

export function computeEdgeCost(edge, mode, options = {}) {
  const cfg = { ...DEFAULTS, ...options };
  const baseDistance = edge.distance || 0;           // meters
  const baseTime = edge.time || baseDistance / cfg.walkSpeedMps; // seconds
  const shade = edge.shadedPreference || 0;          // higher is better
  const safety = edge.safetyWeight || 0;             // higher is better
  const stairs = edge.stairsPenalty || 0;            // higher is worse
  const slope = edge.slopePenalty || 0;              // higher is worse
  const access = edge.accessibilityCost || 0;        // higher is worse

  // Time-based weighting: if bus arrives soon, prefer faster paths
  const busEtaSec = options.busEtaSec; // optional
  let timeBias = 1.0;
  if (typeof busEtaSec === 'number') {
    if (busEtaSec <= 300) timeBias = 1.3;        // <5 min, bias time
    else if (busEtaSec <= 900) timeBias = 1.1;   // <15 min, slight bias
    else timeBias = 0.95;                        // far away, favor other qualities
  }

  // Base mode costs
  let cost = 0;
  if (mode === 'fastest') {
    // prioritize time; incorporate penalties mildly
    cost = baseTime * timeBias + stairs * 2 + slope * 1.2 + access * cfg.accessibilityCostWeight;
    cost -= shade * cfg.shadePreferenceWeight; // reduce cost when shaded
    cost -= safety * cfg.safetyWeightWeight;   // reduce cost when safer
  } else if (mode === 'accessible') {
    // strongly penalize stairs/slope/access issues; balance time
    cost = baseTime * (timeBias * 1.05) + stairs * cfg.stairsPenaltyWeight + slope * cfg.slopePenaltyWeight + access * (cfg.accessibilityCostWeight * 1.5);
    cost -= shade * (cfg.shadePreferenceWeight * 0.5);
    cost -= safety * (cfg.safetyWeightWeight * 0.5);
  } else {
    // shortest: prioritize distance; still consider constraints lightly
    cost = baseDistance + stairs * 50 + slope * 30 + access * 40;
    cost -= shade * 5;
    cost -= safety * 10;
  }

  // Disallow non-walkable paths if any future vehicle support is added; for now walkOnly allowed
  return Math.max(0, cost);
}

export function estimatePathTimeSeconds(totalDistanceMeters, avgMps = DEFAULTS.walkSpeedMps) {
  return totalDistanceMeters / avgMps;
}

export function chooseReason(mode) {
  if (mode === 'accessible') return 'minimized stairs and steep slopes';
  if (mode === 'fastest') return 'prioritized fastest arrival based on ETA';
  return 'minimized walking distance';
}
