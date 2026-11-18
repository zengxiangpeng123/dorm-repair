-- Bootstrap schema for a brand-new database only.
-- The Compose MYSQL_DATABASE selects the target database before this script runs.
-- Existing databases must use deploy/scripts/migrate.sh instead; do not run both.
SOURCE /docker-entrypoint-initdb.d/schema.sql;
