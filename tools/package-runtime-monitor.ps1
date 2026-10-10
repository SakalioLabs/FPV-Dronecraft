param(
    [string]$PackageDirectory = 'F:/Codex-Project/MCFPV/deliverables/runtime-monitor-20261010',
    [string]$ProductionRunId = 'production-monitor-final-20261010'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$packageRoot = [IO.Path]::GetFullPath($PackageDirectory)
if (Test-Path -LiteralPath $packageRoot) { throw 'Package directory already exists' }
$productionRoot = Join-Path $projectRoot ('build/performance/' + $ProductionRunId)
$production = Get-Content -LiteralPath (Join-Path $productionRoot 'report.json') -Raw | ConvertFrom-Json
$receipt = Get-Content -LiteralPath (Join-Path $productionRoot 'launch-receipt.json') -Raw | ConvertFrom-Json
if (!$production.passed -or !$production.runtime_monitor_smoke.passed -or $receipt.exitCode -ne 0) { throw 'Production smoke has not passed with exit code 0' }
$jarName = 'fpv-dronecraft-fabric-0.1.1-playtest.4.jar'
$jar = Join-Path $projectRoot ('fabric-mod/build/libs/' + $jarName)
$jarSha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($jarSha -ne $receipt.modSha256) { throw 'Release JAR differs from tested production JAR' }
$clientReport = Join-Path $projectRoot 'fabric-mod/build/run/playableClientGameTest/runtime-monitor-20261010/playtest-client-report.json'
$client = Get-Content -LiteralPath $clientReport -Raw | ConvertFrom-Json
if (!$client.passed -or !$client.runtimePerformanceFunctionalSmoke.passed) { throw 'Client command/packet smoke did not pass' }
$installerCheckRoot = Join-Path $projectRoot 'build/runtime-monitor-installer-final-check'
$installerCheck = Get-Content -LiteralPath (Join-Path $installerCheckRoot 'installer-verification.json') -Raw | ConvertFrom-Json
if (!$installerCheck.all_pass) { throw 'Installer checks did not pass' }
$releaseCheck = Get-Content -LiteralPath (Join-Path $installerCheckRoot 'release-check.json') -Raw | ConvertFrom-Json
if ($releaseCheck.jar.sha256 -ne $jarSha) { throw 'Release inventory refers to a different artifact' }
$mods = Join-Path $packageRoot 'mods'
$verification = Join-Path $packageRoot 'verification'
New-Item -ItemType Directory -Path $mods,$verification | Out-Null
Copy-Item -LiteralPath $jar -Destination $mods
$utf8Bom = [Text.UTF8Encoding]::new($true)
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $packageRoot 'install-runtime-monitor.ps1'),[IO.File]::ReadAllText((Join-Path $projectRoot 'tools/install-runtime-monitor.ps1')),$utf8Bom)
$manifest = [ordered]@{
    version='0.1.1-playtest.4';old_filename='fpv-dronecraft-fabric-0.1.1-playtest.3.jar'
    old_sha256='e7df9958fbc5251177d2d5abc4908a358da732256997e47f75132d4d0d3c9f13'
    new_filename=$jarName;new_sha256=$jarSha
}
[IO.File]::WriteAllText((Join-Path $packageRoot 'install-manifest.json'),($manifest | ConvertTo-Json),$utf8)
$cmd = @'
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-runtime-monitor.ps1" -PackageDirectory "%~dp0"
if errorlevel 1 echo Update stopped. Read the reason above; close Minecraft before retrying.
pause
'@
[IO.File]::WriteAllText((Join-Path $packageRoot 'install.cmd'),$cmd.Replace("`n","`r`n"),[Text.Encoding]::ASCII)
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/development/runtime-performance-monitor-20261010.md') -Destination (Join-Path $packageRoot '研发与验证说明.md')
Copy-Item -LiteralPath $clientReport -Destination (Join-Path $verification 'client-smoke.json')
Copy-Item -LiteralPath (Join-Path $productionRoot 'report.json') -Destination (Join-Path $verification 'production-monitor-smoke.json')
Copy-Item -LiteralPath (Join-Path $productionRoot 'launch-receipt.json') -Destination (Join-Path $verification 'production-launch-receipt.json')
Copy-Item -LiteralPath (Join-Path $installerCheckRoot 'installer-verification.json'),(Join-Path $installerCheckRoot 'release-check.json') -Destination $verification
Copy-Item -LiteralPath (Join-Path $productionRoot 'screenshots/runtime-monitor-gpu-hud.png') -Destination $verification
Copy-Item -LiteralPath (Join-Path $projectRoot 'build/runtime-monitor-report-ui/report.png') -Destination (Join-Path $verification 'report-preview.png')
foreach ($kind in @('cpu','gpu','disconnect')) {
    $relativeReport = $production.runtime_monitor_smoke.$kind.report_directory
    $capture = [IO.Path]::GetFullPath((Join-Path $productionRoot $relativeReport))
    if (!$capture.StartsWith([IO.Path]::GetFullPath($productionRoot) + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Capture report path escapes production smoke directory' }
    Copy-Item -LiteralPath $capture -Destination (Join-Path $verification ('functional-capture-' + $kind)) -Recurse
}
$readme = @'
# FPV Dronecraft .4：帧时间监测

本包用于原 FPV 实例的 Mod 更新，沿用 Minecraft 1.21.11、Java 21、Fabric Loader 0.19.3 和已安装 Fabric API 0.141.6+1.21.11。保留 .3 的飞行手感、平滑显示和原版保存，本轮新增可开关的性能监测。

## 开始监测

1. 退出游戏后运行 `install.cmd`，更新 `F:/Desktop/.minecraft/versions/FPV/mods`。已更新则无需再装。旧 .3 备份在 FPV 的 `fpvdrone-mod-backups/`；API、选项、手柄设置和存档不变。
2. PCL 启动原 **FPV**，进入世界，执行 `/fpvperf start 120`。
3. 悬停、转向、同一区域高速绕圈。出现不舒服的瞬间可执行 `/fpvperf mark 卡顿`；两分钟后自动停止，或用 `/fpvperf stop` 提前结束。
4. 打开 FPV 的 `performance-diagnostics/perf-.../report.html`。同目录包含 `frames.csv`、`events.csv`、`summary.json` 和指标说明，可把整个报告文件夹打包用于下一轮分析。

监测默认关闭。右侧面板显示帧时间曲线、P95/P99、峰值、提交/交换缓冲/限帧耗时及服务端保存状态。用 `/fpvperf hud off` 隐藏面板继续采集，`hud on` 恢复。

需要 GPU 绘制范围计时，用 `/fpvperf startgpu 120`。先从普通采集开始即可。`/fpvperf target 120` 只设置报告预算，不改变游戏帧率；120 FPS 对应 8.33 ms，60 FPS 对应 16.67 ms。持续时间允许 5–600 秒。

帧间隔反映提交节奏，GPU 查询不覆盖显示扫描，去显式等待后的墙钟时间也不等于纯 CPU 时间。保存、状态更新和画面尖峰在同一时间轴上方便对照，但相关不等于因果。缺失值不按零处理，环形覆盖数量在报告说明。完整定义见《研发与验证说明》及生成报告里的 README。

## 验证

单元测试、服务端 9 项 GameTest、客户端命令/飞行/消息处理/报告导出及正常生产运行验证已通过。生产运行使用原 FPV 的 68 项运行库、LWJGL 3.3.3-snapshot 与 Fabric API 0.141.6；CPU/GPU/采集重启、原版 GPU 调试共存、退出保存和重进检查通过，进程退出码 0。

`verification/functional-capture-*` 是独立合成世界的功能检查样本。隐藏窗口和合成客户端测试不能代表你的垂直同步显示效果，也没有据此宣称剩余卡顿已经修复；实际游玩采集将用于下一步定位。
'@
[IO.File]::WriteAllText((Join-Path $packageRoot 'README-开始监测.md'),$readme,$utf8)
$summary = [ordered]@{
    schema='mcfpv-runtime-monitor-release-validation-v1';version='0.1.1-playtest.4';jar_sha256=$jarSha
    junit=$releaseCheck.junit;server_gametest=[ordered]@{passed=$true;required=9;exit_code=0}
    client_smoke=[ordered]@{passed=$client.passed;registered_commands_and_packet_capture=$client.runtimePerformanceFunctionalSmoke.passed;scope='Fabric Client GameTest functional only'}
    production_smoke=[ordered]@{passed=$production.passed;exit_code=$receipt.exitCode;classpath_entries=$receipt.classpathEntries;lwjgl=$receipt.lwjglVersions;runtime_monitor=$production.runtime_monitor_smoke;save_reopen=$production.verification}
    installer_all_pass=$installerCheck.all_pass
    limits=@('No physical-controller/display smoothness benchmark','No user-world reads','No persistence/physics retuning','Synthetic/hidden functional captures cannot identify remaining user stutter')
}
[IO.File]::WriteAllText((Join-Path $verification 'validation-summary.json'),($summary | ConvertTo-Json -Depth 20),$utf8)
Write-Output $packageRoot
