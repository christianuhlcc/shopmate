# Build context must be the repository root (generate-api reads ../api/openapi.yaml).
# docker build -f frontend/Dockerfile .

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# OpenAPI contract (referenced as ../api/openapi.yaml) and sources
COPY api /app/api
COPY frontend/ ./
RUN npm run build

# ---- Runtime stage: nginx serving the static SPA ----
FROM nginx:1.27-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost/ >/dev/null || exit 1
