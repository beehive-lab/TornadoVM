all: build

# Variable passed for the build process. List of backend/s to use { opencl, ptx, spirv }. The default one is `opencl`.
# nmake BACKENDS="<comma_separated_backend_list>"
BACKEND = opencl

build jdk25:
	python bin\compile --jdk jdk25 --backend $(BACKEND)

rebuild-deps-jdk25:
	python bin\compile --jdk jdk25 --rebuild --backend $(BACKEND)

graal-jdk-25:
	python bin\compile --jdk graal-jdk-25 --backend $(BACKEND)

polyglot:
	python bin\compile --jdk graal-jdk-25 --backend $(BACKEND) --polyglot

mvn-single-threaded-jdk25:
	python bin/compile --jdk jdk25 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-graal-jdk-25:
	python bin/compile --jdk graal-jdk-25 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-polyglot:
	python bin/compile --jdk graal-jdk-25 --backend $(BACKEND) --mvn_single_threaded --polyglot

ptx:
	python bin\compile --jdk jdk25 --backend ptx,opencl

spirv:
	python bin\compile --jdk jdk25 --backend spirv,ptx,opencl

sdk:
	python bin\compile --jdk jdk25 --sdk --backend $(BACKEND)

# Variable passed for the preparation of the Xilinx FPGA emulated target device. The default device is `xilinx_u50_gen3x16_xdma_201920_3`.
# make xilinx_emulation FPGA_PLATFORM=<platform_name> NUM_OF_FPGA_DEVICES=<number_of_devices>
FPGA_PLATFORM       = xilinx_u50_gen3x16_xdma_201920_3
NUM_OF_FPGA_DEVICES = 1

xilinx_emulation:
	emconfigutil --platform $(FPGA_PLATFORM) --nd $(NUM_OF_FPGA_DEVICES) --od $(JAVA_HOME)/bin

checkstyle:
	.\mvnw checkstyle:check

clean:
	.\mvnw -Popencl-backend,ptx-backend,spirv-backend clean

example:
	%TORNADOVM_HOME%\bin\tornado.exe --printKernel --debug -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params="8192"

tests:
	del /f tornado_unittests.log
	%TORNADOVM_HOME%\bin\tornado.exe --devices
	%TORNADOVM_HOME%\bin\tornado-test.exe --verbose
	%TORNADOVM_HOME%\bin\tornado-test.exe -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

fast-tests:
	del /f tornado_unittests.log
	%TORNADOVM_HOME%\bin\tornado.exe --devices
	%TORNADOVM_HOME%\bin\tornado-test.exe --verbose --quickPass
	%TORNADOVM_HOME%\bin\tornado-test.exe -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

tests-uncompressed:
	del /f tornado_unittests.log
	python %TORNADOVM_HOME%\bin\tornado --devices
	python %TORNADOVM_HOME%\bin\tornado-test --verbose --uncompressed
	python %TORNADOVM_HOME%\bin\tornado-test -V --uncompressed -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

fast-tests-uncompressed:
	del /f tornado_unittests.log
	python %TORNADOVM_HOME%\bin\tornado --devices
	python %TORNADOVM_HOME%\bin\tornado-test --verbose --quickPass --uncompressed
	python %TORNADOVM_HOME%\bin\tornado-test -V --uncompressed -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

tests-spirv-levelzero:
	del /f tornado_unittests.log
	%TORNADOVM_HOME%\bin\tornado.exe --jvm="-Dtornado.spirv.dispatcher=levelzero" uk.ac.manchester.tornado.drivers.TornadoDeviceQuery --params="verbose"
	%TORNADOVM_HOME%\bin\tornado-test.exe --jvm="-Dtornado.spirv.dispatcher=levelzero" --verbose
	%TORNADOVM_HOME%\bin\tornado-test.exe --jvm="-Dtornado.spirv.dispatcher=levelzero" -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

tests-spirv-opencl:
	del /f tornado_unittests.log
	%TORNADOVM_HOME%\bin\tornado.exe --jvm="-Dtornado.spirv.dispatcher=opencl" uk.ac.manchester.tornado.drivers.TornadoDeviceQuery --params="verbose"
	%TORNADOVM_HOME%\bin\tornado-test.exe --jvm="-Dtornado.spirv.dispatcher=opencl" --verbose
	%TORNADOVM_HOME%\bin\tornado-test.exe --jvm="-Dtornado.spirv.dispatcher=opencl" -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

test-slam:
	%TORNADOVM_HOME%\bin\tornado-test.exe -V --fast uk.ac.manchester.tornado.unittests.slam.GraphicsTests

docs:
	sphinx-build -M html docs/source/ docs/build

# Generate IntelliJ IDEA project files (developer-only)
# Prerequisites: build TornadoVM first and run setvars.cmd
intellijinit:
	python bin\tornadovm-intellij-init
