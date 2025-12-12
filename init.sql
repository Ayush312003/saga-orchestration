-- init.sql
CREATE DATABASE IF NOT EXISTS order_db;
CREATE DATABASE IF NOT EXISTS payment_db;
CREATE DATABASE IF NOT EXISTS inventory_db;
CREATE DATABASE IF NOT EXISTS orchestrator_db; -- Optional: For Saga logs/audit

-- Optional: Create a specific user for services so we don't use root
-- GRANT ALL PRIVILEGES ON order_db.* TO 'user'@'%' IDENTIFIED BY 'password';
-- GRANT ALL PRIVILEGES ON payment_db.* TO 'user'@'%' IDENTIFIED BY 'password';
-- GRANT ALL PRIVILEGES ON inventory_db.* TO 'user'@'%' IDENTIFIED BY 'password';