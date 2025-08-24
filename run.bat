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
echo 5. Generate Fig 14 Convergence Chart
echo 6. Generate Fig 15 Naive vs Bayesian Chart
echo 7. Generate Fig 19 LOS/NLOS Misclassification Chart
echo 8. Exit
echo.
set /p choice="Enter your choice (1-8): "

if "%choice%"=="1" goto :main
if "%choice%"=="2" goto :lostest
if "%choice%"=="3" goto :coorddemo
if "%choice%"=="4" goto :accdemo
if "%choice%"=="5" goto :fig14
if "%choice%"=="6" goto :fig15
if "%choice%"=="7" goto :fig19
if "%choice%"=="8" goto :end

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

:fig14
echo.
echo === Fig 14 Chart Generation ===
echo Convergence error for different number of particles
echo This will run 4 simulations with different particle counts (100, 500, 1000, 2500)
echo.

echo Step 1: Running particle filter simulations...
echo This may take several minutes depending on your data size...
echo.

:: Run the batch runner to collect data
java -cp "lib/*;classes" com.gps.particlefilter.ParticleFilterBatchRunner convergence

if %errorlevel% neq 0 (
    echo ERROR: Particle filter simulations failed
    pause
    goto :end
)

echo.
echo Simulations complete! Data collected in charts/data/convergence_data.csv
echo.

echo Step 2: Checking Python environment...

:: Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python from https://www.python.org/downloads/
    echo Then run charts/install_requirements.bat to install dependencies
    pause
    goto :end
)

:: Check if required packages are installed
echo Checking required Python packages...
python -c "import matplotlib, pandas, numpy" >nul 2>&1
if %errorlevel% neq 0 (
    echo Installing required Python packages...
    cd charts
    pip install -r requirements.txt
    if %errorlevel% neq 0 (
        echo ERROR: Failed to install Python packages
        echo Please run charts/install_requirements.bat manually
        cd ..
        pause
        goto :end
    )
    cd ..
)

echo Python environment ready!
echo.

echo Step 3: Generating Fig 14 chart...

:: Change to charts directory and run Python script
cd charts
python fig14_convergence.py

if %errorlevel% neq 0 (
    echo ERROR: Chart generation failed
    cd ..
    pause
    goto :end
)

cd ..

echo.
echo === Fig 14 Generation Complete! ===
echo.
echo Results:
echo   - Simulation data: charts/data/convergence_data.csv
echo   - Generated chart: charts/output/fig14_convergence.png
echo.

:: Try to open the generated image
if exist "charts\output\fig14_convergence.png" (
    echo Opening the generated chart...
    start "" "charts\output\fig14_convergence.png"
)

pause
goto :end

:fig15
echo.
echo === Fig 15 Chart Generation ===
echo Naive vs Bayesian weight function comparison
echo This will compare particle filters with and without memory for 100 and 1000 particles
echo.

echo Step 1: Running particle filter simulations...
echo This may take several minutes depending on your data size...
echo.

:: Run the batch runner to collect data
java -cp "lib/*;classes" com.gps.particlefilter.ParticleFilterBatchRunner naive-bayesian

if %errorlevel% neq 0 (
    echo ERROR: Particle filter simulations failed
    pause
    goto :end
)

echo.
echo Simulations complete! Data collected in charts/data/naive_bayesian_data.csv
echo.

echo Step 2: Checking Python environment...

:: Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python from https://www.python.org/downloads/
    echo Then run charts/install_requirements.bat to install dependencies
    pause
    goto :end
)

:: Check if required packages are installed
echo Checking required Python packages...
python -c "import matplotlib, pandas, numpy" >nul 2>&1
if %errorlevel% neq 0 (
    echo Installing required Python packages...
    cd charts
    pip install -r requirements.txt
    if %errorlevel% neq 0 (
        echo ERROR: Failed to install Python packages
        echo Please run charts/install_requirements.bat manually
        cd ..
        pause
        goto :end
    )
    cd ..
)

echo Python environment ready!
echo.

echo Step 3: Generating Fig 15 chart...

:: Change to charts directory and run Python script
cd charts
python fig15_naive_bayesian.py

if %errorlevel% neq 0 (
    echo ERROR: Chart generation failed
    cd ..
    pause
    goto :end
)

cd ..

echo.
echo === Fig 15 Generation Complete! ===
echo.
echo Results:
echo   - Simulation data: charts/data/naive_bayesian_data.csv
echo   - Generated chart: charts/output/fig15_naive_bayesian.png
echo.

:: Try to open the generated image
if exist "charts\output\fig15_naive_bayesian.png" (
    echo Opening the generated chart...
    start "" "charts\output\fig15_naive_bayesian.png"
)

pause
goto :end

:fig19
echo.
echo === Fig 19 Chart Generation ===
echo LOS/NLOS Misclassification Error Analysis - 2500 Particles
echo This will analyze the impact of LOS/NLOS misclassification errors (p=0%, 10%, 20%, 45%)
echo.

echo Step 1: Running particle filter simulations...
echo This may take several minutes depending on your data size...
echo.

:: Run the batch runner to collect data
java -cp "lib/*;classes" com.gps.particlefilter.ParticleFilterBatchRunner los-nlos

if %errorlevel% neq 0 (
    echo ERROR: Particle filter simulations failed
    pause
    goto :end
)

echo.
echo Simulations complete! Data collected in charts/data/los_nlos_misclassification_data.csv
echo.

echo Step 2: Checking Python environment...

:: Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python from https://www.python.org/downloads/
    echo Then run charts/install_requirements.bat to install dependencies
    pause
    goto :end
)

:: Check if required packages are installed
echo Checking required Python packages...
python -c "import matplotlib, pandas, numpy" >nul 2>&1
if %errorlevel% neq 0 (
    echo Installing required Python packages...
    cd charts
    pip install -r requirements.txt
    if %errorlevel% neq 0 (
        echo ERROR: Failed to install Python packages
        echo Please run charts/install_requirements.bat manually
        cd ..
        pause
        goto :end
    )
    cd ..
)

echo Python environment ready!
echo.

echo Step 3: Generating Fig 19 chart...

:: Change to charts directory and run Python script
cd charts
python fig19_los_nlos_misclassification.py

if %errorlevel% neq 0 (
    echo ERROR: Chart generation failed
    cd ..
    pause
    goto :end
)

cd ..

echo.
echo === Fig 19 Generation Complete! ===
echo.
echo Results:
echo   - Simulation data: charts/data/los_nlos_misclassification_data.csv
echo   - Generated chart: charts/output/fig19_los_nlos_misclassification.png
echo.

:: Try to open the generated image
if exist "charts\output\fig19_los_nlos_misclassification.png" (
    echo Opening the generated chart...
    start "" "charts\output\fig19_los_nlos_misclassification.png"
)

pause
goto :end

:end
echo.
echo Exiting...