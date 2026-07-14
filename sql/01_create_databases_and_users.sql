-- Run this script with a MySQL administrator account. Replace both passwords before use.
CREATE DATABASE IF NOT EXISTS ai_analyst DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS ai_business DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'app_rw'@'%' IDENTIFIED BY 'CHANGE_ME_SYSTEM_PASSWORD';
CREATE USER IF NOT EXISTS 'app_readonly'@'%' IDENTIFIED BY 'CHANGE_ME_READONLY_PASSWORD';

GRANT SELECT, INSERT, UPDATE, DELETE ON ai_analyst.* TO 'app_rw'@'%';
GRANT SELECT ON ai_business.* TO 'app_readonly'@'%';
FLUSH PRIVILEGES;
