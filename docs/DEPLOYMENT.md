# UAT Deployment (Jenkins + Docker)

How the UAT server at `35.154.15.238` builds and runs Dine-in QR POS. The pipelines live in the
`digital-apps` repo under `jenkins-pipeline/dine-in-qr/`. For local development use
`docker compose` instead (see the README).

## URLs and ports

| What | URL | Host port | Container |
|---|---|---|---|
| **The app**: customer menu, admin and kitchen (SPA + `/api` + `/ws` through nginx) | http://35.154.15.238:85 | **85** | `dinein-frontend-container` |
| Backend API, direct (testing only) | http://35.154.15.238:9030/api/... | **9030** | `dinein-backend-container` |
| PostgreSQL | – | not published | `dinein-postgres-container` |

- Customers and staff only ever need port **85**. nginx serves the Angular app and proxies `/api`
  and `/ws` to the backend, so browser and API share one origin (no CORS, cookies work).
- Port **9030** exists for testing the API directly. The public proxy still blocks `/actuator`,
  `/swagger-ui` and `/v3/api-docs`; the direct port does not, so keep 9030 restricted to known IPs.
- These ports were chosen so they don't collide with any other app on the server. The full list is
  in `digital-apps/jenkins-pipeline/PORTS.md`. Update both documents if a port changes.

### EC2 security group

| Type | Port | Source | Why |
|---|---|---|---|
| Custom TCP | 85 | Anywhere-IPv4 | Customers open the menu from the table QR code on their own phones |
| Custom TCP | 9030 | My IP | Direct API testing only |

## Containers

All three are on the Docker network `dinein-network`, separate from the other apps on the server.

| Container | Image | Memory | Notes |
|---|---|---|---|
| `dinein-frontend-container` | `nginx:1.27-alpine` + Angular build | 128m | `frontend/nginx/default.conf` proxies to `http://backend:8080` |
| `dinein-backend-container` | Spring Boot 3.5, JRE 21 | 640m | network alias **`backend`**, so the nginx config works unchanged |
| `dinein-postgres-container` | `postgres:16-alpine` | 256m | data in the named volume **`dinein-pgdata`**, kept across redeploys |

## Pipelines

| Jenkinsfile | Stages |
|---|---|
| `jenkins-pipeline/dine-in-qr/Backend/Jenkinsfile` | Sparse checkout `backend/` → Docker build → **Ensure Postgres** (creates it once, waits for `pg_isready`) → run → **Wait For Healthy** (fails the job unless `/actuator/health/readiness` passes) → image cleanup |
| `jenkins-pipeline/dine-in-qr/Frontend/Jenkinsfile` | Sparse checkout `frontend/` → Docker build → **Check Backend** (fails clearly if the backend isn't running) → run → image cleanup |

Both check out `git@github.com:Rajasekhara018/dinein-qr-pos.git`, branch `master`, with the Jenkins
SSH credential `backend_key`. That key must have read access to this repository (if it is a
per-repository deploy key, add it under GitHub → Settings → Deploy keys).

**Run order:** Backend first, then Frontend. nginx resolves the `backend` host when it starts and
exits if it can't, so the Frontend pipeline refuses to deploy while the backend is down.

## Jenkins credentials (create before the first run)

Manage Jenkins → Credentials → System → Global credentials:

| ID | Kind | Used as |
|---|---|---|
| `dinein_db_creds` | Username with password | Postgres user / password (`DB_USERNAME`, `DB_PASSWORD`) |
| `dinein_jwt_secret` | Secret text | `APP_JWT_SECRET` (at least 32 random characters: `openssl rand -base64 48`) |
| `dinein_guest_secret` | Secret text | `APP_GUEST_SESSION_SECRET` (another 32+ random characters) |
| `dinein_bootstrap_owner` | Username with password | `BOOTSTRAP_OWNER_USERNAME` / `BOOTSTRAP_OWNER_PASSWORD`, the first owner login (created once, only when no owner exists) |

The Postgres password is fixed when the volume is first created. Changing `dinein_db_creds` later
also needs `ALTER USER ... PASSWORD ...` inside Postgres, or a fresh volume (which loses the data).

## Runtime settings (set in the Backend Jenkinsfile)

| Variable | UAT value | Why |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | No sample seed data, no Swagger |
| `APP_PUBLIC_BASE_URL` | `http://35.154.15.238:85` | Printed into table QR codes and used for payment redirects. **Change it if the port or a domain changes, then reprint the QR codes** |
| `APP_CORS_ALLOWED_ORIGINS` | same as the public URL | Same-origin through nginx |
| `APP_COOKIES_SECURE` | `false` | UAT is plain HTTP; browsers never send Secure cookies back over HTTP, so login/refresh would fail. Set `true` once HTTPS is in place |
| `DB_POOL_SIZE` | `5` | Small pool for UAT |
| `PAYMENT_PROVIDER` | `RAZORPAY` (no keys yet) | The app starts, but checkout fails until `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` are added (best as another Jenkins credential) |
| `NOTIFY_EMAIL/SMS/PUSH_PROVIDER` | `LOG` | Nothing is sent yet; notifications are only logged |

## Operations

```bash
# Status and memory
docker ps --filter name=dinein --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker stats --no-stream $(docker ps -q --filter name=dinein)

# Logs
docker logs --tail 100 dinein-backend-container
docker logs --tail 100 dinein-frontend-container

# Backup / restore (the volume survives redeploys, not host loss)
docker exec dinein-postgres-container pg_dump -U <db-user> -d dinein -Fc > /backups/dinein-$(date +%F).dump
docker exec -i dinein-postgres-container pg_restore -U <db-user> -d dinein --clean --if-exists < /backups/dinein-YYYY-MM-DD.dump
```
