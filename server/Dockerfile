FROM node:18-alpine

WORKDIR /app

# When building on Render with Docker, the build context is the repo root.
# Our server lives under ./server, while the static dashboard is ./admin-dashboard.

# Copy server package files explicitly (avoid wildcard failing if lockfile is absent)
COPY server/package.json ./package.json
COPY server/package-lock.json ./package-lock.json

RUN npm ci --only=production || npm install --production

# Copy server source and assets
COPY server/src ./src
COPY server/data ./data

# Copy admin dashboard to be served at /
COPY admin-dashboard ./admin-dashboard

ENV NODE_ENV=production

EXPOSE 3000
CMD ["npm", "run", "start"]
