FROM node:18-alpine

WORKDIR /app

# This Dockerfile now assumes the BUILD CONTEXT is the server directory itself
# (Render "Root Directory" set to server). If you want the admin dashboard,
# set Root Directory to repo root AND revert to copying admin-dashboard.

# Copy package manifests
COPY package.json package-lock.json* ./

RUN npm ci --only=production || npm install --production

# Copy source and data from current context
COPY src ./src
COPY data ./data

ENV NODE_ENV=production
EXPOSE 3000
CMD ["npm", "run", "start"]
