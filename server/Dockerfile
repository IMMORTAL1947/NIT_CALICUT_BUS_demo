FROM node:18-alpine

WORKDIR /app

# Copy package files from server folder
COPY package.json package-lock.json* ./

RUN npm ci --only=production || npm install --production

# Copy everything from the server folder
COPY . .

ENV NODE_ENV=production

CMD ["npm", "run", "start"]
