@echo off
echo ===================================================
echo     MediCareX — Smarter Healthcare. Better Access.
echo ===================================================
echo [1/2] Compiling Pure Java Backend...
if not exist "backend\bin" mkdir backend\bin
javac -cp "backend\lib\*" -d "backend\bin" backend\src\com\medicarex\*.java backend\src\com\medicarex\config\*.java backend\src\com\medicarex\auth\*.java backend\src\com\medicarex\model\*.java backend\src\com\medicarex\repository\*.java backend\src\com\medicarex\service\*.java backend\src\com\medicarex\controller\*.java backend\src\com\medicarex\util\*.java

echo [2/2] Starting MediCareX Server on http://localhost:8080...
java -cp "backend\bin;backend\lib\*" com.medicarex.Main
pause
