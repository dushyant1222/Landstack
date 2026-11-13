# LandStack backend

Java 21 · Spring Boot 3.3.5 · Spring Security 6.3 (sessions via Spring Session JDBC) · JPA/Hibernate 6.5 ·
Flyway 10 · PostgreSQL 16 · Apache POI 5.3 · Commons CSV 1.12 · springdoc 2.6.

## Run
```bash
export LANDSTACK_DB_URL=jdbc:postgresql://localhost:5432/landstack LANDSTACK_DB_USER=landstack LANDSTACK_DB_PASSWORD=...
export LANDSTACK_STORAGE_DIR=$HOME/landstack-uploads
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # dev: Swagger UI + demo accounts (needs LANDSTACK_DEMO_PASSWORD)
./mvnw -DskipTests package && java -jar target/landstack-backend-1.0.0.jar
```
`./mvnw` downloads Maven 3.9.9 into `~/.m2/wrapper` on first use.

## Migrations
Flyway runs on startup (`spring.jpa.hibernate.ddl-auto=none`; Hibernate never changes the schema).
`V1` users/audit · `V2` Spring Session · `V3` source files + import jobs · `V4` processing attempts + staging rows ·
`V5` mapping templates + mapping snapshot · `V6` land records + source values · `V7` quality issues · `V8` pg_trgm search indexes.
A second startup is a no-op. Never edit an applied migration; add `V9__...`.

## Tests
`./mvnw verify` — unit tests (`unit/`) and integration tests (`it/`, `*IT.java`) against PostgreSQL via
Testcontainers (`postgres:16-alpine`; Docker required) or `LANDSTACK_TEST_DB_URL`/`_USER`/`_PASSWORD`.
Integration tests TRUNCATE all tables: point them only at a disposable database.

## Configuration
See `src/main/resources/application.yml` (`landstack.*`) and the root `.env.example`. Startup fails with a
readable message if `landstack.storage.dir` is missing. Normalisation dictionaries live in
`src/main/resources/normalization/*.json` (versioned `norm-1.0`).

## Package map
`config, security, common, auth, users, audit, files, imports, parsing, processing, mapping, landrecords,
geospatial, quality, dashboard, exports` — Controller → Service → Repository, DTO records at the edge.
