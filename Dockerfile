FROM node:18-alpine

WORKDIR /app

# Copy package files from backend directory
COPY backend/package*.json ./

# Install ALL dependencies (need devDependencies for prisma generate)
RUN npm ci

# Copy backend source code
COPY backend/ ./

# Set DATABASE_URL for Prisma migration at build time
ENV DATABASE_URL="file:./dev.db"

# Generate Prisma client
RUN npx prisma generate

# Create data directory for production SQLite
RUN mkdir -p /app/data

# Remove devDependencies after build
RUN npm prune --production

# Expose port
EXPOSE 3000

# Set environment defaults (overridden by Railway env vars at runtime)
ENV NODE_ENV=production
ENV PORT=3000

# Start the server (migrations run at startup via connectDatabase)
CMD ["node", "src/index.js"]
