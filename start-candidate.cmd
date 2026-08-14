@echo off
cd /d "%~dp0frontend\candidate"
call npm.cmd install
call npm.cmd run dev

