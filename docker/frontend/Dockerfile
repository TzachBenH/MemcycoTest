# syntax=docker/dockerfile:1

# Frontend image: builds the React app with Vite, then serves the static files with nginx, which also proxies
# /api to the two backend instances (round robin). The build context is the repository root:
#   docker build -f docker/frontend/Dockerfile -t scheduler-frontend .

# ---------------------------------------------------------------------------------------------------------------
# 1) Build
# ---------------------------------------------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies first: cached as a layer until package.json / package-lock.json change.
COPY frontend/package*.json ./
RUN if [ -f package-lock.json ]; then npm ci --no-audit --no-fund; else npm install --no-audit --no-fund; fi

COPY frontend/ ./
RUN npm run build

# ---------------------------------------------------------------------------------------------------------------
# 2) Runtime
# ---------------------------------------------------------------------------------------------------------------
FROM nginx:stable-alpine

COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --start-interval=2s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
