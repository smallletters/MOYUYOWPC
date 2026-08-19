' MOYUYO 后端守护启动脚本
' 双层调用：cscript -> start-bg.bat -> java.exe
' bat 启动后窗口立即消失，java 进程独立运行
Set WshShell = CreateObject("WScript.Shell")
' 第三个参数 0 = 隐藏窗口
WshShell.Run "D:\MOYUYOWPC\moyuyo-server\start-bg.bat", 0, False
' 脚本立即结束，但 wscript.exe 进程与 java.exe 是平行的进程树