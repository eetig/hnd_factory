# 后端发布（2026-10-23，变更-163）
#
# 用法（在 hnd_factory 仓根目录）：
#   .\scripts\deploy-api.ps1              # 测试 + 打包 + 上传 + 进容器 + 重启 + 验收
#   .\scripts\deploy-api.ps1 -SkipTest    # 跳过单测（急发时用，不推荐）
#   .\scripts\deploy-api.ps1 -SkipBuild   # 直接发 build\libs 里现成的 jar
#
# 读 docs/部署手册.md 的第 1 节：如果这次还带库变更，先跑 scripts\db-apply.ps1，再跑本脚本。

param(
    [switch]$SkipTest,
    [switch]$SkipBuild
)

# ⚠️ 不用 $ErrorActionPreference = 'Stop'：Windows PowerShell 5.1 会把原生命令写到 stderr 的
#    警告（比如 vitest 的 [Vue warn]）当成致命错误抛 NativeCommandError。
#    这里改成每一步显式检查 $LASTEXITCODE（下方每个外部命令后都有）。
$ErrorActionPreference = 'Continue'
$common = Join-Path $PSScriptRoot '_deploy-common.ps1'
if (-not (Test-Path $common)) { throw "找不到 $common" }
. $common

$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    Write-Step '0) 前置检查'
    '  仓库: ' + $repo
    '  分支: ' + (& git rev-parse --abbrev-ref HEAD)
    $dirty = (& git status --porcelain | Measure-Object).Count
    '  未提交改动: ' + $dirty + ' 项（发布的是磁盘代码，注意）'

    if (-not $SkipBuild) {
        if (-not $SkipTest) {
            Write-Step '1) 单元测试'
            & .\gradlew.bat test --console=plain -q
            if ($LASTEXITCODE -ne 0) { throw '单测未通过，不发布' }
            '  测试通过'
        }

        Write-Step '2) 打包'
        & .\gradlew.bat bootJar --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw 'bootJar 失败' }
    }
    else {
        Write-Step '1-2) 跳过测试与打包（-SkipBuild）'
    }

    $jar = Get-ChildItem (Join-Path $repo 'build\libs') -Filter '*.jar' |
        Where-Object { $_.Name -notmatch 'plain' } | Select-Object -First 1
    if (-not $jar) { throw '没有找到 build\libs\*.jar（先去掉 -SkipBuild 打一次）' }
    $localMd5 = Get-FileMd5 $jar.FullName
    '  jar: ' + $jar.Name + '  ' + [math]::Round($jar.Length / 1MB, 1) + ' MB  md5=' + $localMd5

    Write-Step '3) 上传到服务器'
    Send-HndFile -LocalPath $jar.FullName -RemotePath '/tmp/hnd_factory-new.jar'
    '  已上传 /tmp/hnd_factory-new.jar'

    Write-Step '4) 进容器 + 重启 + 验收'
    $sudo = Get-HndSudo
    $out = Invoke-HndScript -Name 'hnd-api-deploy' -Body @"
#!/bin/bash
echo '$sudo' | sudo -S -p '' cp /tmp/hnd_factory-new.jar /opt/hnd_factory/hnd_factory.jar 2>/dev/null
docker cp /opt/hnd_factory/hnd_factory.jar hnd_factory:/app/hnd_factory.jar >/dev/null && echo '  docker cp ok'
echo -n '  container md5: '; docker exec hnd_factory md5sum /app/hnd_factory.jar | awk '{print `$1}'
docker restart hnd_factory >/dev/null && echo '  restarted, waiting for Spring Boot...'
sleep 34
B=$ApiBase
for u in '/api/material/page?pageNum=1&pageSize=1' '/api/material/facets' '/api/purchase/list?pageNum=1&pageSize=1' '/api/tank-level/suggestions'; do
  curl -s -o /dev/null -w "  `$u  http=%{http_code}\n" "`$B`$u"
done
curl -s -o /dev/null -w '  /api/ocr-proxy/recognize  http=%{http_code}[415 without file]\n' -X POST "`$B/api/ocr-proxy/recognize"
echo -n '  started at: '; docker inspect hnd_factory --format '{{.State.StartedAt}}'
"@
    $out | ForEach-Object { '  ' + $_ }

    Write-Step '5) 结论'
    $remoteMd5 = ((& ssh -o BatchMode=yes -i $HndKey -p $HndPort $HndHost 'docker exec hnd_factory md5sum /app/hnd_factory.jar' | Out-String).Trim() -split '\s+')[0]
    if ($remoteMd5 -eq $localMd5) {
        '  ✅ 容器里跑的正是刚打的 jar（md5 ' + $localMd5 + '）'
    }
    else {
        '  ❌ md5 不一致：本地 ' + $localMd5 + '，容器 ' + $remoteMd5 + ' —— 没 cp 上'
        throw '后端 md5 不一致'
    }
}
finally {
    Pop-Location
}
