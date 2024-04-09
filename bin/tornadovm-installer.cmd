@echo off & setlocal

set version="v1.0.1-dev"
set options="-h, --help, --version, --jdk, --backend, --listJDKs, --javaHome"

set givenBackends=opencl, ptx, spirv
set opencl=1 & set ptx=1 & set spirv=1

set TORNADO_DIR=%CD%



:argv
if not "%1"=="" (
	echo %options% | findstr "\<%1\>" >nul
	if errorlevel 1 goto :usage

	if "%1"=="-h" goto :usage
	if "%1"=="--help" goto :usage
	if "%1"=="--version" goto :version
	if "%1"=="--jdk" (
		shift
	)
	if "%1"=="--backend" (
		set takenBackends=%2
		shift
	)
	if "%1"=="--listJDKs" goto :listJDKs
	if "%1"=="--javaHome" (
		set _JAVA_HOME=%2
		shift
	)
	shift & goto :argv
)



rem compile for all backends if no --backend option
if not defined takenBackends set takenBackends="%givenBackends: =%"

rem check for valid backends given to --backend option
if %takenBackends%=="" goto :usage
set opencl=0 & set ptx=0 & set spirv=0

for %%b in (%takenBackends:"=%) do (
	echo %givenBackends% | findstr "\<%%b\>" >nul
	if errorlevel 1 goto :usage
	if "%%b"=="opencl" set opencl=1
	if "%%b"=="ptx" set ptx=1
	if "%%b"=="spirv" set spirv=1
)



rem probe tools
if not exist "%CMAKE_ROOT%" goto :usage
cmake -version
if errorlevel 1 goto :usage

call mvn -version
if errorlevel 1 goto :usage

if not exist "%JAVA_HOME%" goto :usage
"%JAVA_HOME%\bin\java" -version
if errorlevel 1 goto :usage

if %ptx% equ 1 (
	if not exist "%CUDA_PATH%" goto :usage
	"%CUDA_PATH%\bin\nvcc" --version
	if errorlevel 1 goto :usage
)



rem compile
set TORNADO_SDK=%TORNADO_DIR%\bin\sdk
set PATH=%TORNADO_SDK%\bin;%JAVA_HOME%\bin;%PATH%

rem needed by SPIR-V backend on execution
set PATH=%TORNADO_DIR%\..\level-zero\build\bin\Release;%PATH%

rem clean all
call mvn -Popencl-backend,ptx-backend,spirv-backend clean

rem prepare for OpenCL backend
if %opencl% equ 1 (
	if exist %TORNADO_DIR%\..\OpenCL-Headers goto :repoOpenCLBuilt
	rem OpenCL headers
	cd %TORNADO_DIR%\..
	git clone https://github.com/KhronosGroup/OpenCL-Headers.git
	cd OpenCL-Headers
	cmake -S . -B build -DCMAKE_INSTALL_PREFIX=%TORNADO_DIR%\tornado-drivers\opencl-jni\src\main\cpp\headers
	if errorlevel 1 exit /b %errorlevel%
	cmake --build build --target install
	if errorlevel 1 exit /b %errorlevel%
	:repoOpenCLBuilt
	echo:
)

rem prepare for PTX backend
if %ptx% equ 1 (
	set CPLUS_INCLUDE_PATH=%CUDA_PATH%\include;%CPLUS_INCLUDE_PATH%
	set C_INCLUDE_PATH=%CUDA_PATH%\include;%C_INCLUDE_PATH%
)

