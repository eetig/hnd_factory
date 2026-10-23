# 线上状态体检（只读，2026-10-23，变更-163）
#
# 用法：
#   .\scripts\verify-prod.ps1                 # 前端 + 后端 + 库 + 接口
#   .\scripts\verify-prod.ps1 -Section api    # 只看后端
#
# 不改任何东西：不重启容器、不动文件、不写库。
#
# ⚠️ 两条硬约定（都是 2026-10-23 实测踩出来的）：
#   ① **远端脚本只用 ASCII 输出**：Windows PowerShell 5.1 用本地代码页解码 ssh 的
#      stdout，远端打中文会变乱码（既难看也可能干扰解析）。中文提示放在 PS 这一侧。
#   ② **mysql 的密码在容器内用单引号包住**：`sh -c 'mysql ... -p"$MYSQL_ROOT_PASSWORD" ...'`
#      —— 密码由容器自己的环境变量展开。写成 `-p\"$MYSQL...\"` 会在宿主机展开成空、
#      mysql 转去问密码，脚本直接卡死（踩过，超 600s）。

param(
    [ValidateSet('all', 'web', 'api', 'db')]
    [string]$Section = 'all'
)

# ⚠️ 不用 $ErrorActionPreference = 'Stop'：Windows PowerShell 5.1 会把原生命令写到 stderr 的
#    警告（比如 vitest 的 [Vue warn]）当成致命错误抛 NativeCommandError。
#    这里改成每一步显式检查 $LASTEXITCODE（下方每个外部命令后都有）。
$ErrorActionPreference = 'Continue'
$common = Join-Path $PSScriptRoot '_deploy-common.ps1'
if (-not (Test-Path $common)) { throw "找不到 $common" }
. $common

if ($Section -eq 'all' -or $Section -eq 'web') {
    Write-Step '前端'
    $body = @'
#!/bin/bash
S=__SITE_ROOT__
curl -s http://127.0.0.1:__SITE_PORT__/ -o /tmp/i.html -w '  index http=%{http_code}\n'
echo '  served assets:'
grep -o 'assets/index-[A-Za-z0-9_-]*\.\(js\|css\)' /tmp/i.html | sort -u | sed 's/^/    /'
echo '  site dir built files (newest 3):'
ls -1t $S/assets/index-*.js 2>/dev/null | head -3 | xargs -r -n1 basename | sed 's/^/    /'
echo '  rollback copies:'
ls -1d $S/index.prev.* 2>/dev/null | tail -3 | xargs -r -n1 basename | sed 's/^/    /'
'@
    $body = $body.Replace('__SITE_ROOT__', $SiteRoot).Replace('__SITE_PORT__', "$SitePort")
    $out = Invoke-HndScript -Name 'hnd-verify-web' -Body $body
    $out | ForEach-Object { '  ' + $_ }
}

if ($Section -eq 'all' -or $Section -eq 'api') {
    Write-Step '后端'
    $body = @'
#!/bin/bash
echo -n '  jar md5 in container: '
docker exec hnd_factory md5sum /app/hnd_factory.jar 2>/dev/null | awk '{print $1}'
echo -n '  started at: '
docker inspect hnd_factory --format '{{.State.StartedAt}} restarts={{.RestartCount}}'
B=__API_BASE__
echo '  endpoints:'
curl -s -o /dev/null -w "    /api/material/page    %{http_code}  [200]\n"  "$B/api/material/page?pageNum=1&pageSize=1"
curl -s -o /dev/null -w "    /api/material/facets  %{http_code}  [200]\n"  "$B/api/material/facets"
curl -s -o /dev/null -w "    /api/purchase/list    %{http_code}  [401 without token]\n" "$B/api/purchase/list?pageNum=1&pageSize=1"
curl -s -o /dev/null -w "    /api/ocr-proxy        %{http_code}  [415 without file]\n" -X POST "$B/api/ocr-proxy/recognize"
curl -s -o /dev/null -w "    /api/tank-level/suggestions  %{http_code}  [200]\n" "$B/api/tank-level/suggestions"
'@
    $body = $body.Replace('__API_BASE__', $ApiBase)
    $out = Invoke-HndScript -Name 'hnd-verify-api' -Body $body
    $out | ForEach-Object { '  ' + $_ }
}

if ($Section -eq 'all' -or $Section -eq 'db') {
    Write-Step '数据库'
    $body = @'
#!/bin/bash
M() { timeout 20 docker exec -i 1Panel-mysql-Ipel sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" factory_db -N -B -e "'"$1"'"' </dev/null 2>/dev/null; }
echo '  key table rows:'
for t in material_master material_type_dict purchase_tracking work_order; do
  n=$(M "SELECT COUNT(*) FROM $t;")
  echo "    $t = ${n:-n/a}"
done
echo '  material status (1 ok / 2 pending-disable / 0 disabled):'
M "SELECT status, COUNT(*) FROM material_master GROUP BY status ORDER BY status;" | sed 's/^/    status=/'
echo '  backup tables:'
M "SHOW TABLES LIKE '%_bak_%';" | sed 's/^/    /'
'@
    $out = Invoke-HndScript -Name 'hnd-verify-db' -Body $body
    $out | ForEach-Object { '  ' + $_ }
}

Write-Step '完成'
'  提醒用户 Ctrl+F5 清缓存'
