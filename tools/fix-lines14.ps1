# 第 14 批：字节缓冲标识符 / GameRules 包 / 世界高度 / 引燃 / 耐久 / 权限 / 疲劳
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # FriendlyByteBuf 的标识符方法改名
    $t = $t.Replace('.writeResourceLocation(', '.writeIdentifier(')
    $t = $t.Replace('.readResourceLocation(', '.readIdentifier(')

    # GameRules 换包
    $t = $t.Replace('import net.minecraft.world.level.GameRules;', 'import net.minecraft.world.level.gamerules.GameRules;')

    # 世界高度访问器
    $t = $t.Replace('.getMinBuildHeight()', '.getMinY()')
    $t = $t.Replace('getMaxBuildHeight() - 1', 'getMaxY() - 1')

    # 引燃物品/实体
    $t = [regex]::Replace($t, '(\w+)\.setSecondsOnFire\(100\)', '$1.igniteForSeconds(5.0F)')

    # 耐久损耗（26.2 需要手持槽位）
    $t = [regex]::Replace($t, '(\w+)\.hurtAndBreak\(1, (\w+), p -> p\.broadcastBreakEvent\((\w+)\.getUsedItemHand\(\)\)\)',
                              '$1.hurtAndBreak(1, $2, $3.getUsedItemHand())')

    # 权限节点
    $t = [regex]::Replace($t, '\.hasPermission\(2\)',
        '.hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)')

    # 疲劳值：26.2 只有 addExhaustion
    $t = [regex]::Replace($t, '(\w+)\.setExhaustion\(0\.0f\)', '$1.addExhaustion(-$1.getExhaustion())')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines14: modified $n files"
