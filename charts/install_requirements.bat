@echo off
echo === Installing Python Requirements for Chart Generation ===
echo.

REM Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python from https://www.python.org/downloads/
    echo Make sure to add Python to PATH during installation
    pause
    exit /b 1
)

echo Python found:
python --version

REM Check if pip is available
pip --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: pip is not available
    echo Please ensure pip is installed with Python
    pause
    exit /b 1
)

echo.
echo Installing required packages...
echo.

REM Install requirements
pip install -r requirements.txt

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to install requirements
    echo Please check your internet connection and try again
    pause
    exit /b 1
)

echo.
echo === Installation Complete ===
echo All required Python packages have been installed successfully!
echo You can now run generate_fig14.bat to create the charts.
echo.
pause