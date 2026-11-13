-- LandStack V8: trigram indexes for substring search (pg_trgm is a trusted extension since PG 13)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_land_record_village_trgm ON land_record USING gin (lower(village_locality) gin_trgm_ops);
CREATE INDEX ix_land_record_parcel_trgm ON land_record USING gin (lower(parcel_id) gin_trgm_ops);
CREATE INDEX ix_land_record_khasra_trgm ON land_record USING gin (lower(survey_khasra_no) gin_trgm_ops);
CREATE INDEX ix_land_record_owner_trgm ON land_record USING gin (lower(owner_name) gin_trgm_ops);
