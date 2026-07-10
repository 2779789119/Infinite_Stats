@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set JAVA_HOME=C:\Program Files\Java\jdk-17

@rem Resolve "." and ".." in APP_HOME
set APP_HOME=%APP_HOME:"=%

@rem Add default JVM options here.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if exist "%JAVA_HOME%\bin\java.exe" goto init

@rem Try to find java in PATH
for %%i in (java.exe) do set JAVA_EXE=%%~$PATH:i
goto execute

:init
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
