#!/bin/sh
set -eu

# The official MySQL image executes this only when its data volume is empty.
# Passwords come from the ignored .env file, so no credential is committed to Git.
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS ai_analyst DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS ai_business DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'app_rw'@'%' IDENTIFIED BY '${MYSQL_SYSTEM_PASSWORD}';
CREATE USER IF NOT EXISTS 'app_readonly'@'%' IDENTIFIED BY '${MYSQL_READONLY_PASSWORD}';

GRANT SELECT, INSERT, UPDATE, DELETE ON ai_analyst.* TO 'app_rw'@'%';
GRANT SELECT ON ai_business.* TO 'app_readonly'@'%';
FLUSH PRIVILEGES;
SQL
