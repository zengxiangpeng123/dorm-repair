@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================================
echo   宿舍维修系统 · 正式项目（Spring Boot 4 + MySQL + Python AI）
echo ------------------------------------------------------------
echo   后端  http://localhost:8082/api/v1/health
echo   自检  http://localhost:8082/api/v1/state/matrix
echo   AI    http://localhost:8001/health
echo   学生端 cd app\student-web ^&^& npm run dev  ->  http://localhost:5173/student/
echo   师傅端 cd app\worker-web  ^&^& npm run dev  ->  http://localhost:5174/worker/
echo   管理端 cd app\admin-web   ^&^& npm run dev  ->  http://localhost:5175/admin/
echo ------------------------------------------------------------
echo   AI/后端由本脚本启动；三个前端需另开三个终端。关掉窗口就是停服务。
echo   数据库：MySQL 8 的 dorm_repair 库（root/123456）
echo   演示模式：set APP_DEMO=true 后才可重置/快进演示数据
echo ============================================================
echo.

start "宿舍维修 AI" cmd /k "cd /d %~dp0app\ai && D:\develop\python-envs\dorm-ai\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001"

timeout /t 3 >nul
start "" http://localhost:8082/api/v1/health

if "%APP_DEMO%"=="" set APP_DEMO=true
if "%DB_USER%"=="" set DB_USER=root
if "%DB_PASSWORD%"=="" set DB_PASSWORD=123456
cd /d "%~dp0app\server"
if not exist "target\dorm-repair-server-1.0.0.jar" (
  echo 第一次跑，先打包……
  call mvn -q -DskipTests package
)
java -jar target\dorm-repair-server-1.0.0.jar
pause
