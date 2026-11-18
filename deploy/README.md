# Deployment

The deployment is deliberately not presented as production-ready until Docker, TLS
certificates, secrets, S3, and restore drills are verified on the target host.

## Prerequisites

- Docker Engine with Compose v2
- A private S3-compatible bucket
- TLS certificate and key in `deploy/certs/tls.crt` and `deploy/certs/tls.key`
- `deploy/.env.production` copied from `.env.production.example` and filled from a secret manager

## Build and start

```bash
cd app/server && mvn -DskipTests package
cd ../..
cp deploy/.env.production.example deploy/.env.production
# Fill secrets in deploy/.env.production before starting
docker compose --env-file deploy/.env.production -f deploy/docker-compose.yml up -d --build

# .env.production is used for Compose interpolation and is explicitly injected
# into the ai/server containers. Do not reuse app/ai/.env.local in production.
curl -k https://localhost/actuator/health
```

Three frontends are served at `/student/`, `/worker/`, and `/admin/`; the API is
proxied under `/api/`. The Prometheus endpoint is intentionally not exposed by Nginx.

## Operations

- Liveness/readiness: `/actuator/health/liveness`, `/actuator/health/readiness`
- Metrics: internal `/actuator/prometheus` and `/metrics`
- Prometheus: internal service on port 9090; metrics endpoints are not exposed by Nginx
- Backups: see `backup/README.md`
- Rollback: keep the previous image tag and database backup; do not drop new tables.

Prometheus alert rules are loaded, but no Alertmanager receiver is configured by default.
Configure a receiver and send a test alert before production rollout.

Production rollout remains blocked until HTTPS, monitoring, backup restore, and rollback
are exercised on the target environment.
