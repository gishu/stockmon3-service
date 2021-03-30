#!/bin/bash
set -e

echo "INIT SCRIPT - setup tables and existing data"
echo $POSTGRES_PASSWORD
PGPASSWORD=$POSTGRES_PASSWORD psql -U $POSTGRES_USER -d $POSTGRES_DB < /docker-entrypoint-initdb.d/kaching.dmp