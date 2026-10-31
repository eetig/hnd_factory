# 本地开发栈一键启动（Windows）

这套脚本负责本机开机自启：Nacos → 四个 Spring Boot 服务 → 前端 dev server。

## 文件

| 文件 | 作用 |
|---|---|
| `start-local-stack.ps1` | 正本。拉起 / 查看 / 停止全栈。**唯一可以改逻辑的地方** |
| `start-local-stack-hidden.vbs` | 静默包装器：计划任务调它，它用 `WScript.Shell.Run(cmd, 0, False)` 起 PowerShell |
| `check-stack.cmd` | 双击看状态：六个端口 + Nacos 注册列表 + 各服务日志尾巴 |

计划任务（登录时触发）：

```
HND-local-dev-stack
  wscript.exe //B //Nologo "D:\project\hnd_factory\scripts\local-stack\start-local-stack-hidden.vbs"
```

## 服务的端口（和各自 `config/application.yml` 一致）

| 服务 | 端口 |
|---|---|
| `hnd_factory` | 8084 |
| `img-service` | 8082 |
| `myocr` | 8085 |
| `excel-import-service` | 8083 |
| Nacos | 8848 |
| 前端 dev | 9091 |

## 两个真实踩过的坑（别再改回去）

### 1. `excel-import-service` 曾不在清单里

它一直在跑、jar 也在，但**没写进 `$Services`**，于是开机自启不会拉它，Nacos 里也就缺它。
使用方 2026-10-31 的原话：「少了个服务 excel-import-service」。
加服务时**同时**填端口，判活看的是端口，不是进程名。

### 2. 黑窗口：`-WindowStyle Hidden` 隐藏不了"自己的控制台"

- 症状：开机后屏上留一个黑窗口，里面打印 `Nacos 已在运行，跳过 / 启动 xxx PID ...`
- 原因：PowerShell 5.1 的 `-WindowStyle Hidden` 只作用于**它自己创建的子进程**，
  隐藏不了 Windows 启动 PowerShell 时给它的那个控制台宿主窗口
- 解法：计划任务改成 `wscript.exe //B` 调 VBS，VBS 里 `Run(cmd, 0, False)` ——
  在**创建进程时**就要求无窗口（0），这是不装 NSSM 时唯一干净的办法
- 附加坑：**无控制台时 `Start-Transcript` 会直接失败**（PowerShell 已知行为），
  所以 VBS 自己用 `cmd /c ... >> start-local-stack.out.log 2>&1` 做重定向；
  否则"静默"就等于"出事没有日志"

### 3. 这两个文件必须**纯 ASCII**

`start-local-stack-hidden.vbs` 与 `check-stack.cmd` 由 Windows Script Host / cmd.exe
按 OEM 代码页读取。写中文注释会解码成乱码字节，进而被当成命令执行，直接语法错误。
实测：VBS 里塞中文后 `cscript` 静默失败、开机自启完全没反应。

## 日志

| 文件 | 内容 |
|---|---|
| `D:\project\logs\start-local-stack.out.log` | 静默启动的全量 stdout+stderr（VBS 重定向） |
| `D:\project\logs\start-local-stack.trace.log` | `-Quiet` 时的转录（前台跑才有） |
| `D:\project\<service>\logs\local-run.out.log` | 每个服务的 Spring Boot 输出（`Started ... in N seconds` 是判活依据） |
| `D:\project\hnd_factory_UI\logs\frontend-dev.log` | 前端 dev server 输出（静默模式下看 HMR 报错） |

## 手工用法

```powershell
# 全栈（缺什么起什么；已起的跳过）
powershell -ExecutionPolicy Bypass -File .\start-local-stack.ps1

# 只看端口
powershell -ExecutionPolicy Bypass -File .\start-local-stack.ps1 -Status

# 停掉 Nacos 与四个服务（前端自己 Ctrl+C / 关进程）
powershell -ExecutionPolicy Bypass -File .\start-local-stack.ps1 -Stop

# 想看前端 HMR：让前端 dev server 也开窗口
powershell -ExecutionPolicy Bypass -File .\start-local-stack.ps1 -ShowFrontend
```

## 为什么不按端口判"服务已就绪"

Nacos 没起时，四个服务会在注册失败后**直接退出**，而它们退出的前一瞬端口是监听的。
所以判活看的是日志里的 `Started ... in N seconds`，不是端口。启动顺序也必须是 Nacos 最先。
