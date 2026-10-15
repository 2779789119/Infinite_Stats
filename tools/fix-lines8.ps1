# 第 8 批：sendToServer / setTag / 配方输入 / GUI 绘制残留
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # 26.2 的 SimpleChannel 没有 sendToServer/sendToClient，改用 PacketDistributor 目标
    $t = [regex]::Replace($t, '(\w+)\.sendToServer\(([^;]+)\)', '$1.send($2, PacketDistributor.SERVER.noArg())')
    $t = [regex]::Replace($t, '(\w+)\.sendToClient\(([^,;]+),\s*(\w+)\)', '$1.send($2, PacketDistributor.PLAYER.with($3))')
    $t = [regex]::Replace($t, '(\w+)\.sendToClient\(([^;]+)\)', '$1.send($2, PacketDistributor.ALL.noArg())')

    # ItemStack.setTag 的其余形式
    $t = [regex]::Replace($t, '(\w+)\.setTag\(([^;()]+)\)',
        'net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, $1, $2)')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines8: modified $n files"
