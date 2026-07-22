Add-Type -AssemblyName System.IO.Compression.FileSystem
$base = Join-Path $env:USERPROFILE ".gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.15_mapped_official_1.20.1"
$jars = @(
    "$base\forge-1.20.1-47.4.15_mapped_official_1.20.1-sources.jar",
    "$base\forge-1.20.1-47.4.15_mapped_official_1.20.1-recomp.jar",
    "$base\forge-1.20.1-47.4.15_mapped_official_1.20.1.jar"
)
foreach ($j in $jars) {
    if (-not (Test-Path $j)) { Write-Host ("MISSING " + $j); continue }
    Write-Host ("=== " + $j)
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($j)
        $z.Entries | Where-Object { $_.FullName -like '*world/inventory/*' -and ($_.FullName -like '*Crafting*' -or $_.FullName -like '*Workbench*' -or $_.FullName -like '*WorkBench*' -or $_.FullName -like '*MenuType*') } | ForEach-Object { Write-Host $_.FullName }
        $z.Dispose()
    } catch { Write-Host ("ERR " + $_) }
}
