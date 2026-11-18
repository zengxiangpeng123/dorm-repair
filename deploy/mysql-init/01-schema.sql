-- Bootstrap schema for a brand-new database.
-- Fresh databases get the complete schema; existing databases must run deploy/scripts/migrate.sh.
SOURCE /docker-entrypoint-initdb.d/schema.sql;

-- Apply the incremental migrations for a new installation as well.
SOURCE /docker-entrypoint-initdb.d/migrations/20261118_split_roles_and_rework.sql;
SOURCE /docker-entrypoint-initdb.d/migrations/20261118_file_metadata.sql;
SOURCE /docker-entrypoint-initdb.d/migrations/20261118_ai_review_audit.sql;
SOURCE /docker-entrypoint-initdb.d/migrations/20261118_business_closure.sql;
