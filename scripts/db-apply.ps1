# 执行一个数据库变更步骤（2026-10-23，变更-163）
#
# 用法（在 hnd_factory 仓根目录）：
#   .\scripts\db-apply.ps1 -Step 25                       # 跑 docs\schema.sql 里"-- 第 25 步"那一段
#   .\scripts\db-apply.ps1 -File .\docs\material-dict-seed.sql
#   .\scripts\db-apply.ps1 -Step 25 -DryRun               # 只看要执行什么，不执行
#
# ⚠️ 只跑**你要的那一步**。整份 schema.sql 里有"重建表"的步骤，整份重跑会覆盖数据。
# ⚠️ 破坏性步骤（重建表）请先按手册里的写法把旧表 RENAME 备份，脚本不替你 DROP 任何表。

param(
    [int]$Step,
    [string]$File,
    [switch]$DryRun
)

# ⚠️ 不用 $ErrorActionPreference = 'Stop'：Windows PowerShell 5.1 会把原生命令写到 stderr 的
#    警告（比如 vitest 的 [Vue warn]）当成致命错误抛 NativeCommandError。
#    这里改成每一步显式检查 $LASTEXITCODE（下方每个外部命令后都有）。
$ErrorActionPreference = 'Continue'
$common = Join-Path $PSScriptRoot '_deploy-common.ps1'
if (-not (Test-Path $common)) { throw "找不到 $common" }
. $common

$repo = Split-Path $PSScriptRoot -Parent
$sql = $null

if ($File) {
    $path = if ([IO.Path]::IsPathRooted($File)) { $File } else { Join-Path $repo $File }
    if (-not (Test-Path $path)) { throw "找不到 SQL 文件：$path" }
    $sql = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    '  来源: ' + $path
}
elseif ($Step -gt 0) {
    $schemaPath = Join-Path $repo 'docs\schema.sql'
    $schema = [IO.File]::ReadAllText($schemaPath, [Text.Encoding]::UTF8)
    $marker = "-- 第 $Step 步"
    $at = $schema.IndexOf($marker)
    if ($at -lt 0) { throw "docs\schema.sql 里没有「$marker」" }
    # 切到下一个步骤标记（或文件末尾）
    $next = $schema.IndexOf('-- 第 ', $at + $marker.Length)
    $sql = if ($next -gt 0) { $schema.Substring($at, $next - $at) } else { $schema.Substring($at) }
    '  来源: docs\schema.sql  ' + $marker
}
else {
    throw '请给 -Step N 或 -File 路径'
}

$tables = [regex]::Matches($sql, 'CREATE TABLE\s+`?([A-Za-z0-9_]+)`?') | ForEach-Object { $_.Groups[1].Value }
$lines = ($sql -split "`n").Count
'  语句量: ' + $lines + ' 行'
'  涉及表: ' + ($(if ($tables) { $tables -join ', ' } else { '（无 CREATE TABLE，可能是 INSERT/ALTER）' }))
Write-Host ''
Write-Host '--- 即将执行的内容（前 40 行）---' -ForegroundColor DarkGray
($sql -split "`n") | Select-Object -First 40 | ForEach-Object { '  ' + $_ }
Write-Host ''

if ($DryRun) {
    '  -DryRun：未执行'
    return
}

$confirm = Read-Host "确认在**生产库 factory_db** 执行以上内容？输入 yes 继续"
if ($confirm -ne 'yes') {
    '  已取消'
    return
}

Write-Step '执行'
Assert-HndKey
$local = Join-Path $env:TEMP 'hnd-db-step.sql'
[IO.File]::WriteAllText($local, ($sql -replace "`r`n", "`n"), (New-Object Text.UTF8Encoding($false)))
Send-HndFile -LocalPath $local -RemotePath '/tmp/hnd-db-step.sql'
'  已上传 /tmp/hnd-db-step.sql'

Invoke-HndScript -Name 'hnd-db-apply' -Body @"
#!/bin/bash
docker exec -i 1Panel-mysql-Ipel sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"`$MYSQL_ROOT_PASSWORD" factory_db' < /tmp/hnd-db-step.sql
RC=`$?
echo "  mysql 退出码: `$RC"
Q() { docker exec -i 1Panel-mysql-Ipel sh -c "mysql --default-character-set=utf8mb4 -uroot -p\"`$MYSQL_ROOT_PASSWORD\" factory_db -N -B -e \"`$1\"" 2>/dev/null; }
echo '  rows in touched tables:'
for t in $($tables -join ' '); do
  n=`$(Q "SELECT COUNT(*) FROM \`\`\`$t\`\`\`;" 2>/dev/null)
  echo "    `$t: `${n:-（查不到）}"
done
exit `$RC
"@ | ForEach-Object { '  ' + $_ }

Write-Step '完成'
'  回滚方式写在 docs\schema.sql 对应步骤的注释里（不用回头翻聊天记录）'
