@echo off
rem ============================================================
rem  Infinite Stats build helper  (Forge 1.19.2)
rem  (Keep this file ASCII-only: cmd parses .bat with the OEM code page,
rem   non-ASCII bytes break batch parsing under chcp 936.)
rem
rem  Usage (double-click = no argument):
rem    build.bat                 incremental build, copy jar to dist\
rem    build.bat clean           clean build
rem    build.bat nocopy          build only, no copy
rem    build.bat check           compile only (fastest sanity check)
rem    build.bat deploy          build + dist\ + install into the modpack mods\
rem    build.bat deploy "PATH"   install into the given mods folder
rem
rem  Output:
rem    build\libs\infinite_stats-<cn name>-<version>-Forge-1.19.2.jar
rem ============================================================

setlocal enabledelayedexpansion
cd /d "%~dp0"

set "MODE=%~1"
set "TARGET=%~2"
set "EXIT_CODE=0"
if "%MODE%"=="" set "MODE=build"

set "DO_CLEAN=0"
set "DO_COPY=1"
set "DO_DEPLOY=0"
if /i "%MODE%"=="clean"  set "DO_CLEAN=1"
if /i "%MODE%"=="nocopy" set "DO_COPY=0"
if /i "%MODE%"=="deploy" set "DO_DEPLOY=1"
if /i "%MODE%"=="check"  goto :check

echo ============================================================
echo  Infinite Stats - build (Forge 1.19.2)
echo ============================================================

if "%DO_CLEAN%"=="1" (
    echo [1/3] clean + build ^(full rebuild, slower^) ...
    call gradlew.bat clean build compileGameTestJava --console=plain
) else (
    echo [1/3] build ^(incremental; use "build.bat clean" for a clean build^) ...
    call gradlew.bat build compileGameTestJava --console=plain
)
if errorlevel 1 goto :fail

echo [2/3] locating artifact ...
rem 按 gradle.properties 里的 mod_version 精确定位本次产物：
rem build\libs 会累积旧版本的 jar，只按文件名匹配会挑错。
set "VER="
for /f "tokens=2 delims==" %%A in ('findstr /b /c:"mod_version=" gradle.properties') do set "VER=%%A"

set "JAR="
set "JARNAME="
if defined VER for %%F in ("build\libs\*-%VER%-Forge-*.jar") do (
    set "JAR=%%~fF"
    set "JARNAME=%%~nxF"
)

if not defined JAR (
    echo         [WARN] cannot read mod_version, falling back to any jar
    for %%F in ("build\libs\*.jar") do (
        set "JAR=%%~fF"
        set "JARNAME=%%~nxF"
    )
)
if not defined JAR goto :nojar

for %%F in ("%JAR%") do echo         artifact: %%~nxF ^(%%~zF bytes^)

rem 提示一下 build\libs 里遗留的其它版本，免得手动分发时拿错
for %%F in ("build\libs\*.jar") do (
    set "N=%%~nxF"
    if not "!N!"=="%JARNAME%" echo         stale in build\libs: !N!
)

rem Sanity check: the refmap must be inside the jar (build.gradle already fails
rem the build when it is missing; this line is just for visibility).
tar -tf "%JAR%" 2>nul | findstr /c:"infinitestats.refmap.json" >nul
if errorlevel 1 (
    echo         [WARN] refmap not found in jar - Mixin will fail at runtime, please rebuild
) else (
    echo         refmap: ok
)

if "%DO_COPY%"=="1" (
    if not exist "dist" mkdir "dist"
    del /q "dist\infinite_stats-*.jar" >nul 2>nul
    copy /y "%JAR%" "dist\" >nul
    echo [3/3] copied to dist\
) else (
    echo [3/3] copy skipped ^(nocopy^)
)

if "%DO_DEPLOY%"=="1" (
    set "MODS_DIR=%TARGET%"
    if not defined MODS_DIR (
        rem Auto-detect the modpack that already has this mod installed, so that no
        rem non-ASCII path has to be hardcoded here.
        for /d %%D in ("d:\pcl\.minecraft\versions\*") do (
            if exist "%%~fD\mods\infinite_stat*.jar" set "MODS_DIR=%%~fD\mods"
        )
    )
    if defined MODS_DIR (
        del /q "!MODS_DIR!\infinite_stats-*.jar" >nul 2>nul
        copy /y "%JAR%" "!MODS_DIR!\" >nul
        if errorlevel 1 (
            echo        [FAIL] cannot write to !MODS_DIR!
            set "EXIT_CODE=1"
        ) else (
            echo        [deploy] replaced the old jar in !MODS_DIR!
        )
    ) else (
        echo        [hint] no modpack with this mod found; pass the folder yourself:
        echo               build.bat deploy "D:\...\versions\PACK\mods"
    )
)

echo ============================================================
if "%DO_COPY%"=="1" (
    echo  done: dist\%JARNAME%
) else (
    echo  done: build\libs\%JARNAME%
)
echo ============================================================
goto :done

:check
echo [check] compile only ^(compileJava + compileGameTestJava^) ...
call gradlew.bat compileJava compileGameTestJava --console=plain
if errorlevel 1 goto :fail
echo ============================================================
echo  compile ok ^(no jar produced; run "build.bat" for the package^)
echo ============================================================
goto :done

:nojar
echo [ERROR] build finished but no jar found in build\libs; paste the log above.
set "EXIT_CODE=1"
goto :done

:fail
echo ============================================================
echo  BUILD FAILED - see the Gradle error above.
echo   * compile error  -^> fix the code and run again
echo   * missing refmap -^> see the check in build.gradle
echo ============================================================
set "EXIT_CODE=1"

:done
rem Keep the window open when double-clicked. From a terminal set NO_PAUSE=1
rem (or just press Enter) to skip it.
if not defined NO_PAUSE pause
exit /b %EXIT_CODE%
