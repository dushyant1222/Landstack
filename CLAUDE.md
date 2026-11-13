# LandStack — standing agent instructions

LandStack = GIS-Based Land Information Integration & Governance Dashboard (academic, software-only).
The source of truth for scope is the "LandStack Master Prompt Pack" (SRS v1.0, 14 Oct 2026); the
decisions taken from it are recorded in `docs/architecture.md`. Read those before changing anything.

## Working rules
- Build one module at a time, in order (0 → 16). Do not start a module until the previous one builds,
  its tests pass, and `docs/implementation-log.md` is updated with a Module Completion Report.
- Extend existing working code; never recreate or delete it without a stated reason.
- Report real command output. "Done" means implemented, executed and verified.
- If something can't be done safely, implement the safest working alternative and document the gap.

## Non-negotiables
- Core workflow: upload → validate → parse/extract → map → normalize → human review → confirm →
  search → map → quality review.
- Stack: React 18 + TS strict + Vite + Tailwind + TanStack Query + RHF/Zod + Leaflet;
  Java 21 + Spring Boot 3 + Spring Security + JPA + Flyway; PostgreSQL; optional Python (FastAPI).
- Roles ADMINISTRATOR / DATA_OPERATOR / READ_ONLY_REVIEWER, enforced in the backend.
- Never fabricate land data, coordinates, geometry, statistics, statuses or test results.
- Preserve raw source values and lineage (source_file_id, import_job_id) on every imported record.
- Identifiers (ULPIN, parcel ID, Khasra/survey no.) are TEXT; areas are NUMERIC.
- Nothing is a confirmed LandRecord until an operator explicitly confirms the import (one transaction,
  idempotent).
- Quality flags are "potential issue for review", never legal conclusions. No auto-merge or auto-delete.
- No live government APIs, no geocoding, no external AI services for uploaded data.
- Uploads live outside public roots, with size/type/signature checks and server-generated names.
- No secrets in git; no passwords/tokens/owner data in logs.
- One home per business rule: normalization and validation live in the backend (`mapping`/`quality`
  packages), never duplicated in React.
