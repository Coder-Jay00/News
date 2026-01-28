@echo off
REM Navigate to project directory
cd /d "c:\Users\Admin\OneDrive\Desktop\News"

REM Activate Virtual Environment
call venv\Scripts\activate.bat

REM Run the Intelligence Pipeline
python backend\main.py

REM Optional: Pause to see output if run manually (remove if automated)
REM pause
