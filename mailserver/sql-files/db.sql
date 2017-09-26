CREATE DATABASE IF NOT EXISTS `mail`;
CREATE USER 'mailserver'@'localhost' IDENTIFIED BY 'S6TTAykTfAEMJjqN';
USE `mail`;
GRANT ALL ON `mail`.* TO 'mailserver'@'localhost';
SET @@global.time_zone = '+00:00';
