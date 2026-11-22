#!/bin/bash
# Tao user/schema cho tung service trong PDB.
# Dung .sh thay vi .sql vi SQL*Plus khong doc duoc bien moi truong, ma mat khau phai lay tu env.
# Mat khau cua schema X doc tu bien X_DB_PASSWORD (xem infra/.env.example).
set -Eeuo pipefail

SERVICE_SCHEMAS=(CUSTOMER_SVC ACCOUNT_SVC TRANSFER_SVC NAPAS_SVC NOTI_SVC FRAUD_SVC)
PDB_NAME="${ORACLE_DATABASE:-FREEPDB1}"

for schema in "${SERVICE_SCHEMAS[@]}"; do
  password_var="${schema}_DB_PASSWORD"
  password="${!password_var:?${password_var} is required}"

  echo "Creating schema ${schema} in ${PDB_NAME}"
  sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT FAILURE
ALTER SESSION SET CONTAINER = ${PDB_NAME};
CREATE USER ${schema} IDENTIFIED BY "${password}"
  DEFAULT TABLESPACE USERS
  QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW,
      CREATE PROCEDURE, CREATE TRIGGER, CREATE SYNONYM TO ${schema};
EXIT
SQL
done
