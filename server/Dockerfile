FROM node:18-alpine

# Create app directory
WORKDIR /app

# Install dependencies first (takes advantage of Docker cache)
COPY package.json package-lock.json* ./
RUN npm ci --only=production || npm install --production

# Copy source
COPY src ./src
COPY ../admin-dashboard ./admin-dashboard
COPY data ./data

ENV NODE_ENV=production
EXPOSE 3000

CMD ["npm", "run", "start"]
