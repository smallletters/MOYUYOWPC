$Root = 'D:\MOYUYOWPC\moyuyo-server'
$migDir = $Root + '\moyuyo-api\src\main\resources\db\migration'
Add-Content -Path D:\MOYUYOWPC\moyuyo-server\debug-out.txt -Value ('Root=[' + $Root + ']')
Add-Content -Path D:\MOYUYOWPC\moyuyo-server\debug-out.txt -Value ('migDir=[' + $migDir + ']')
Add-Content -Path D:\MOYUYOWPC\moyuyo-server\debug-out.txt -Value ('len=' + $migDir.Length)
Add-Content -Path D:\MOYUYOWPC\moyuyo-server\debug-out.txt -Value ('TestPath=' + (Test-Path $migDir))
