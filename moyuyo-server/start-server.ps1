$JarPath = "D:\TRAED_IDE\TRAED_workspace\MOYUYOWAC\MOYUYOWPC\moyuyo-server\moyuyo-api\target\moyuyo-api-1.0.0.jar"
$LogFile = "D:\TRAED_IDE\TRAED_workspace\MOYUYOWAC\MOYUYOWPC\moyuyo-server\server-output.log"

Write-Host "正在启动 MOYUYO 后端服务..."
java -jar $JarPath *>&1 | Out-File -FilePath $LogFile -Encoding utf8
Write-Host "服务进程已退出，退出码：$LASTEXITCODE"
