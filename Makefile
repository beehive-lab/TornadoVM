all: build

# Variable passed for the build process. List of backend/s to use { opencl, ptx, spirv }. The default one is `opencl`.
# make BACKEND=<comma_separated_backend_list>
BACKEND ?= opencl

build jdk25:
	bin/compile --jdk jdk25 --backend $(BACKEND)

rebuild-deps-jdk25:
	bin/compile --jdk jdk25 --rebuild --backend $(BACKEND)

graal-jdk-25:
	bin/compile --jdk graal-jdk-25 --backend $(BACKEND)

polyglot:
	bin/compile --jdk graal-jdk-25 --backend $(BACKEND) --polyglot

mvn-single-threaded-jdk25:
	bin/compile --jdk jdk25 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-graal-jdk-25:
	bin/compile --jdk graal-jdk-25 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-polyglot:
	bin/compile --jdk graal-jdk-25 --backend $(BACKEND) --mvn_single_threaded --polyglot

ptx:
	bin/compile --jdk jdk25 --backend ptx,opencl

spirv:
	bin/compile --jdk jdk25 --backend spirv,ptx,opencl

sdk:
	bin/compile --jdk jdk25 --sdk --backend $(BACKEND)

# Variable passed for the preparation of the Xilinx FPGA emulated target device. The default device is `xilinx_u50_gen3x16_xdma_201920_3`.
# make xilinx_emulation FPGA_PLATFORM=<platform_name> NUM_OF_FPGA_DEVICES=<number_of_devices>
FPGA_PLATFORM       ?= xilinx_u50_gen3x16_xdma_201920_3
NUM_OF_FPGA_DEVICES ?= 1

xilinx_emulation:
	emconfigutil --platform $(FPGA_PLATFORM) --nd $(NUM_OF_FPGA_DEVICES) --od $(JAVA_HOME)/bin

checkstyle:
	./mvnw checkstyle:check

clean:
	./mvnw -Popencl-backend,ptx-backend,spirv-backend clean

example:
	tornado --printKernel --debug -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params="8192"

tests:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --ea --verbose
	tornado-test --ea -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

fast-tests:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --ea --verbose --quickPass
	tornado-test --ea -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

tests-uncompressed:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --ea --verbose --uncompressed
	tornado-test --ea -V --uncompressed -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

fast-tests-uncompressed:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --ea --verbose --quickPass --uncompressed
	tornado-test --ea -V --uncompressed -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh	

tests-spirv-levelzero:
	rm -f tornado_unittests.log
	tornado --jvm="-Dtornado.spirv.dispatcher=levelzero" uk.ac.manchester.tornado.drivers.TornadoDeviceQuery --params="verbose"
	tornado-test --jvm="-Dtornado.spirv.dispatcher=levelzero" --ea --verbose
	tornado-test --jvm="-Dtornado.spirv.dispatcher=levelzero"--ea -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

tests-spirv-opencl:
	rm -f tornado_unittests.log
	tornado --jvm="-Dtornado.spirv.dispatcher=opencl" uk.ac.manchester.tornado.drivers.TornadoDeviceQuery --params="verbose"
	tornado-test --jvm="-Dtornado.spirv.dispatcher=opencl" --ea --verbose
	tornado-test --jvm="-Dtornado.spirv.dispatcher=opencl"--ea -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

test-slam:
	tornado-test -V --fast uk.ac.manchester.tornado.unittests.slam.GraphicsTests

docs:
	sphinx-build -M html docs/source/ docs/build

.PHONY: docs
