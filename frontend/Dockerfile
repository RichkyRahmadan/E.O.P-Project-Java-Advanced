# ============================================================
# E.O.P Frontend (Angular 18) — Dockerfile
# Build with Node.js → Serve with Nginx
# ============================================================

# --- STAGE 1: Build ---
FROM node:20-alpine AS builder
WORKDIR /app

# Install dependencies first (cache layer)
COPY package*.json ./
RUN npm ci --silent

# Build production bundle
COPY . .
RUN npm run build -- --configuration=production

# --- STAGE 2: Serve with Nginx ---
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Remove default Nginx static files
RUN rm -rf ./*

# Copy Angular build output
COPY --from=builder /app/dist/eop-frontend/browser ./

# Copy custom Nginx config for Angular routing (SPA fallback)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
