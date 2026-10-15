# 第 19 批：姿态栈 / 附魔常量名 / 游戏规则 / 背包槽位 / 效果 Holder
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # 姿态栈（Matrix3x2fStack）：只有 pushMatrix/popMatrix，且只有 2D 变换
    $t = $t.Replace('.pushPose()', '.pushMatrix()')
    $t = $t.Replace('.popPose()', '.popMatrix()')
    $t = [regex]::Replace($t, '(\w+)\.translate\(([^,()]+),\s*([^,()]+),\s*[^,()]+\)', '$1.translate($2, $3)')
    $t = [regex]::Replace($t, '(\w+)\.scale\(([^,()]+),\s*([^,()]+),\s*[^,()]+\)', '$1.scale($2, $3)')

    # 附魔常量改名
    $t = $t.Replace('Enchantments.POWER_ARROWS', 'Enchantments.POWER')
    $t = $t.Replace('Enchantments.PUNCH_ARROWS', 'Enchantments.PUNCH')
    $t = $t.Replace('Enchantments.FLAMING_ARROWS', 'Enchantments.FLAME')

    # 游戏规则读取
    $t = [regex]::Replace($t, '(\w+)\.getGameRules\(\)\.getIntOr\(GameRules\.RULE_RANDOMTICKING,\s*0\)',
        '$1.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED)')

    # 疲劳值：26.2 无读取器，直接清零到安全阈值以下
    $t = [regex]::Replace($t, '(\w+)\.addExhaustion\(-\1\.getExhaustion\(\)\)', '$1.addExhaustion(-4.0F)')

    # 背包槽位
    $t = [regex]::Replace($t, '(\w+)\.getInventory\(\)\.items', '$1.getInventory().getNonEquipmentItems()')
    $t = [regex]::Replace($t, '\.addAll\((\w+)\.getInventory\(\)\.armor\)',
        '.addAll(java.util.List.of($1.getInventory().getItem(36), $1.getInventory().getItem(37), $1.getInventory().getItem(38), $1.getInventory().getItem(39)))')
    $t = [regex]::Replace($t, '\.addAll\((\w+)\.getInventory\(\)\.offhand\)',
        '.addAll(java.util.List.of($1.getInventory().getItem(Inventory.SLOT_OFFHAND)))')

    # 效果实例的 Holder 取值
    $t = $t.Replace('event.getEffectInstance().getEffect()', 'event.getEffectInstance().getEffect().value()')
    $t = $t.Replace('inst.getEffect()', 'inst.getEffect().value()')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines19: modified $n files"
