@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem ---- 模式与密钥 ----
rem 密钥只从环境变量读，不写进这个文件（仓库是 Public，写进来就等于公开了）。
rem 想永久设一次，在本机 CMD 里跑：  setx AI_API_KEY "sk-你的密钥"
rem 想临时用真模型：这里默认 llm，没设密钥会自动退回规则模式，不影响演示。
if "%AI_MODE%"=="" set AI_MODE=llm
if "%AI_BASE_URL%"=="" set AI_BASE_URL=http://127.0.0.1:3065/v1
if "%AI_MODEL%"=="" set AI_MODEL=workbuddy/deepseek-v4.1-flash
if "%AI_TIMEOUT_S%"=="" set AI_TIMEOUT_S=20

echo ================================================
echo  宿舍维修 AI 定级服务  http://127.0.0.1:8001
echo  接口：GET /health   POST /classify   POST /warmup   GET /rules
if "%AI_MODE%"=="llm" if not "%AI_API_KEY%"=="" (
  echo  模式：llm  模型 %AI_MODEL%  ^(密钥已配置^)
  echo  提示：冷启动第一次要 15 秒以上，先点一下 /warmup
) else (
  echo  模式：rules  纯关键词规则，毫秒级，断网可跑
  echo  想用真模型：设好 AI_API_KEY 再启动，或 set AI_MODE=llm
)
echo  关掉这个黑窗口就是停服务（后端会自动走兜底）
echo ================================================
D:\develop\python-envs\dorm-ai\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001
pause