rem prepare for SPIR-V backend
if %spirv% equ 1 (
	if exist %TORNADO_DIR%\..\level-zero goto :repoL0ApiBuilt
	rem Intel oneAPI Level Zero
	cd %TORNADO_DIR%\..
	git clone https://github.com/oneapi-src/level-zero
	cd level-zero
	md build
	cd build
	cmake ..
	if errorlevel 1 exit /b %errorlevel%
	cmake --build . --config Release
	if errorlevel 1 exit /b %errorlevel%
	bin\Release\zello_world
	if not %errorlevel% equ 0 exit /b %errorlevel%
	:repoL0ApiBuilt
	echo:

	rem Beehive Level Zero JNI
	set ZE_SHARED_LOADER=%TORNADO_DIR%\..\level-zero\build\lib\Release\ze_loader.lib
	set CPLUS_INCLUDE_PATH=%TORNADO_DIR%\..\level-zero\include;%CPLUS_INCLUDE_PATH%
	set C_INCLUDE_PATH=%TORNADO_DIR%\..\level-zero\include;%C_INCLUDE_PATH%
	if exist %TORNADO_DIR%\levelzero-jni (
		cd %TORNADO_DIR%\levelzero-jni
		rd /s /q levelZeroLib\build
		goto :repoL0JniCloned
	)
	cd %TORNADO_DIR%
	git clone https://github.com/beehive-lab/levelzero-jni
	cd levelzero-jni
	git checkout winstall
	:repoL0JniCloned
	call mvn clean install
	if errorlevel 1 exit /b %errorlevel%
	cd levelZeroLib
	md build
	cd build
	cmake ..
	if errorlevel 1 exit /b %errorlevel%
	cmake --build . --config Release
	if errorlevel 1 exit /b %errorlevel%
	
	rem install Beehive SPIR-V toolkit
	if exist %TORNADO_DIR%\beehive-spirv-toolkit (
		cd %TORNADO_DIR%\beehive-spirv-toolkit
		goto :repoSpirvTkCloned
	)
	cd %TORNADO_DIR%
	git clone https://github.com/beehive-lab/beehive-spirv-toolkit
	cd beehive-spirv-toolkit
	git checkout winstall
	:repoSpirvTkCloned
	call mvn clean install
	if errorlevel 1 exit /b %errorlevel%
)

rem compile TornadoVM and defined backends
cd %TORNADO_DIR%
set tornadoBackends=%takenBackends:"=%
set tornadoBackends=%tornadoBackends:,=-backend,%-backend
call mvn -Dcmake.root.dir="%CMAKE_ROOT%" -Pgraal-jdk-21,"%tornadoBackends%" install
if errorlevel 1 exit /b %errorlevel%



rem point TORNADO_SDK at latest build
cd %TORNADO_DIR%
for /f "tokens=1" %%d in ('dir /b /od dist\tornado-sdk\tornado-sdk-*') do set latest=%%d
if exist bin\sdk rmdir bin\sdk
mklink /j bin\sdk %TORNADO_DIR%\dist\tornado-sdk\%latest%
if exist bin\bin rmdir bin\bin
mklink /j bin\bin %TORNADO_DIR%\dist\tornado-sdk\%latest%\bin



rem create compiled backends file
echo tornado.backends=%tornadoBackends%> %TORNADO_SDK%\etc\tornado.backend



rem create setvars.cmd batch file
echo rem environment> setvars.cmd
echo rem   - used to build TornadoVM>> setvars.cmd
echo rem   - required to run tornado>> setvars.cmd
echo set JAVA_HOME=%JAVA_HOME%>> setvars.cmd
echo set CMAKE_ROOT=%CMAKE_ROOT%>> setvars.cmd
echo set TORNADO_SDK=%TORNADO_SDK%>> setvars.cmd
echo set PATH=%PATH%>> setvars.cmd

endlocal
exit /b 0



:usage
echo TornadoVM Installer Tool for Windows. CD to top-level directory of TornadoVM
echo repository. Run in "x64 Native Tools Command Prompt for VS 2022".
echo:
echo Working native Windows toolchain required:
echo   - VS 22 (incl. C++, Git, Spectre mitigated libraries)
echo   - CMake, Maven, GraalVM for JDK, Python
echo   - Intel CPU RT for OpenCL (optional)
echo:
echo Expected environment variables:
echo   JAVA_HOME, if not supplied via --javaHome option
echo   CMAKE_ROOT, CMake install directory
echo   CUDA_PATH, if PTX backend wanted
echo:
echo   tornadovm-installer.cmd [-h] [--help] [--version] [--jdk JDK]
echo     [--backend BACKEND] [--listJDKs] [--javaHome JAVAHOME]
echo:
echo   -h, --help       Show this help message and exit.
echo   --version        Print version of TornadoVM.
echo   --jdk JDK        Select one of the supported JDKs. Silently ignored
echo                    as there is only GraalVM for JDK supported on Windows.
echo   --backend LIST   Select at least one backend (%givenBackends%)
echo                    to install or omit for all.
echo   --listJDKs       List supported JDK versions.
echo   --javaHome PATH  Use a particular JDK version.
exit /b 1

:version
echo TornadoVM %version:"=%
exit /b 0

:listJDKs
echo GraalVM for JDK 21.0.1 (GraalVM 23.1.0)
exit /b 0
