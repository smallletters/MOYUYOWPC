@echo off
REM MOYUYO 后端启动批处理（通过 cscript 调用时不会弹窗）
REM 启动前通过 load-env.ps1 加载 .env，确保 MOYUYO_CORS_ALLOWED_ORIGINS 等环境变量在 bat 启动下也能生效
REM 与 start-with-env.ps1 行为对齐；这样 .env 修改后无需切换启动方式

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0load-env.ps1"
if errorlevel 9009 (
    echo [start-bg] powershell.exe 未在 PATH 中，跳过 .env 加载（dev 兜底生效中）
) else if errorlevel 1 (
    echo [start-bg] .env 文件不存在或加载失败，仅依赖 application-dev.yml 默认值
)

java -Dspring.profiles.active=dev -jar "D:\MOYUYOWPC\moyuyo-server\moyuyo-api\target\moyuyo-api-1.0.0.jar" > "D:\MOYUYOWPC\moyuyo-server\server-out.log" 2> "D:\MOYUYOWPC\moyuyo-server\server-err.log"