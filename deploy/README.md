# Deployment

The deployment is deliberately not presented as production-ready until Docker, TLS,
secrets, S3, migrations, and restore drills are verified on the target host.

## Prerequisites

- Docker Engine with Compose v2
- A private S3-compatible bucket and a least-privilege credential or workload IAM role
- TLS certificate and key in `deploy/certs/tls.crt` and `deploy/certs/tls.key`
- A real DNS name in `PUBLIC_HOST`; it must match the certificate and is the only Host accepted by Nginx
- `deploy/.env.production` copied from `.env.production.example` and filled from a secret manager

## First administrator

A new production database has no accounts. Set `BOOTSTRAP_ADMIN_ACCOUNT`,
`BOOTSTRAP_ADMIN_NAME`, and `BOOTSTRAP_ADMIN_PASSWORD` (at least 12 characters) for
the first start only. The initializer runs only when the administrator table is empty;
remove the password from the deployment environment after the account is created.

## Database migration

Back up the database before every release. Existing databases must run:

```bash
export DB_HOST=mysql DB_USER=dorm_app DB_PASSWORD='...' DB_NAME=dorm_repair
./deploy/scripts/migrate.sh
```

Fresh Compose databases initialize only from `schema.sql`, which already represents the
current structure. Existing databases use `deploy/scripts/migrate.sh`; never run both
paths against the same database. The application checks the required tables, compatibility view, and critical columns at startup and
fails instead of serving with an incomplete schema. The same read-only schema contract is enforced by `preflight.sh` and `preflight.ps1`. Never run destructive cleanup or delete `t_user`
during the observation window.

## Build and start

```bash
cd app/server && mvn -DskipTests package
cd ../..
cp deploy/.env.production.example deploy/.env.production
cp deploy/prometheus/alertmanager.example.yml deploy/prometheus/alertmanager.yml
# Fill PUBLIC_HOST, secrets and a real Alertmanager receiver before starting
./deploy/scripts/preflight.sh
docker compose --env-file deploy/.env.production -f deploy/docker-compose.yml up -d --build
docker compose --env-file deploy/.env.production -f deploy/docker-compose.yml ps
# Health probes are container-internal and intentionally not exposed through Nginx.
```

`.env.production` is used for Compose interpolation and is explicitly injected into the
AI/server containers. Do not reuse `app/ai/.env.local` in production.

Three frontends are served at `/student/`, `/worker/`, and `/admin/`; each Vite build
uses the same subpath as its `base`, and Nginx falls back to that app's own
`index.html` for deep links. The API is proxied under `/api/`. The Prometheus endpoint is intentionally not exposed by Nginx.

## Database TLS

Production startup requires `APP_STORE=mysql` and `APP_FILE_METADATA=mysql`; memory repositories are rejected under the `prod` profile. Production database TLS is controlled by `DB_USE_SSL` and
`DB_ALLOW_PUBLIC_KEY_RETRIEVAL`. Use `DB_USE_SSL=true`; only disable certificate
verification for an explicitly trusted private network and document the exception.

## S3 credentials

Production supports either workload IAM credentials or explicit static credentials:

- IAM Role / instance profile: leave `APP_FILE_S3_ACCESS_KEY`,
  `APP_FILE_S3_SECRET_KEY`, and `APP_FILE_S3_SESSION_TOKEN` empty.
- Static credentials: set Access Key and Secret Key together; Session Token is optional.
- Set `APP_FILE_S3_PATH_STYLE=true` for MinIO and providers that require path-style URLs.
- Production startup rejects an S3 endpoint that is not HTTPS.

The readiness endpoint includes the `fileStorage` health contributor and performs a
bucket access check. A running process with an unavailable bucket is not ready.

Uploaded files that remain unbound to an order for 24 hours are deleted by the scheduled
cleanup job. Adjust `APP_FILE_CLEANUP_RETENTION_HOURS` and `APP_FILE_CLEANUP_BATCH_SIZE`
for the deployment policy.

## Runtime database account

After the first schema initialization, strip DDL and user-management privileges from the
runtime account. Run this once per environment (and again only after recreating the database):

```bash
export DB_HOST=mysql DB_ROOT_PASSWORD='...' DB_USER=dorm_app DB_NAME=dorm_repair
./deploy/scripts/restrict-app-user.sh
```

The application account must retain only `SELECT`, `INSERT`, `UPDATE`, `DELETE`, and
`EXECUTE`. Migrations use a separate controlled operator account. `preflight.sh` rejects a
runtime account that still has `CREATE`, `DROP`, `ALTER`, `ALL PRIVILEGES`, or `GRANT OPTION`.

## Authentication operations

Login failures are limited per account and client address. Defaults are five failures in a
15-minute window; successful login clears the counter. Each account keeps only its five
newest active sessions by default; older tokens are revoked on the next successful login.
Expired and revoked sessions are purged every 60 minutes. Configure with `APP_AUTH_LOGIN_MAX_FAILURES`,
`APP_AUTH_LOGIN_BLOCK_MINUTES`, `APP_AUTH_SESSION_CLEANUP_MINUTES`,
`APP_AUTH_MAX_ACTIVE_SESSIONS`, and `APP_AUTH_TTL_HOURS`.

Set `APP_AUTH_TRUST_FORWARDED_FOR=true` only when the backend cannot be reached except
through the bundled Nginx proxy. The proxy replaces `X-Forwarded-For` with the actual peer
address; the application reads that single address for throttling. Never enable this on a
directly exposed backend, because a caller could forge the header and rotate the limiter key.

## Operations

- Liveness/readiness: container-internal `/actuator/health/liveness` and `/actuator/health/readiness`
- Metrics: internal `/actuator/prometheus` and `/metrics`
- Prometheus: internal service on port 9090; health and metrics endpoints are not exposed by Nginx
- Backups: see `backup/README.md`
- Rollback: keep the previous image tag and database backup; do not drop new tables.

Prometheus is wired to the internal Alertmanager service. Copy
`prometheus/alertmanager.example.yml` to `prometheus/alertmanager.yml`, configure a real
email/webhook/IM receiver, and send a test alert before production rollout. The preflight
script refuses placeholder receivers and missing TLS certificates. Production rollout
remains blocked until HTTPS, monitoring, S3 access, backup restore, migrations, and
rollback are exercised on the target environment.
