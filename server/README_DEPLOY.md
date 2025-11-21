# Deploying the Bus Platform Server

This document shows easy ways to make your local server publicly accessible so the Android emulator and other devices can reach it.

Important notes
- The server currently stores data in `server/data/data.json` on the local filesystem. Many cloud hosts provide ephemeral filesystems (data will be lost when the instance is redeployed). For production use, migrate to a managed database (e.g. MongoDB Atlas, PostgreSQL) and update `src/index.js` to use the DB.

Options to host publicly

1) Deploy to a platform from your GitHub repo (recommended for simplicity)

- Create a GitHub repo and push the `server/` folder (or the project root).
- Use Render (or Railway, Fly) to create a new Web Service and connect your GitHub repo.

Render quick steps
1. Push your repo to GitHub.
2. Sign in to https://render.com and create a new "Web Service".
3. Connect your GitHub repo and select the branch.
4. Build Command: `npm install`
5. Start Command: `npm run start`
6. Render sets `PORT` automatically. The server uses `process.env.PORT || 3000` so no changes are required.

Notes: the admin dashboard is served statically at `/` if `admin-dashboard/` exists in your repo root.

Railway quick steps
1. Push the repo to GitHub.
2. Sign in to https://railway.app and create a new project + deploy from GitHub.
3. Railway uses `npm install` and `npm start` by default; verify service logs.

2) Container-based deploy (Docker)

- I added `server/Dockerfile` so you can build and run locally or push the image to any container host:

Build and run locally
```bash
cd server
docker build -t bus-platform-server .
docker run -p 3000:3000 bus-platform-server
```

Push to Docker Hub and deploy to any cloud that supports containers (DigitalOcean App Platform, AWS ECS, Azure App Service for Containers, Google Cloud Run, Fly.io etc.)

3) Quick temporary solution: ngrok (development only)

- If you need a public URL quickly for development, run the server locally and expose port 3000 with ngrok:

```bash
# after running server locally
ngrok http 3000
```

This gives a public `https://*.ngrok.io` URL you can paste into the Android app settings. Remember: ngrok URLs change each session unless you have a paid account.

Persistence recommendation
- For real multi-user use, replace file-based storage with a managed DB. Example: store colleges, stops, routes in MongoDB Atlas and update the API functions `readDb()`/`writeDb()` to use Mongo.

If you'd like, I can:
- Prepare a Docker Compose file with a MongoDB container and updated server code to use Mongo for demo persistence.
- Prepare a small migration that reads `data/data.json` into MongoDB and update `src/index.js` to read/write to the DB.

Next steps for me (pick one):
- Help you push and deploy to Render/GitHub (I can generate the GitHub repo contents and instructions). 
- Create a Docker Compose + Mongo demo and test locally.
- Walk you through installing Node on Windows and running the server locally (if you prefer to host on your machine).
