# PesaTrack API — Phase 1 image
# Follows the multi-app pattern in C:\Eng\StockUp\deploy\README.md §4
#
# Base is node:20-slim (Debian) rather than -alpine because Prisma's schema
# engine binary has ongoing OpenSSL detection issues on Alpine musl.
# See https://www.prisma.io/docs/orm/reference/system-requirements

FROM node:20-slim AS base
WORKDIR /app
ENV NODE_ENV=production

# Runtime OS deps:
#   - openssl + ca-certificates: Prisma schema/query engines link against libssl3
#   - wget: Docker HEALTHCHECK + docker-compose healthcheck use it
RUN apt-get update \
  && apt-get install -y --no-install-recommends openssl ca-certificates wget \
  && rm -rf /var/lib/apt/lists/*

# --- deps (with prisma generate) ---
FROM base AS deps
# Copy package.json AND package-lock.json (glob keeps it optional so the image
# still builds pre-lockfile). With a lockfile present, npm ci gives us a
# reproducible install.
COPY package.json package-lock.json* ./
COPY prisma ./prisma
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
# schema.prisma writes the generated client to ../src/generated/prisma (i.e.
# /app/src/generated/prisma). That path is gitignored, so it's absent from the
# runtime build context — we copy it from the deps stage instead.
COPY --from=deps /app/src/generated ./src/generated
# Non-root user for defense-in-depth.
RUN groupadd --system app \
  && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
  && chown -R app:app /app
USER app
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:3000/health >/dev/null 2>&1 || exit 1
CMD ["node", "src/index.js"]
