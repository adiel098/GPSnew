@echo off
echo ========================================
echo GPS Particle Filter - Run Menu
echo ========================================
echo.
echo Select which application to run:
echo.
echo 1. Main GPS Particle Filter Application
echo 2. LOS/NLOS Test
echo 3. Coordinate Test Demo
echo 4. Accuracy Test Demo
echo 5. Exit
echo.
set /p choice="Enter your choice (1-5): "

if "%choice%"=="1" goto :main
if "%choice%"=="2" goto :lostest
if "%choice%"=="3" goto :coorddemo
if "%choice%"=="4" goto :accdemo
if "%choice%"=="5" goto :end

echo Invalid choice. Please try again.
pause
goto :menu

:main
echo.
echo Running Main GPS Particle Filter Application...
echo ========================================
java -cp "lib/*;classes" com.gps.particlefilter.Main
pause
goto :end

:lostest
echo.
echo Running LOS/NLOS Test...
echo ========================================
java -cp "lib/*;classes" com.gps.particlefilter.LosNlosTest
pause
goto :end

:coorddemo
echo.
echo Running Coordinate Test Demo...
echo ========================================
if exist classes\CoordinateTestDemo.class (
    java -cp "lib/*;classes" CoordinateTestDemo
) else (
    echo CoordinateTestDemo not compiled. Please run build.bat first.
)
pause
goto :end

:accdemo
echo.
echo Running Accuracy Test Demo...
echo ========================================
if exist classes\AccuracyTestDemo.class (
    java -cp "lib/*;classes" AccuracyTestDemo
) else (
    echo AccuracyTestDemo not compiled. Please run build.bat first.
)
pause
goto :end

:end
echo.
echo Exiting...