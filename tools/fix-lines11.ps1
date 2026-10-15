# 第 11 批：修复「跨行 CHANNEL.send(packet)」——上一行以 .send( 结尾时，给 packet 行补网络目标
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $content = [System.IO.File]::ReadAllText($f.FullName)
    $sep = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $arr = New-Object System.Collections.Generic.List[string]
    $arr.AddRange($content -split "`r?`n")
    $dirty = $false
    for ($i = 1; $i -lt $arr.Count; $i++) {
        $prev = $arr[$i - 1].TrimEnd()
        $cur  = $arr[$i]
        if ($prev.EndsWith('.send(') -and $cur -match '^\s*new .*\),\s*$') {
            $arr[$i] = [regex]::Replace($cur, '^\s*new (.*)\),\s*$', '                new $1, PacketDistributor.SERVER.noArg()),')
            $dirty = $true
        }
    }
    if ($dirty) { [System.IO.File]::WriteAllText($f.FullName, ($arr -join $sep), $enc); $n++ }
}
Write-Output "fix-lines11: modified $n files"
