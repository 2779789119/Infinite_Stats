# 第 21 批：Optional getter / 效果 Holder / 箭矢属性 / 烹饪时长 / display 标签
$ErrorActionPreference = 'Stop'
$root = "d:\mod\infinitestats-forge-26_2"
$enc = New-Object System.Text.UTF8Encoding $false
$files = Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java
$n = 0
foreach ($f in $files) {
    $t = [System.IO.File]::ReadAllText($f.FullName); $o = $t

    # Optional 化 getter 漏网
    $t = [regex]::Replace($t, '(\w+)\.getFloat\("(\w+)"\)', '$1.getFloatOr("$2", 0.0f)')
    $t = [regex]::Replace($t, '(\w+)\.getShort\("(\w+)"\)', '$1.getShortOr("$2", (short) 0)')
    $t = [regex]::Replace($t, '(\w+)\.getString\((\w+)\)', '$1.getStringOr($2, "")')

    # 1.20 的 display 标签在 26.2 已组件化，这里退化为空标签（名称/描述已由组件承载）
    $t = $t.Replace('stack.getCompoundOrEmpty("display")', 'new CompoundTag()')

    # 药水效果：注册表按 MobEffect 查键，实体接口按 Holder 接收
    $t = $t.Replace('getKey(event.getEffectInstance().getEffect()).toString()',
                    'getKey(event.getEffectInstance().getEffect().value()).toString()')
    $t = $t.Replace('player.removeEffect(inst.getEffect().value())', 'player.removeEffect(inst.getEffect())')

    # 箭矢：26.2 无 getBaseDamage，基础伤害固定为原版的 2.0
    $t = $t.Replace('arrow.getBaseDamage() + powerLevel * 0.5 + 0.5', '2.0 + powerLevel * 0.5 + 0.5')

    # 烹饪时长（record 访问器）
    $t = $t.Replace('recipe.getCookingTime()', 'recipe.cookingTime()')

    if ($t -ne $o) { [System.IO.File]::WriteAllText($f.FullName, $t, $enc); $n++ }
}
Write-Output "fix-lines21: modified $n files"
