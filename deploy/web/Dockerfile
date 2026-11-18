FROM node:22-alpine AS build
WORKDIR /src
COPY app/student-web/package*.json ./student-web/
COPY app/worker-web/package*.json ./worker-web/
COPY app/admin-web/package*.json ./admin-web/
COPY app/web/packages ./web/packages
RUN cd student-web && npm ci && cd ../worker-web && npm ci && cd ../admin-web && npm ci
COPY app/student-web ./student-web
COPY app/worker-web ./worker-web
COPY app/admin-web ./admin-web
COPY app/tests/check-three-apps.mjs ./tests/check-three-apps.mjs
RUN cd student-web && npm run build && cd ../worker-web && npm run build && cd ../admin-web && npm run build
RUN node tests/check-three-apps.mjs

FROM nginx:1.27-alpine
COPY deploy/nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/student-web/dist /usr/share/nginx/html/student
COPY --from=build /src/worker-web/dist /usr/share/nginx/html/worker
COPY --from=build /src/admin-web/dist /usr/share/nginx/html/admin
EXPOSE 80 443
