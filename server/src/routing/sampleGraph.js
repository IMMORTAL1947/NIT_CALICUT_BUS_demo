// Example campus graph for academic testing
export const exampleGraph = {
  meta: { code: 'nitc', name: 'NIT Calicut (Example)', avgWalkSpeedMps: 1.4 },
  nodes: [
    { id: 'gate', name: 'Main Gate', lat: 11.321, lng: 75.934, type: 'gate' },
    { id: 'junction', name: 'Central Junction', lat: 11.322, lng: 75.935, type: 'intersection' },
    { id: 'library', name: 'Library', lat: 11.323, lng: 75.9355, type: 'landmark' },
    { id: 'canteen', name: 'Canteen', lat: 11.324, lng: 75.936, type: 'landmark' },
    { id: 'stopA', name: 'Stop A', lat: 11.325, lng: 75.937, type: 'bus_stop' },
    { id: 'stopB', name: 'Stop B', lat: 11.326, lng: 75.938, type: 'bus_stop' },
  ],
  // Directed edges to allow asymmetric costs (e.g., slopes)
  edges: [
    // Gate ↔ Junction (internal road, shaded)
    { from: 'gate', to: 'junction', distance: 180, time: 130, walkOnly: true, shadedPreference: 1 },
    { from: 'junction', to: 'gate', distance: 180, time: 130, walkOnly: true, shadedPreference: 1 },

    // Junction ↔ Library (stairs between)
    { from: 'junction', to: 'library', distance: 140, time: 110, stairsPenalty: 2, accessibilityCost: 2 },
    { from: 'library', to: 'junction', distance: 140, time: 120, stairsPenalty: 2, accessibilityCost: 2 },

    // Library ↔ Canteen (slope, partially shaded)
    { from: 'library', to: 'canteen', distance: 220, time: 160, slopePenalty: 1, shadedPreference: 0.5 },
    { from: 'canteen', to: 'library', distance: 220, time: 170, slopePenalty: 1, shadedPreference: 0.5 },

    // Canteen ↔ Stop A (safe internal road)
    { from: 'canteen', to: 'stopA', distance: 250, time: 190, safetyWeight: 1 },
    { from: 'stopA', to: 'canteen', distance: 250, time: 190, safetyWeight: 1 },

    // Junction ↔ Stop B (stairs, shorter but less accessible)
    { from: 'junction', to: 'stopB', distance: 260, time: 185, stairsPenalty: 2, accessibilityCost: 3 },
    { from: 'stopB', to: 'junction', distance: 260, time: 190, stairsPenalty: 2, accessibilityCost: 3 },

    // Library ↔ Stop B (longer but accessible and shaded)
    { from: 'library', to: 'stopB', distance: 350, time: 260, shadedPreference: 1, accessibilityCost: 0 },
    { from: 'stopB', to: 'library', distance: 350, time: 260, shadedPreference: 1, accessibilityCost: 0 },
  ]
};
