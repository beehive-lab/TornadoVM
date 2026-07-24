all: build

# Variable passed for the build process. List of backend/s to use { opencl, ptx, spirv, cuda, metal }. The default one is `opencl`.
# make BACKEND=<comma_separated_backend_list>
BACKEND ?= opencl

build jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND)

rebuild-deps-jdk21:
	bin/compile --jdk jdk21 --rebuild --backend $(BACKEND)

graal-jdk-21:
	bin/compile --jdk graal-jdk-21 --backend $(BACKEND)

polyglot:
	bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --polyglot

mvn-single-threaded-jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-graal-jdk-21:
	bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-polyglot:
	bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --mvn_single_threaded --polyglot

ptx:
	bin/compile --jdk jdk21 --backend ptx,opencl

spirv:
	bin/compile --jdk jdk21 --backend spirv,ptx,opencl

metal:
	bin/compile --jdk jdk21 --backend metal,opencl

cuda:
	bin/compile --jdk jdk21 --backend cuda

sdk:
	bin/compile --jdk jdk21 --sdk --backend $(BACKEND)

checkstyle:
	./mvnw checkstyle:check

clean:
	./mvnw -Popencl-backend,ptx-backend,spirv-backend,cuda-backend,metal-backend clean

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

# Generate IntelliJ IDEA project files (developer-only)
# Prerequisites: build TornadoVM first and source setvars.sh
intellijinit:
	bin/tornadovm-intellij-init

.PHONY: docs intellijinit
