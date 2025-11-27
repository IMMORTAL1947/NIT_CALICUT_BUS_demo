FROM node:18-alpine

# Root build: bundle server API + admin dashboard
WORKDIR /app

# Copy server manifests
COPY server/package.json server/package-lock.json* ./
RUN npm ci --only=production || npm install --production

# Copy server source & data
COPY server/src ./src
COPY server/data ./data

# Copy admin dashboard static assets
COPY admin-dashboard ./admin-dashboard

ENV NODE_ENV=production
EXPOSE 3000
CMD ["node", "src/index.js"]
