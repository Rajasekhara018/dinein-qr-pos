FROM node:20 AS build

WORKDIR /app

# hq-shared-lib is a file: dependency on the packed .tgz (see heuristiq-web-common), so it has
# to be in the image before npm install.
COPY package*.json hq-shared-lib-*.tgz ./
RUN ls hq-shared-lib-*.tgz >/dev/null 2>&1 || (echo "ERROR: hq-shared-lib-0.0.1.tgz is missing from the build context. Run heuristiq-web-common/build.sh (it copies the packed library into every app) before docker build, and build without a cached COPY layer." >&2; exit 1)

RUN npm install --legacy-peer-deps

COPY . .

# Angular build configuration (realboom | dev | development | adityarailcab | production). Passed by the
# Jenkins Web pipeline as --build-arg PROFILE=...; defaults to realboom for a plain `docker build`.
ARG PROFILE=realboom
RUN npx ng build --configuration ${PROFILE}


FROM nginx:alpine

COPY --from=build /app/dist/heuristiq-web-shell/browser /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
