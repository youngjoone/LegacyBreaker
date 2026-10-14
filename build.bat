@echo off
setlocal
cd /d "%~dp0"
if not defined LEGACYBREAKER_JDK goto usage
if not defined LEGACYBREAKER_IDEA goto usage
if not exist "%LEGACYBREAKER_JDK%\bin\javac.exe" goto usage
if not exist "%LEGACYBREAKER_IDEA%\build.txt" goto usage

if not exist build mkdir build
"%LEGACYBREAKER_JDK%\bin\javac.exe" -version > build\jdk-version.txt 2>&1
findstr /b /l /c:"javac 1.8." build\jdk-version.txt >nul
if errorlevel 1 (
    echo JDK 8 is required.
    type build\jdk-version.txt
    exit /b 1
)
findstr /b /l /c:"IC-183." /c:"IU-183." "%LEGACYBREAKER_IDEA%\build.txt" >nul
if errorlevel 1 (
    echo Provisional target is IDEA 2018.3. Review SDK compatibility and plugin.xml first.
    exit /b 1
)

if exist build\classes rmdir /s /q build\classes
if exist build\classes exit /b 1
mkdir build\classes
if not exist build\distributions mkdir build\distributions
rem 4차수정: 수정판 0.3.0 산출물 경로.
if exist build\distributions\legacybreaker-0.3.0.jar del /q build\distributions\legacybreaker-0.3.0.jar
rem 4차수정: 새 변수 탐색 Action도 컴파일 시작점에 포함한다.
"%LEGACYBREAKER_JDK%\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -proc:none -classpath "%LEGACYBREAKER_IDEA%\lib\*" -sourcepath src\main\java -d build\classes src\main\java\com\github\youngjoone\legacybreaker\action\ExploreCodeRelationsAction.java src\main\java\com\github\youngjoone\legacybreaker\action\TraceJspValueAction.java src\main\java\com\github\youngjoone\legacybreaker\action\ExploreVariableRelationsAction.java
if errorlevel 1 exit /b 1
xcopy /e /i /y src\main\resources build\classes >nul
if errorlevel 1 exit /b 1
rem 4차수정: 수정판 0.3.0 산출물 경로.
"%LEGACYBREAKER_JDK%\bin\jar.exe" cf build\distributions\legacybreaker-0.3.0.jar -C build\classes .
if errorlevel 1 exit /b 1
rem 4차수정: 수정판 0.3.0 산출물 경로.
echo Built build\distributions\legacybreaker-0.3.0.jar
exit /b 0

:usage
echo Set LEGACYBREAKER_JDK to an existing JDK 8 directory.
echo Set LEGACYBREAKER_IDEA to an existing IntelliJ IDEA 2018.3 directory.
exit /b 1
