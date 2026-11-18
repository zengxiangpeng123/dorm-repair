# Deployment

The deployment is deliberately not presented as production-ready until Docker, TLS,
secrets, S3, migrations, and restore drills are verified on the target host.

## Prerequisites

- Docker Engine with Compose v2
- A private S3-compatible bucket and a least-privilege credential or workload IAM role
- TLS certificate and key in `deploy/certs/tls.crt` and `deploy/certs/tls.key`
- `deploy/.env.production` copied from `.env.production.example` and filled from a secret manager

## Database migration

Back up the database before every release. Existing databases must run:

```bash
export DB_HOST=mysql DB_USER=dorm_app DB_PASSWORD='...' DB_NAME=dorm_repair
./deploy/scripts/migrate.sh
```

Fresh Compose databases initialize only from `schema.sql`, which already represents the
current structure. Existing databases use `deploy/scripts/migrate.sh`; never run both
paths against the same database. Never run destructive cleanup or delete `t_user`
during the observation window.

## Build and start

```bash
cd app/server && mvn -DskipTests package
cd ../..
cp deploy/.env.production.example deploy/.env.production
# Fill secrets in deploy/.env.production before starting
docker compose --env-file deploy/.env.production -f deploy/docker-compose.yml up -d --build
curl -k https://localhost/actuator/health/readiness
```

`.env.production` is used for Compose interpolation and is explicitly injected into the
AI/server containers. Do not reuse `app/ai/.env.local` in production.

Three frontends are served at `/student/`, `/worker/`, and `/admin/`; the API is
proxied under `/api/`. The Prometheus endpoint is intentionally not exposed by Nginx.

## S3 credentials

Production supports either workload IAM credentials or explicit static credentials:

- IAM Role / instance profile: leave `APP_FILE_S3_ACCESS_KEY`,
  `APP_FILE_S3_SECRET_KEY`, and `APP_FILE_S3_SESSION_TOKEN` empty.
- Static credentials: set Access Key and Secret Key together; Session Token is optional.
- Set `APP_FILE_S3_PATH_STYLE=true` for MinIO and providers that require path-style URLs.

The readiness endpoint includes the `fileStorage` health contributor and performs a
bucket access check. A running process with an unavailable bucket is not ready.

## Operations

- Liveness/readiness: `/actuator/health/liveness`, `/actuator/health/readiness`
- Metrics: internal `/actuator/prometheus` and `/metrics`
- Prometheus: internal service on port 9090; metrics endpoints are not exposed by Nginx
- Backups: see `backup/README.md`
- Rollback: keep the previous image tag and database backup; do not drop new tables.

Prometheus rules are loaded by default; `prometheus/alertmanager.example.yml` is a
receiver template. Add an Alertmanager service and configure a real email/webhook/IM
receiver, then send a test alert before production rollout. Production rollout remains
blocked until HTTPS, monitoring, S3 access, backup restore, migrations, and rollback are
exercised on the target environment.
