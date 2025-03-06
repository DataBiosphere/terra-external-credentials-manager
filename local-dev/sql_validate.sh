#!/bin/bash

# validate postgres
echo "sleeping for 5 seconds during postgres boot..."
sleep 5
PGPASSWORD=ecmpwd psql --username ecmuser -d ecm -c "SELECT VERSION();SELECT NOW()"
