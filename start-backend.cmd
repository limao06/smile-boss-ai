@echo off
cd /d "%~dp0backend"
call mvnw.cmd -pl smile-app -am spring-boot:run

