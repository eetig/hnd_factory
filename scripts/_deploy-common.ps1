# 部署脚本共用的 SSH / 站定参数（2026-10-23，变更-163）
#
# 由 deploy-api.ps1 / deploy-web.ps1 / verify-prod.ps1 / db-apply.ps1 点源引入。
# 想换服务器只改这一个文件。

# ⚠️ 不用 $ErrorActionPreference = 'Stop'：Windows PowerShell 5.1 会把原生命令写到 stderr 的
#    警告（比如 vitest 的 [Vue warn]）当成致命错误抛 NativeCommandError。
#    这里改成每一步显式检查 $LASTEXITCODE（下方每个外部命令后都有）。
$ErrorActionPreference = 'Continue'

$script:HndHost = 'eetig@124.220.60.154'
$script:HndPort = 9002
$script:HndKey = Join-Path $env:USERPROFILE '.ssh\id_ed25519_hnd_deploy'

$script:ApiBase = 'http://127.0.0.1:8084'
$script:SitePort = 9091
$script:SiteRoot = '/opt/1panel/apps/openresty/openresty/www/sites/hnd-ui'
$script:StagingDir = '/tmp/hnd-ui-new'

# sudo 密码：优先环境变量，其次 %USERPROFILE%\.hnd-deploy.env（不进仓）
function Get-HndSudo {
    if ($env:HND_SUDO) { return $env:HND_SUDO }
    $envFile = Join-Path $env:USERPROFILE '.hnd-deploy.env'
    if (Test-Path $envFile) {
        foreach ($line in Get-Content $envFile) {
            if ($line -match '^\s*HND_SUDO\s*=\s*(.+)$') { return $Matches[1].Trim() }
        }
    }
    throw "缺少 sudo 密码：请在 $envFile 里写一行 HND_SUDO=你的密码，或设置环境变量 HND_SUDO"
}

function Assert-HndKey {
    if (-not (Test-Path $script:HndKey)) {
        throw "缺少 SSH 私钥：$($script:HndKey)"
    }
}

# 运行一段远程 bash 脚本：先 scp 上去，再用 bash 执行（避免引号地狱）
# 用法：Invoke-HndScript -Body @' ...bash... '@
function Invoke-HndScript {
    param([Parameter(Mandatory = $true)][string]$Body, [string]$Name = 'hnd-task')

    Assert-HndKey
    $local = Join-Path $env:TEMP "$Name.sh"
    # 统一 LF，否则远端 bash 会报 $'\r': command not found
    $Body = $Body -replace "`r`n", "`n"
    [IO.File]::WriteAllText($local, $Body, (New-Object Text.UTF8Encoding($false)))
    $remote = "/tmp/$Name.sh"

    & scp -q -i $script:HndKey -P $script:HndPort $local "$($script:HndHost):$remote"
    if ($LASTEXITCODE -ne 0) { throw "scp 失败：$local" }

    & ssh -o BatchMode=yes -i $script:HndKey -p $script:HndPort $script:HndHost "bash $remote"
    if ($LASTEXITCODE -ne 0) { throw "远程脚本失败：$remote（退出码 $LASTEXITCODE）" }
}

function Send-HndFile {
    param([Parameter(Mandatory = $true)][string]$LocalPath, [Parameter(Mandatory = $true)][string]$RemotePath)

    Assert-HndKey
    & scp -q -i $script:HndKey -P $script:HndPort $LocalPath "$($script:HndHost):$RemotePath"
    if ($LASTEXITCODE -ne 0) { throw "scp 失败：$LocalPath → $RemotePath" }
}

function Get-FileMd5 {
    param([Parameter(Mandatory = $true)][string]$Path)
    (Get-FileHash $Path -Algorithm MD5).Hash.ToLower()
}

function Write-Step {
    param([string]$Text)
    Write-Host ''
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}
