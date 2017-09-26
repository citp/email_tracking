Mail server for thesis project.

Consists of an SMTP server, web server, and MySQL database.

## Prerequisites
Requires Java 8, Maven, and MySQL.

Installation on Ubuntu 16.04:
```
$ sudo apt-get install default-jdk maven mysql-server
```

Execute the MySQL setup scripts located in the `sql-files/` directory:
```
$ mysql -u root -p
(enter your root password)

mysql> source ./sql-files/db.sql;
mysql> source ./sql-files/main.sql;
mysql> exit
```

## Usage
Build with Maven and run the packaged JAR:
```
$ mvn clean package
$ java -jar target/mailserver.jar
```

Notes:
* By default, the SMTP server listens on port 25 and the web server listens on port 8080.
* Incoming mail is stored on disk in the `mail/` directory.
* MySQL connection details are specified in `Launcher.java`.
* Some sample queries for analyzing the data can be found in `sql-files/sample_queries.sql`.
