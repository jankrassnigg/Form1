@ECHO OFF

IF "%VCPKG_ROOT%" == "" (
  ECHO VCPKG_ROOT environment varialbe is not set, using '%~dp0vcpkg'

  SET VCPKG_ROOT="%~dp0vcpkg"
)

IF NOT EXIST "%VCPKG_ROOT%\README.md" (
  ECHO Installing vcpkg to '%VCPKG_ROOT%' ...

  git clone https://github.com/Microsoft/vcpkg.git "%VCPKG_ROOT%"
)

IF NOT EXIST "%VCPKG_ROOT%\README.md" (
  ECHO FATAL ERROR: Could not clone vcpkg
  exit /b 1
)

IF NOT EXIST "%VCPKG_ROOT%\vcpkg.exe" (
  ECHO Bootstrapping vcpkg in %VCPKG_ROOT%
  CALL %VCPKG_ROOT%\bootstrap-vcpkg.bat
)

IF NOT EXIST "%VCPKG_ROOT%\vcpkg.exe" (
  ECHO FATAL ERROR: Could not bootstrap vcpkg
  exit /b 2
)

ECHO Installing taglib:x64-windows
START "" /D %VCPKG_ROOT% /WAIT /B vcpkg.exe install taglib:x64-windows

ECHO Installing sqlite3:x64-windows
START "" /D %VCPKG_ROOT% /WAIT /B vcpkg.exe install sqlite3:x64-windows

ECHO Installing qt5-base:x64-windows
START "" /D %VCPKG_ROOT% /WAIT /B vcpkg.exe install qt5-base:x64-windows

CHO Installing qt5-winextras:x64-windows
START "" /D %VCPKG_ROOT% /WAIT /B vcpkg.exe install qt5-winextras:x64-windows

ECHO Finished successfully.