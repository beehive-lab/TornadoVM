@echo off & setlocal

for /f %%b in (%TORNADOVM_HOME%\etc\tornado.backend) do set backends=%%b

echo %backends% | findstr "\<opencl\>" >nul 2>nul
if not errorlevel 1 (
	echo:
	echo Testing the native PTX API
	echo:
	python %TORNADOVM_HOME%\bin\tornado uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXJITCompiler
	python %TORNADOVM_HOME%\bin\tornado uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXTornadoCompiler
)
echo %backends% | findstr "\<ptx\>" >nul 2>nul
if not errorlevel 1 (
	echo:
	echo Testing the native OpenCL API
	echo:
	python %TORNADOVM_HOME%\bin\tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
	python %TORNADOVM_HOME%\bin\tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler
)
echo %backends% | findstr "\<spirv\>" >nul 2>nul
if not errorlevel 1 (
	echo:
	echo Testing the native SPIR-V API
	echo:
	python %TORNADOVM_HOME%\bin\tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler
	python %TORNADOVM_HOME%\bin\tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVTornadoCompiler
	echo:
	echo Testing the native OpenCL API
	echo:
	python %TORNADOVM_HOME%\bin\tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
	python %TORNADOVM_HOME%\bin\tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler
)
