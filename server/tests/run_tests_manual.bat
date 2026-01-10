@echo off
echo Compiling Server Tests...
if not exist "bin" mkdir bin

C:\MinGW\bin\g++.exe -std=c++17 -I../src ^
    server_integrity_test.cpp ^
    -o bin\server_test.exe > compile.log 2>&1

type compile.log


echo.
echo Running Tests...
bin\server_test.exe
