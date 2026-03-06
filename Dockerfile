FROM node:22-alpine

WORKDIR /app

# Copy package files from backend directory
COPY backend/package*.json ./

# Copy prisma config and schema (needed for postinstall prisma generate)
COPY backend/prisma.config.js ./
COPY backend/prisma/ ./prisma/

# Install ALL dependencies (postinstall runs prisma generate automatically)
RUN npm ci

# Copy backend source code
COPY backend/src/ ./src/

# Create data directory for production SQLite
RUN mkdir -p /app/data

# Remove devDependencies after build
RUN npm prune --production

# Expose port
EXPOSE 3000

# Set environment defaults (overridden by Railway env vars at runtime)
ENV NODE_ENV=production
ENV PORT=3000
ENV DATABASE_URL="file:./data/pesatrack.db"

# Start the server
CMD ["node", "src/index.js"]
