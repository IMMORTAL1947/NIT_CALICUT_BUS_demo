# Data Repo Sync (Render)

This server can keep `data.json` in a separate GitHub repository so app deployments aren’t triggered by content edits.

## Summary
- Read local runtime state from `DATA_DIR` (defaults to `server/data`)
- On startup: optionally pull `data.json` from a separate GitHub repo
- On change: commit updated `data.json` back to that repo
- Optional admin endpoint to force a pull from GitHub

## Environment Variables
- DATA_DIR: Absolute path to store runtime `data.json` (e.g., `/var/data` on Render with a disk)
- DATA_GITHUB_TOKEN: GitHub Personal Access Token (scope: `repo`)
- DATA_GITHUB_REPO: `owner/repo` for data (e.g., `IMMORTAL1947/campus-transit-data`)
- DATA_GITHUB_BRANCH: Branch name (default `main`)
- DATA_GITHUB_FILE_PATH: Path in data repo (default `data.json`)
- DATA_SYNC_ON_START: `true` (default) to pull from GitHub at boot
- ADMIN_TOKEN: If set, protects the manual sync endpoint

## Render Setup
1. Create repo `campus-transit-data` with a `data.json` file:
   ```json
   { "colleges": {} }
   ```
2. In Render service settings:
   - (Optional) Add a Persistent Disk (e.g., mount at `/var/data`)
   - Add Environment variables:
     - `DATA_DIR=/var/data` (if disk used; else skip)
     - `DATA_GITHUB_TOKEN=ghp_...`
     - `DATA_GITHUB_REPO=IMMORTAL1947/campus-transit-data`
     - `DATA_GITHUB_BRANCH=main`
     - `DATA_GITHUB_FILE_PATH=data.json`
     - `DATA_SYNC_ON_START=true`
     - `ADMIN_TOKEN=<choose-a-strong-token>`
   - Clear build cache & deploy

## Manual Sync Endpoint
- POST `/api/admin/sync-from-github`
- Headers: `X-Admin-Token: <ADMIN_TOKEN>` (if configured)

## Notes
- If both Persistent Disk and GitHub sync are configured, local runtime edits are pushed to the data repo, and on reboot the latest data is pulled.
- Using a separate data repo avoids rebuilding the app on every content update.
- You can keep the data repo private; the server only needs a PAT.
