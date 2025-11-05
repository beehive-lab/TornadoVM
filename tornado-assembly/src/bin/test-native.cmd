@echo off & setlocal

for /f %%b in (%TORNADO_SDK%\etc\tornado.backend) do set backends=%%b

echo %backends% | findstr "\<opencl\>" >NUL
if not errorlevel 1 (
	echo:
	echo Testing the native PTX API
	echo:
	python %TORNADO_SDK%\bin\tornado uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXJITCompiler
	python %TORNADO_SDK%\bin\tornado uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXTornadoCompiler
)
echo %backends% | findstr "\<ptx\>" >NUL
if not errorlevel 1 (
	echo:
	echo Testing the native OpenCL API
	echo:
	python %TORNADO_SDK%\bin\tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
	python %TORNADO_SDK%\bin\tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler
)
echo %backends% | findstr "\<spirv\>" >NUL
if not errorlevel 1 (
	echo:
	echo Testing the native SPIR-V API
	echo:
	python %TORNADO_SDK%\bin\tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler
	python %TORNADO_SDK%\bin\tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVTornadoCompiler
	echo:
	echo Testing the native OpenCL API
	echo:
	python %TORNADO_SDK%\bin\tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
	python %TORNADO_SDK%\bin\tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler
)
