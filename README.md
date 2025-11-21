# NIT Calicut Bus Platform (Demo)

This repository contains:
- Android app under `app/`
- Node.js backend under `server/`
- Admin dashboard (static) under `admin-dashboard/`

## Deploy to Render (recommended, free tier)

1. Push this repository to your GitHub account.
2. Create a new Web Service on https://render.com and choose “Use render.yaml”.
3. Render will detect `render.yaml` at the repo root and create the service.
4. Deploy. The server listens on `PORT` provided by Render.

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

More deployment options: see `server/README_DEPLOY.md`.