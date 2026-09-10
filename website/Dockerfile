# Multi-stage Dockerfile for the PesaTrack marketing site (Astro static).
#
# Stage 1 — install pnpm + build static site (astro build runs pagefind postbuild).
# Stage 2 — tiny Caddy container serving /usr/share/caddy on port 80.
#
# Runs on Hetzner behind the shared Caddy proxy at ~/proxy. Container name is
# `pesatrack-web-1`; TLS terminates upstream.

# ---------------------------------------------------------------------------
# 1) build — produce dist/ with pagefind index in dist/_pagefind
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# pnpm via corepack — matches packageManager pin in package.json.
RUN corepack enable

# Install deps with a frozen lockfile.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

# Public site URL used by astro.config.mjs for canonical URLs + sitemap.
ARG SITE=https://pesatrack.jmumo.com
ENV SITE=${SITE}

COPY . .
RUN pnpm run build

# ---------------------------------------------------------------------------
# 2) runtime — serve dist/ with Caddy on :80
# ---------------------------------------------------------------------------
FROM caddy:2-alpine AS runtime
COPY --from=build /app/dist /usr/share/caddy
COPY Caddyfile /etc/caddy/Caddyfile
