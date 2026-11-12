# Standalone frontend image (used by the EKS staging environment).
# Prod on EC2 keeps using the root Dockerfile, where Spring Boot serves the React build itself.
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
# Rendered at start-up by the nginx image: ${BACKEND_UPSTREAM} and ${DATA_UPSTREAM} come from env vars.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV BACKEND_UPSTREAM=framebucket-backend:8080 \
    DATA_UPSTREAM=framebucket-data-api:8001
EXPOSE 80
