<#
  本地开发栈一键启动 / 查看 / 停止
  ============================================================================
  ⚠️ 顺序有硬依赖：**Nacos 必须最先起来**。
     2026-10-15 重启电脑后实测：Nacos 没起时，hnd_factory / img-service / myocr
     三个服务会在注册中心连不上后**直接退出**（日志里是 NamingGrpcClientProxy
     .doRegisterService 失败 + 线程池 destroy）。
     更坑的是：它们退出的前一瞬端口是监听的，按端口判活会误判成"起来了"。
     所以本脚本判活看的是日志里的 `Started ... in N seconds`。

  用法（本机是 Windows PowerShell 5.1，没有 pwsh）：
     powershell -ExecutionPolicy Bypass -File D:\project\start-local-stack.ps1
         → 拉起全栈（Nacos → 三个服务 → 前端 dev server），已起的自动跳过
     ... -Status     → 只看端口状态，不动任何进程
     ... -Stop       → 停掉 Nacos 与三个服务（前端 dev server 自己 Ctrl+C）
     ... -NacosOnly  → 只保证 Nacos 在跑（服务按需自己起）

  已注册为**登录时自动执行**（计划任务 HND-local-dev-stack）。不想要了就：
     Unregister-ScheduledTask -TaskName 'HND-local-dev-stack' -Confirm:$false
  想改成只自启 Nacos：给该任务的参数加上 -NacosOnly。

  三个服务的启动方式与手工启动完全一致（java -jar + 同工作目录 + 同名日志），
  所以 config/application.yml 里的本地凭据照常生效；旧日志会改名留档。
#>
param(
    [switch]$Status,
    [switch]$Stop,
    [switch]$NacosOnly,
    # 前端 dev server 默认静默（输出写日志）；想看 HMR 就加这个开关，仍会开一个窗口
    [switch]$ShowFrontend,
    # 静默模式：由计划任务经 wscript 调用时自动带上，输出只进转录日志
    [switch]$Quiet
)

$ErrorActionPreference = 'Continue'

<#
  静默模式（计划任务用）：整个脚本的输出写进 trace 日志。
  否则"没有窗口"就等于"出事没线索"，运维时只能猜。
#>
$TraceLog = 'D:\project\logs\start-local-stack.trace.log'
if ($Quiet) {
    try {
        Start-Transcript -Path $TraceLog -Append -Force | Out-Null
    } catch {
        # 转录失败不影响启动
    }
}
$JavaPath = 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe'
$NacosBin = 'D:\dev_download\nacos\bin'
$FrontendDir = 'D:\project\hnd_factory_UI'
<#
  服务清单（2026-10-31 修正）：补上 excel-import-service。

  使用方 2026-10-31 反馈「少了个服务 excel-import-service」—— 它一直在跑，
  只是没进这份清单，所以开机自启时不会被拉起（Nacos 里也就缺它）。
  端口 8083 取自 config/application.yml 的 `port: ${SERVER_PORT:8083}`；
  jar 在同名目录下的 build/libs/excel-import-service.jar（62MB，10-15 构建）。
#>
$Services = @(
    @{ name = 'hnd_factory';          port = 8084 },
    @{ name = 'img-service';          port = 8082 },
    @{ name = 'myocr';                port = 8085 },
    @{ name = 'excel-import-service'; port = 8083 }
)
$Extras = @(
    @{ name = 'MySQL';   port = 3306 },
    @{ name = 'MinIO';   port = 9000 },
    @{ name = 'Nacos';   port = 8848 },
    @{ name = '前端 dev'; port = 9091 }
)

function Get-PortPid([int]$Port) {
    $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) { return $c.OwningProcess }
    return $null
}

function Show-Status {
    Write-Host "`n端口状态：" -ForegroundColor Cyan
    foreach ($s in ($Services + $Extras)) {
        $p = Get-PortPid $s.port
        if ($p) { Write-Host ("  [OK]   {0,-12} {1,-5} PID {2}" -f $s.name, $s.port, $p) -ForegroundColor Green }
        else    { Write-Host ("  [DOWN] {0,-12} {1,-5}" -f $s.name, $s.port) -ForegroundColor DarkGray }
    }
}

if ($Status) { Show-Status; return }

