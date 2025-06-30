all: build

# Variable passed for the build process. It specifies which backend/s to use { opencl, ptx, spirv }. The default one is `opencl`.
# nmake BACKENDS="<comma_separated_backend_list>"
BACKEND = opencl

build jdk21:
	python bin\compile --jdk jdk21 --backend $(BACKEND)

rebuild-deps:
	python bin\compile --jdk graal-jdk-21 --rebuild --backend $(BACKEND)

graal-jdk-21:
	python bin\compile --jdk graal-jdk-21 --backend $(BACKEND)

polyglot:
	python bin\compile --jdk graal-jdk-21 --backend $(BACKEND) --polyglot

mvn-single-threaded-jdk21:
	python bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-graal-jdk-21:
	python bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-polyglot:
	python bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --mvn_single_threaded --polyglot

ptx:
	python bin\compile --jdk graal-jdk-21 --backend ptx,opencl

spirv:
	python bin\compile --jdk graal-jdk-21 --backend spirv,ptx,opencl

# Variable passed for the preparation of the Xilinx FPGA emulated target device. The default device is `xilinx_u50_gen3x16_xdma_201920_3`.
# make xilinx_emulation FPGA_PLATFORM=<platform_name> NUM_OF_FPGA_DEVICES=<number_of_devices>
FPGA_PLATFORM       = xilinx_u50_gen3x16_xdma_201920_3
NUM_OF_FPGA_DEVICES = 1

xilinx_emulation:
	emconfigutil --platform $(FPGA_PLATFORM) --nd $(NUM_OF_FPGA_DEVICES) --od $(JAVA_HOME)/bin

checkstyle:
	mvn checkstyle:check

clean:
	mvn -Popencl-backend,ptx-backend,spirv-backend clean

example:
	python %TORNADO_SDK%\bin\tornado --printKernel --debug -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params="8192"

tests:
	del /f tornado_unittests.log
	python %TORNADO_SDK%\bin\tornado --devices
	python %TORNADO_SDK%\bin\tornado-test --ea --verbose
	python %TORNADO_SDK%\bin\tornado-test --ea -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADO_SDK%\bin\test-native.cmd

test-slam:
	python %TORNADO_SDK%\bin\tornado-test -V --fast uk.ac.manchester.tornado.unittests.slam.GraphicsTests
