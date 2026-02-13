# Campus Routing (Dijkstra / A*)

This backend adds campus-specific routing and optimization without external APIs.

## Endpoint
- GET `/api/colleges/:code/route-to-stop`
- Query params:
  - `userLat`, `userLng`: user location
  - `stopId`: destination bus stop node id
  - `mode`: `shortest` | `fastest` | `accessible`
  - `algo`: `dijkstra` | `astar`
  - `busEtaSeconds` (optional): seconds until bus arrives, biases path to faster routes
  - Note: If `stopId` does not directly exist in the campus graph, the backend will attempt to map your college stop ID to the nearest graph node using the stop's coordinates from college config.

## Response
```
{
  nodes: [{ id, name, lat, lng }],
  totalDistanceMeters,
  estimatedTimeSeconds,
  totalDistance, // alias for totalDistanceMeters
  estimatedTime, // alias for estimatedTimeSeconds
  reason,
  steps: ["Walk from A to B (~140 m)", ...],
  performance: { algo, nodesVisited, timeMs }
}
```

## Graph JSON
Place college graphs under `server/data/graphs/<code>.json`:
```
{
  meta: { code, name, avgWalkSpeedMps },
  nodes: [{ id, name, lat, lng, type }],
  edges: [{ from, to, distance, time, accessibilityCost, safetyWeight, shadedPreference, slopePenalty, stairsPenalty, walkOnly }]
}
```

## Dev and Tests
- Quick comparison without Jest:
  - `npm run route:test`
- Jest tests (requires install):
  - `npm install`
  - `npm test`
