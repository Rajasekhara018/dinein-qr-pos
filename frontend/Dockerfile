# syntax=docker/dockerfile:1.7

# ---- Build ----
# FROM node:22-alpine AS build

# WORKDIR /app

# COPY package.json package-lock.json ./

# RUN npm ci --no-audit --no-fund

# COPY . .

# RUN npx ng build --configuration production


# # ---- Serve ----
# FROM nginx:1.27-alpine

# COPY nginx/default.conf /etc/nginx/conf.d/default.conf

# COPY --from=build /app/dist/dinein/browser /usr/share/nginx/html

# EXPOSE 80

# HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
#     CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1

# CMD ["nginx", "-g", "daemon off;"]


# Build stage
FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm install --no-audit --no-fund

COPY . .

ARG APP_PROFILE=uat
RUN npm run build -- --configuration ${APP_PROFILE}

# Serve stage
FROM nginx:1.27-alpine

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/gateway-frontend/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