if ($Stop) {
    Write-Host "停止 Nacos 与三个服务…" -ForegroundColor Yellow
    foreach ($s in (@{ name = 'Nacos'; port = 8848 } + $Services)) {
        $p = Get-PortPid $s.port
        if ($p) {
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
            Write-Host ("  已停 {0} (PID {1})" -f $s.name, $p)
        }
    }
    Write-Host "（前端 dev server 未动，需要时自己 Ctrl+C）" -ForegroundColor DarkGray
    return
}

# 1) Nacos 必须先起
if (-not (Get-PortPid 8848)) {
    Write-Host "启动 Nacos（standalone）…" -ForegroundColor Cyan
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'startup.cmd' -WorkingDirectory $NacosBin -WindowStyle Hidden
    for ($i = 0; $i -lt 120; $i++) {
        if (Get-PortPid 8848) { Write-Host "  Nacos 就绪（$i 秒）" -ForegroundColor Green; break }
        Start-Sleep -Seconds 1
    }
    if (-not (Get-PortPid 8848)) {
        Write-Host "  Nacos 起不来，先看 D:\dev_download\nacos\logs\start.out" -ForegroundColor Red
        return
    }
} else {
    Write-Host "Nacos 已在运行，跳过" -ForegroundColor DarkGray
}

if ($NacosOnly) {
    Write-Host "（-NacosOnly：三个服务与前端不自动起，需要时自己跑本脚本不带参数）" -ForegroundColor DarkGray
    Show-Status
    return
}

# 2) 三个服务（已起的跳过）
$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
foreach ($s in $Services) {
    if (Get-PortPid $s.port) { Write-Host ("{0} 已在运行，跳过" -f $s.name) -ForegroundColor DarkGray; continue }
    $dir = "D:\project\$($s.name)"
    $jar = "$dir\build\libs\$($s.name).jar"
    if (-not (Test-Path $jar)) { Write-Host ("  {0}: 没有 {1}" -f $s.name, $jar) -ForegroundColor Red; continue }
    $log = "$dir\logs\local-run.out.log"
    if (Test-Path $log) { Move-Item $log "$dir\logs\local-run.out.$stamp.log" -Force -ErrorAction SilentlyContinue }
    $proc = Start-Process -FilePath $JavaPath -ArgumentList '-jar', $jar -WorkingDirectory $dir `
        -RedirectStandardOutput $log -RedirectStandardError "$dir\logs\local-run.err.log" -WindowStyle Hidden -PassThru
    Write-Host ("  启动 {0} PID {1}" -f $s.name, $proc.Id) -ForegroundColor Cyan
}

# 3) 判活：看 Started 行，不看端口
foreach ($s in $Services) {
    $log = "D:\project\$($s.name)\logs\local-run.out.log"
    $up = $false
    for ($i = 0; $i -lt 180; $i++) {
        if (Select-String -Path $log -Pattern 'Started .* in .* seconds' -ErrorAction SilentlyContinue) {
            $up = $true; Write-Host ("  {0} 启动完成（{1} 秒）" -f $s.name, $i) -ForegroundColor Green; break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $up) { Write-Host ("  {0} 未见 Started 行，看 {1}" -f $s.name, $log) -ForegroundColor Red }
}

# 4) 前端 dev server
#
# 默认**静默**（2026-10-31 使用方要求「不要这种黑窗口，能不能后台静默启动」）：
# 输出重定向到 logs\frontend-dev.log；想看 HMR 就带 -ShowFrontend，仍开窗口。
if (-not (Get-PortPid 9091)) {
    $feLog = Join-Path $FrontendDir 'logs\frontend-dev.log'
    New-Item -ItemType Directory -Force -Path (Split-Path $feLog) | Out-Null
    if ($ShowFrontend) {
        Write-Host "启动前端 dev server（窗口模式，端口 9091）…" -ForegroundColor Cyan
        Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'start', 'npm run dev' -WorkingDirectory $FrontendDir
    } else {
        Write-Host "启动前端 dev server（静默，日志 logs\frontend-dev.log）…" -ForegroundColor Cyan
        Start-Process -FilePath 'cmd.exe' `
            -ArgumentList '/c', ('npm run dev > "{0}" 2>&1' -f $feLog) `
            -WorkingDirectory $FrontendDir -WindowStyle Hidden
    }
}

Show-Status
Write-Host "`n前端地址： http://127.0.0.1:9091/   （局域网 http://172.26.20.69:9091/）" -ForegroundColor Cyan

if ($Quiet) {
    try { Stop-Transcript | Out-Null } catch { }
}
