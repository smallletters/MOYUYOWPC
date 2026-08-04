$body = '{"email":"admin","password":"123456"}'
$resp = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/auth/login" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 10 -ErrorAction Continue
$resp | ConvertTo-Json -Depth 4
