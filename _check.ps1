$found = $false
$base = Join-Path $env:USERPROFILE ".gradle\caches\forge_gradle"
Get-ChildItem -Path $base -Recurse -Filter *.jar | ForEach-Object {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        foreach ($e in $z.Entries) {
            if ($e.FullName -like '*WorkbenchMenu*' -or $e.FullName -like '*WorkBenchMenu*') {
                Write-Host ($_.FullName + '::' + $e.FullName)
                $found = $true
            }
        }
        $z.Dispose()
    } catch {}
}
# also check MenuType for WORKBENCH field in any jar
Get-ChildItem -Path $base -Recurse -Filter *.jar | ForEach-Object {
    try {
        $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        foreach ($e in $z.Entries) {
            if ($e.FullName -like '*/inventory/MenuType.class') {
                Write-Host ('MENUTYPE: ' + $_.FullName)
                $found = $true
            }
        }
        $z.Dispose()
    } catch {}
}
Write-Host ("DONE " + $found)
