@echo off & setlocal

for /f %%b in (%TORNADOVM_HOME%\etc\tornado.backend) do set backends=%%b

echo %backends% | findstr "opencl" >nul 2>nul
if not errorlevel 1 (
	echo:
	echo Testing the native OpenCL API
	echo:
	%TORNADOVM_HOME%\bin\tornado.exe uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
	%TORNADOVM_HOME%\bin\tornado.exe uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler
)
echo %backends% | findstr "ptx" >nul 2>nul
if not errorlevel 1 (
	echo:
	echo Testing the native PTX API
	echo:
	%TORNADOVM_HOME%\bin\tornado.exe uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXJITCompiler
	%TORNADOVM_HOME%\bin\tornado.exe uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXTornadoCompiler
)
