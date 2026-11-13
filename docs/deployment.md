# LandStack — Deployment & Operations (Module 16)

LandStack targets a reproducible **local/academic demonstration**. No production deployment has been performed.

## Docker Compose
```bash
cp .env.example .env      # fill LANDSTACK_DB_PASSWORD, LANDSTACK_PROCESSING_TOKEN, LANDSTACK_DEMO_PASSWORD
docker compose up --build -d
docker compose ps         # postgres, processing, backend (healthy), frontend
```
| Service | Image | Port | Notes |
|---|---|---|---|
| postgres | postgres:16-alpine | 127.0.0.1:5433 | volume `pgdata`; healthcheck `pg_isready` |
| processing | ./processing-service | internal 8090 | optional; token from `.env` |
| backend | ./backend | 127.0.0.1:8080 | waits for postgres; Flyway migrates; uploads in volume `uploads`; profile `${LANDSTACK_PROFILE:-dev}` |
| frontend | ./frontend (nginx) | 8088 | serves SPA, proxies `/api` to backend |

**Profiles.** `dev` (default in compose) enables Swagger UI and seeds three demo accounts from
`LANDSTACK_DEMO_PASSWORD`. For a production-like run set `LANDSTACK_PROFILE=` (empty), set
`LANDSTACK_BOOTSTRAP_ADMIN_EMAIL/PASSWORD` for the first administrator, `LANDSTACK_COOKIE_SECURE=true` behind HTTPS,
and a random `LANDSTACK_IP_HASH_SALT`.

**Flyway.** Runs automatically at backend startup; migrations are immutable once applied. Check with
`docker compose exec postgres psql -U landstack -c 'select version, description, success from flyway_schema_history'`.

**Demo data.** There is no automatic data seeding — import the files in `sample-data/` through the UI so every
record has real lineage.

**Stop / reset.** `docker compose down` keeps volumes. `docker compose down -v` deletes the database **and** all
uploaded files — back up first: `docker compose exec postgres pg_dump -U landstack landstack > backup.sql`.

## Hardening checklist (beyond the demo)
* Terminate TLS in front of nginx; set `LANDSTACK_COOKIE_SECURE=true`.
* Database role used by the app should not own the schema in production; additionally
  `REVOKE UPDATE, DELETE ON audit_event FROM <app_role>;` to make the audit trail append-only at DB level
  (the demo uses one owner role so Flyway can migrate).
* Rotate `LANDSTACK_PROCESSING_TOKEN`; keep the processing service on an internal network only.
* Use an approved tile provider for anything beyond light interactive use (OSM tile usage policy).
* Run dependency scans (`npm audit`, OWASP dependency-check, `pip-audit`) and update pinned versions.

## CI
`.github/workflows/ci.yml`: backend `./mvnw verify` (Testcontainers on the GitHub runner), frontend lint →
typecheck → unit tests → build, processing-service pytest, and `docker compose config` validation. No
secrets are needed. CI does not deploy anything.
