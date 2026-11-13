# LandStack — Test Report

| Field | Value |
|---|---|
| Date | _to be filled when run_ |
| Commit | _to be filled_ |
| Environment | _OS, CPU, RAM, JDK, Node, Python, PostgreSQL versions_ |

## Status
The test suites listed in `test-plan.md` are written but **have not been executed yet**: the environment
in which the code was produced could not install Maven/npm/pip dependencies. No pass/fail numbers are
claimed here. Fill in the tables below with real command output.

## Results (fill in)
| Command | Tests run | Passed | Failed | Skipped | Duration | Notes |
|---|---|---|---|---|---|---|
| `cd backend && ./mvnw verify` | | | | | | |
| `cd frontend && npm run lint` | – | | | – | | |
| `cd frontend && npm run typecheck` | – | | | – | | |
| `cd frontend && npm test` | | | | | | |
| `cd frontend && npm run build` | – | | | – | | |
| `cd processing-service && pytest -q` | | | | | | |
| `cd frontend && E2E_PASSWORD=… npm run e2e` | | | | | | |

## Performance (fill in)
| Measurement | Dataset | Result | Target |
|---|---|---|---|
| Parse 5,000-row CSV (upload → AWAITING_MAPPING) | performance_5000.csv | | – |
| Save mapping (normalize + validate 5,000 rows) | performance_5000.csv | | – |
| Confirm 5,000 rows | performance_5000.csv | | – |
| `GET /land-records?q=PERF00012` (median of 3) | 5,000 records | | < 3 s |
| `GET /land-records?district=Patna&landType=AGRICULTURAL` | 5,000 records | | < 3 s |
| `GET /map/points` | 5,000 records | | < 3 s |

## Checks already performed in the build environment
* `docker compose config -q` — compose file valid.
* `bash -n backend/mvnw` — wrapper script syntax valid.
* `python3 -m py_compile` on all Python sources — OK.
* All JSON resources and `package.json` parse.
* Static scan for unused/missing imports in TypeScript and Java sources (custom script) — issues found were fixed.
* `sample-data/generate_samples.py` executed — sample files generated.

## Known gaps
* Results above pending; any failing test must be fixed before the demonstration.
* Playwright E2E needs a running stack with demo accounts.
