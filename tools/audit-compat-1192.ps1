# Audit which hard-coded third-party classes still exist on the 1.19.2 sources.
# Source of truth: GitHub repos, file tree of the 1.19.x branch / tag. No jar download.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\audit-compat-1192.ps1
# NOTE: keep this file ASCII-only (PowerShell 5.1 reads .ps1 as ANSI without a BOM).
$ErrorActionPreference = 'Continue'
$ua = @{ 'User-Agent' = 'compat-audit'; 'Accept' = 'application/vnd.github+json' }

function Get-Branches($repo) {
    try { return (Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/branches?per_page=100" -Headers $ua -TimeoutSec 25 | ForEach-Object { $_.name }) }
    catch { return @() }
}

function Get-Tags($repo) {
    try { return (Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/tags?per_page=100" -Headers $ua -TimeoutSec 25 | ForEach-Object { $_.name }) }
    catch { return @() }
}

function Test-Tree($repo, $ref, $want) {
    try {
        $tree = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/trees/$ref`?recursive=1" -Headers $ua -TimeoutSec 60
    } catch {
        return "tree read failed"
    }
    $paths = $tree.tree | Where-Object { $_.type -eq 'blob' } | ForEach-Object { $_.path }
    $miss = @($want | Where-Object { $w = $_; -not ($paths | Where-Object { $_ -like "*/$w.java" }) })
    if ($miss.Count -eq 0) { return "all $($want.Count) classes present  [OK]" }
    return "MISSING: $($miss -join ', ')"
}

$targets = @(
    @{ mod = 'Create';                     repo = 'Creators-of-Create/Create';                want = @('KineticBlockEntity','KineticNetwork');                      ref = 'mc.*1\.19' },
    @{ mod = 'Apotheosis';                 repo = 'Shadows-of-Fire/Apotheosis';               want = @('EnchHooks');                                                ref = '^1\.19' },
    @{ mod = 'Curios';                     repo = 'TheIllusiveC4/Curios';                     want = @('CuriosApi','ISlotHelper','ISlotType');                      ref = '^1\.19' },
    @{ mod = 'ProjectE';                   repo = 'sinkillerj/ProjectE';                      want = @('ProjectEAPI');                                              ref = '1\.19' },
    @{ mod = 'AE2';                        repo = 'AppliedEnergistics/Applied-Energistics-2'; want = @('AEKey','AEItemKey','WirelessTerminalItem','IActionSource');  ref = '1\.19' },
    @{ mod = 'Waystones';                  repo = 'blay09/Waystones';                         want = @('WaystonesAPI','IWaystone');                                 ref = '^1\.19' },
    @{ mod = 'Polymorph';                  repo = 'illusivesoulworks/polymorph';              want = @('PolymorphApi','IPolymorphCommon');                           ref = '^1\.19' },
    @{ mod = 'IronsSpells';                repo = 'Iron431/irons-spells-n-spellbooks';        want = @('MagicData','AttributeRegistry');                            ref = '^1\.19' },
    @{ mod = 'ArsNouveau';                 repo = 'baileyholl/Ars-Nouveau';                   want = @('IManaCap','CapabilityRegistry');                             ref = '^1\.19' },
    @{ mod = 'Botania';                    repo = 'VazkiiMods/Botania';                       want = @('ManaItemHandler','ManaItem');                                ref = '^1\.19' },
    @{ mod = 'TomsStorage';                repo = 'tom5454/Toms-Storage';                     want = @('Config');                                                   ref = '^1\.19' },
    @{ mod = 'Goety';                      repo = 'Polarice3/Goety';                          want = @('SEHelper','TotemFinder','ITotem');                          ref = '.' },
    @{ mod = 'RefinedStorage';             repo = 'refinedmods/refinedstorage';               want = @('IRSAPI','NetworkItem');                                     ref = '^1\.19'; tagPattern = '^v1\.11' },
    @{ mod = 'SlashBlade';                 repo = 'flammpfeil/SlashBlade';                    want = @('ItemSlashBlade','ISlashBladeState','SlashArtsRegistry');    ref = '^1\.19' },
    @{ mod = 'SophisticatedBackpacks';     repo = 'P3pp3rF1y/SophisticatedBackpacks';         want = @('CapabilityBackpackWrapper');                                ref = '^1\.19' }
)

foreach ($t in $targets) {
    $branches = @(Get-Branches $t.repo)
    if ($branches.Count -eq 0) { Write-Output ("{0,-24} repo not reachable" -f $t.mod); continue }

    $ref = $branches | Where-Object { $_ -match $t.ref } | Select-Object -First 1
    if (-not $ref -and $t.tagPattern) {
        $ref = @(Get-Tags $t.repo | Where-Object { $_ -match $t.tagPattern }) | Select-Object -First 1
    }
    if (-not $ref) {
        Write-Output ("{0,-24} no 1.19 ref  (branches: {1})" -f $t.mod, (($branches | Select-Object -First 8) -join ','))
        continue
    }
    $line = Test-Tree $t.repo $ref $t.want
    Write-Output ("{0,-24} {1,-26} {2}" -f $t.mod, $ref, $line)
    Start-Sleep -Milliseconds 250
}
