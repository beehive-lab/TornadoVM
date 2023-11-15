all: build


# Variable passed for the build process:
# BACKENDS=<ptx|opencl|spirv>. It specifies which backend/s to use { opencl, ptx, spirv }. The default one is `opencl`.
BACKEND?=opencl
BACKENDS := --backends $(BACKEND)

build jdk21:
	bin/compile --jdk jdk21 $(BACKENDS)

graal-jdk-21:
	bin/compile --jdk graal-jdk-21 $(BACKENDS)

ptx:
	bin/compile --jdk jdk21 --backends ptx,opencl

spirv:
	bin/compile --jdk jdk21 --backends spirv,ptx,opencl

offline:
	bin/compile --jdk jdk21 $(BACKENDS) --offline

# Variable passed for the preparation of the Xilinx FPGA emulated target device. The default device is `xilinx_u50_gen3x16_xdma_201920_3`.
# make xilinx_emulation FPGA_PLATFORM=<platform_name> NUM_OF_FPGA_DEVICES=<number_of_devices>
FPGA_PLATFORM?=xilinx_u50_gen3x16_xdma_201920_3
NUM_OF_FPGA_DEVICES?=1

xilinx_emulation:
	emconfigutil --platform $(FPGA_PLATFORM) --nd $(NUM_OF_FPGA_DEVICES) --od $(JAVA_HOME)/bin

checkstyle:
	mvn checkstyle:check

clean:
	mvn -Popencl-backend,ptx-backend clean

example:
	tornado --printKernel --debug -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params="8192"

tests:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --ea --verbose
	tornado-test --ea -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

tests-opt:
	tornado --devices
	tornado-test -V --fast --ea --verbose -J"-Dtornado.spirv.loadstore=True" --printKernel

test-slam:
	tornado-test -V --fast uk.ac.manchester.tornado.unittests.slam.GraphicsTests

docs:
	cd docs && make html

.PHONY: docs
