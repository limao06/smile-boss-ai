@echo off
cd /d "%~dp0frontend\admin"
call npm.cmd install
call npm.cmd run dev

