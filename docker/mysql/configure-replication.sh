#!/bin/sh
set -eu

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-myrootpassword}"
REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-replicationpassword}"

wait_for_mysql() {
    host="$1"
    until mysqladmin ping --host="$host" --user=root --password="$MYSQL_ROOT_PASSWORD" --silent; do
        sleep 2
    done
}

wait_for_mysql mysql
wait_for_mysql mysql-replica

mysql --host=mysql --user=root --password="$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS 'replicator'@'%' IDENTIFIED BY '${REPLICATION_PASSWORD}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'replicator'@'%';
FLUSH PRIVILEGES;
SQL

mysqldump \
    --host=mysql \
    --user=root \
    --password="$MYSQL_ROOT_PASSWORD" \
    --all-databases \
    --single-transaction \
    --routines \
    --events \
    --triggers \
    --set-gtid-purged=ON \
    > /tmp/primary-seed.sql

mysql --host=mysql-replica --user=root --password="$MYSQL_ROOT_PASSWORD" <<SQL
STOP REPLICA;
RESET REPLICA ALL;
RESET BINARY LOGS AND GTIDS;
SQL

mysql --host=mysql-replica --user=root --password="$MYSQL_ROOT_PASSWORD" < /tmp/primary-seed.sql

mysql --host=mysql-replica --user=root --password="$MYSQL_ROOT_PASSWORD" <<SQL
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST = 'mysql',
    SOURCE_PORT = 3306,
    SOURCE_USER = 'replicator',
    SOURCE_PASSWORD = '${REPLICATION_PASSWORD}',
    SOURCE_AUTO_POSITION = 1;
START REPLICA;
SQL

echo "MySQL replication configured"