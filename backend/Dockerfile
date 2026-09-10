# PesaTrack API — Phase 1 image
# Follows the multi-app pattern in C:\Eng\StockUp\deploy\README.md §4

FROM node:20-alpine AS base
WORKDIR /app
ENV NODE_ENV=production

# --- deps (with prisma generate) ---
FROM base AS deps
COPY package.json ./
COPY prisma ./prisma
# npm ci needs a lockfile; use install to bootstrap when the file is absent.
# CI/CD should commit the lockfile after the first deploy.
RUN if [ -f package-lock.json ]; then \
      npm ci --omit=dev --ignore-scripts; \
    else \
      npm install --omit=dev --ignore-scripts; \
    fi
# Generate the Prisma client (postinstall was skipped above).
RUN npx prisma generate

# --- runtime ---
FROM base AS runtime
COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/prisma ./prisma
COPY package.json ./
COPY src ./src
# Non-root user for defense-in-depth.
RUN addgroup -S app && adduser -S app -G app && chown -R app:app /app
USER app
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:3000/health >/dev/null 2>&1 || exit 1
CMD ["node", "src/index.js"]
