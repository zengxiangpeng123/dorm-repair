@echo off
chcp 65001 >nul
cd /d "%~dp0"
if "%PORT%"=="" set PORT=5173
if "%DEMO%"=="" set DEMO=1
echo.
echo   宿舍维修系统 · 演示环境
echo   ---------------------------------------------
echo   学生端   http://localhost:%PORT%/student/
echo   师傅端   http://localhost:%PORT%/worker/
echo   管理端   http://localhost:%PORT%/admin/
echo   自检台   http://localhost:%PORT%/lab.html
echo   入口页   http://localhost:%PORT%/
echo   ---------------------------------------------
echo   第一次跑会自动建库并灌 14 张样本单，不用 npm install。
echo   关掉这个窗口 = 停服务。数据在 app\server\data\app.db。
echo.
start "" http://localhost:%PORT%/
node --no-warnings app/server/index.js
pause
