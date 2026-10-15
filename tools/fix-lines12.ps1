# 第 12 批：按钮调用末尾多余参数 / 带参数的 send / imageWidth 赋值
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # 删除 text(...) 之后被错误追加的网络目标（button(...) 调用的尾部）
    $t = [regex]::Replace($t, '(text\([^()]*\))\,\s*PacketDistributor\.\w+\.noArg\(\)\);', '$1);')

    # send(new X(args)) -> 补目标
    $t = [regex]::Replace($t, '\.send\((new [A-Za-z0-9_.]+\([^()]*\))\)', '.send($1, PacketDistributor.SERVER.noArg())')

    # imageWidth/imageHeight 单独赋值并入 super
    $t = [regex]::Replace($t, 'super\(menu, inv, title\);\s*\r?\n\s*imageWidth = (\w+);\s*\r?\n\s*imageHeight = (\w+);',
                              'super(menu, inv, title, $1, $2);')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines12: modified $n files"
