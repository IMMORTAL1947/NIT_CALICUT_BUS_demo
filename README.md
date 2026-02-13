# NIT Calicut Bus Platform (Demo)

This repository contains:
- Android app under `app/`
- Node.js backend under `server/`
- Admin dashboard (static) under `admin-dashboard/`

## Deploy to Render (recommended, free tier)

There are two approaches:

### A) Full UI + API (root Dockerfile)
1. Ensure the root `Dockerfile` exists (bundles server + admin-dashboard).
2. On Render: create a new Web Service, set Root Directory to repository root (blank).
3. Environment: Docker; no build/start commands (Dockerfile handles it).
4. Deploy. Visit `/` for the admin dashboard, `/api/...` for endpoints.

### B) API only (server folder Dockerfile)
1. Set Root Directory to `server`.
2. Use `server/Dockerfile` (no dashboard included) for a slimmer image.
3. Root URL returns JSON hint; dashboard not served.

### C) Node runtime (render.yaml)
Use the provided `render.yaml` to let Render manage Node without Docker (add back if removed). Good for quick iterations.

Notes
- The admin dashboard is served at the root path `/` of your Render URL.
- API is under `/api`, e.g. `GET /api/colleges/NITC/config`.
- File storage is `server/data/data.json` which is ephemeral on hosts like Render. For production use, migrate to a managed DB.

## Local development (Windows)

```powershell
# Install Node.js LTS from https://nodejs.org
cd M:\NIT_CALCIUT_BUS_DEMO\server
npm install
npm run dev
```
- Browse http://localhost:3000/ (dashboard)
- Android emulator can use `http://10.0.2.2:3000` as the Server URL in Settings.

## Android app settings
- College Code: `NITC`
- Server URL: your Render URL (e.g., `https://your-app.onrender.com`), or `http://10.0.2.2:3000` for local.

## Campus routing API
- Endpoint: `GET /api/colleges/:code/route-to-stop`
- Query params: `userLat`, `userLng`, `stopId`, `mode=shortest|fastest|accessible`, `algo=dijkstra|astar`
- Response includes ordered nodes, total distance, estimated time, and steps. See `server/README-routing.md` for details.

More deployment options: see `server/README_DEPLOY.md`.

## Switching Deployment Mode
- Want the dashboard back? Use the root Dockerfile (Approach A) or Node runtime.
- Want to reduce image size? Use server-only Dockerfile (Approach B).
- Need persistent data? Attach a disk or migrate to a database.