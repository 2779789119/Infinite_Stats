# 第 10 批：PacketDistributor import / 单参 send 统一
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # 单参 send(X) -> send(X, SERVER.noArg())
    $t = [regex]::Replace($t, '\.send\((new [A-Za-z0-9_.]+\(\))\)', '.send($1, PacketDistributor.SERVER.noArg())')

    # 补 import
    if ($t -match 'PacketDistributor\.' -and $t -notmatch 'import net\.minecraftforge\.network\.PacketDistributor;') {
        if ($t -match 'import net\.minecraftforge\.network\.SimpleChannel;') {
            $t = $t.Replace("import net.minecraftforge.network.SimpleChannel;",
                "import net.minecraftforge.network.SimpleChannel;`r`nimport net.minecraftforge.network.PacketDistributor;")
        } elseif ($t -match '^import ') {
            $t = [regex]::Replace($t, '^(import [^\r\n]+;)', "`$1`r`nimport net.minecraftforge.network.PacketDistributor;", 'Multiline')
        } else {
            $t = [regex]::Replace($t, '^(package [^\r\n]+;)', "`$1`r`n`r`nimport net.minecraftforge.network.PacketDistributor;", 'Multiline')
        }
    }

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines10: modified $n files"
