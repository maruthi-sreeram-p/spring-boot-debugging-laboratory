-- Creates one database per MySQL-backed lab project and grants the lab user access.
-- Runs automatically the first time the mysql volume is initialised.

CREATE DATABASE IF NOT EXISTS shopdb        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS hrdb          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS hospitaldb    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS fooddb        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS jobportaldb   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS paymentsdb    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS librarydb     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON shopdb.*      TO 'labuser'@'%';
GRANT ALL PRIVILEGES ON hrdb.*        TO 'labuser'@'%';
GRANT ALL PRIVILEGES ON hospitaldb.*  TO 'labuser'@'%';
GRANT ALL PRIVILEGES ON fooddb.*      TO 'labuser'@'%';
GRANT ALL PRIVILEGES ON jobportaldb.* TO 'labuser'@'%';
GRANT ALL PRIVILEGES ON paymentsdb.*  TO 'labuser'@'%';
GRANT ALL PRIVILEGES ON librarydb.*   TO 'labuser'@'%';
FLUSH PRIVILEGES;
