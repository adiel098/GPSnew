@echo off
echo ========================================
echo Building GPS Particle Filter Project
echo ========================================
echo.

REM Create classes directory if it doesn't exist
if not exist classes mkdir classes

echo Step 1: Compiling Configuration classes...
javac -cp "lib/*" -d classes src/main/java/com/gps/particlefilter/config/*.java
if errorlevel 1 goto :error

echo Step 2: Compiling Model classes...
javac -cp "lib/*;classes" -d classes src/main/java/com/gps/particlefilter/model/*.java
if errorlevel 1 goto :error

echo Step 3: Compiling Utility classes...
javac -cp "lib/*;classes" -d classes src/main/java/com/gps/particlefilter/util/*.java
if errorlevel 1 goto :error

echo Step 4: Compiling LOS classes...
javac -cp "lib/*;classes" -d classes src/main/java/com/gps/particlefilter/los/*.java
if errorlevel 1 goto :error

echo Step 5: Compiling IO classes...
javac -cp "lib/*;classes" -d classes src/main/java/com/gps/particlefilter/io/*.java
if errorlevel 1 goto :error

echo Step 6: Compiling Main application classes...
javac -cp "lib/*;classes" -d classes src/main/java/com/gps/particlefilter/*.java
if errorlevel 1 goto :error

echo Step 7: Compiling Test classes...
javac -cp "lib/*;classes" -d classes src/test/java/com/gps/particlefilter/*.java
if errorlevel 1 goto :error

echo Step 8: Compiling demo files (if they exist)...
if exist CoordinateTestDemo.java (
    javac -cp "lib/*;classes" -d classes CoordinateTestDemo.java
    if errorlevel 1 echo Warning: Could not compile CoordinateTestDemo.java
)
if exist AccuracyTestDemo.java (
    javac -cp "lib/*;classes" -d classes AccuracyTestDemo.java
    if errorlevel 1 echo Warning: Could not compile AccuracyTestDemo.java
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo To run the applications, use one of these commands:
echo.
echo 1. Main GPS Particle Filter Application:
echo    java -cp "lib/*;classes" com.gps.particlefilter.Main
echo.
echo 2. LOS/NLOS Test:
echo    java -cp "lib/*;classes" com.gps.particlefilter.LosNlosTest
echo.
echo 3. Coordinate Test Demo (if available):
echo    java -cp "lib/*;classes" CoordinateTestDemo
echo.
echo 4. Accuracy Test Demo (if available):
echo    java -cp "lib/*;classes" AccuracyTestDemo
echo.
echo 5. Batch Runner for Chart Data Generation:
echo    java -cp "lib/*;classes" com.gps.particlefilter.ParticleFilterBatchRunner [convergence^|naive-bayesian^|los-nlos]
echo    - convergence: Generate Fig 14 data
echo    - naive-bayesian: Generate Fig 15 data  
echo    - los-nlos: Generate Fig 19 data
echo.
echo Note: If you encounter errors with newer Java versions, add:
echo    --add-opens java.base/java.lang=ALL-UNNAMED
echo.
goto :end

:error
echo.
echo ========================================
echo BUILD FAILED!
echo ========================================
echo Please check the error messages above.
echo Common issues:
echo - Missing lib folder with required JAR files
echo - Java not installed or not in PATH
echo - Source files missing or have syntax errors
echo.

:end