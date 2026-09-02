$f = 'D:\MOYUYOWPC\moyuyo-server\moyuyo-api\src\main\resources\db\migration\V2__init_business_tables.sql'
$content = Get-Content $f.FullName -Raw
Write-Host ('len=' + $content.Length)
$opt = [System.Text.RegularExpressions.RegexOptions]::Singleline
$pattern = 'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`"\s]?(mo_[a-zA-Z0-9_]+)'
$matches2 = [System.Text.RegularExpressions.Regex]::Matches($content, $pattern, $opt)
Write-Host ('matches=' + $matches2.Count)
foreach ($m in $matches2) {
    Write-Host (' -> ' + $m.Groups[1].Value)
}
