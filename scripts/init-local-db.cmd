@echo off
rem ===========================================================================
rem  Initialize the LOCAL factory_db from docs\schema.sql
rem
rem  Why this script exists (2026-10-31):
rem    The PC purchase board threw "Request failed with status code 500" and the
rem    backend log said:
rem        Table 'factory_db.purchase_tracking' doesn't exist
rem    Root cause was NOT code: the local MySQL (BtSoft) only had 19 of the
rem    ~40 tables, because docs\schema.sql had never been applied locally.
rem    Applying it fixed it: /api/purchase/list went from 500 to 200.
rem
rem  Notes learned the hard way:
rem    * --default-character-set=utf8mb4 is REQUIRED. Without it the seed
rem      INSERTs fail with "Data too long for column 'role_name'" and
rem      "Incorrect string value" (the client speaks GBK otherwise).
rem    * --force keeps going past "table already exists" so re-running is safe.
rem    * The schema has no DROP DATABASE / TRUNCATE; it only creates missing
rem      tables and adds missing columns. Still, do not run it against a
rem      database you care about: it also re-seeds dict/role rows.
rem
rem  KEEP THIS FILE PURE ASCII (cmd.exe parses .cmd with the OEM codepage).
rem ===========================================================================
setlocal

set "MYSQL=D:\BtSoft\mysql\MySQL8.0\bin\mysql.exe"
set "SCHEMA=%~dp0..\docs\schema.sql"
set "DB=factory_db"

if not exist "%MYSQL%" (
  echo [FAIL] mysql.exe not found: %MYSQL%
  echo        edit MYSQL= in this file to point at your client
  pause
  exit /b 1
)
if not exist "%SCHEMA%" (
  echo [FAIL] schema not found: %SCHEMA%
  pause
  exit /b 1
)

echo.
echo Creating database %DB% if missing...
"%MYSQL%" -uroot -p123eetig --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS %DB% DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo Applying %SCHEMA% ...
"%MYSQL%" -uroot -p123eetig --default-character-set=utf8mb4 %DB% --force < "%SCHEMA%"
echo   (errors like "table already exists" are expected when re-running)

echo.
echo Result:
"%MYSQL%" -uroot -p123eetig --default-character-set=utf8mb4 -N -e "select concat('  tables: ', count(*)) from information_schema.tables where table_schema='%DB%'; select concat('  purchase_tracking: ', count(*)) from information_schema.tables where table_schema='%DB%' and table_name='purchase_tracking'; select concat('  attendance_day:    ', count(*)) from information_schema.tables where table_schema='%DB%' and table_name='attendance_day'; select concat('  roles: ', count(*)) from %DB%.sys_role;"

echo.
echo Restart the backend if it was running before this (it caches nothing, but
echo a failed request may have left the page in an error state - just refresh).
echo Press any key to close...
pause > nul
