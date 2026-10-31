@echo off
rem ===========================================================================
rem  HND local dev stack - status check (double-click friendly)
rem
rem  2026-10-31: after making autostart silent there is no console window to
rem  look at, so this is the "where do I look" entry point.
rem
rem  WARNING: keep this file PURE ASCII. cmd.exe parses .cmd with the OEM
rem  codepage; UTF-8 comments turn into garbage that cmd tries to execute.
rem ===========================================================================
setlocal
title HND stack status

set "PS=powershell.exe -NoProfile -ExecutionPolicy Bypass"

echo.
echo === ports ===
%PS% -Command ^
  "$ports = @{8084='hnd_factory';8082='img-service';8085='myocr';8083='excel-import-service';8848='Nacos';3306='MySQL';9000='MinIO';9091='frontend dev'};" ^
  "foreach ($p in ($ports.Keys | Sort-Object)) {" ^
  "  $c = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1;" ^
  "  if ($c) { Write-Host ('  [OK]   {0,-22} {1,-5} PID {2}' -f $ports[$p], $p, $c.OwningProcess) -ForegroundColor Green }" ^
  "  else    { Write-Host ('  [DOWN] {0,-22} {1,-5}' -f $ports[$p], $p) -ForegroundColor DarkGray }" ^
  "}"

echo.
echo === services registered in Nacos ===
%PS% -Command ^
  "try { (Invoke-RestMethod 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100' -TimeoutSec 5).doms | ForEach-Object { '  ' + $_ } } catch { '  Nacos not reachable' }"

echo.
echo === tail of startup trace ===
if exist "D:\project\logs\start-local-stack.trace.log" (
  %PS% -Command "Get-Content 'D:\project\logs\start-local-stack.trace.log' -Tail 25"
) else (
  echo   no trace log yet
)

echo.
echo === tail of each service log (last 3 lines) ===
for %%S in (hnd_factory img-service myocr excel-import-service) do (
  if exist "D:\project\%%S\logs\local-run.out.log" (
    echo   --- %%S ---
    %PS% -Command "Get-Content 'D:\project\%%S\logs\local-run.out.log' -Tail 3"
  )
)

echo.
echo Press any key to close...
pause > nul
