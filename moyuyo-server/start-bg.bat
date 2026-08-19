@echo off
REM MOYUYO 后端启动批处理（通过 cscript 调用时不会弹窗）
java -Dspring.profiles.active=dev -jar "D:\MOYUYOWPC\moyuyo-server\moyuyo-api\target\moyuyo-api-1.0.0.jar" > "D:\MOYUYOWPC\moyuyo-server\server-out.log" 2> "D:\MOYUYOWPC\moyuyo-server\server-err.log"