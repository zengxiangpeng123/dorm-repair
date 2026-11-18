@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ================================================
echo  宿舍维修 AI 定级服务  http://127.0.0.1:8000
echo  接口：GET /health   POST /classify   GET /rules
echo  关掉这个黑窗口就是停服务（后端会自动走兜底）
echo ================================================
D:\develop\python-envs\dorm-ai\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
pause
