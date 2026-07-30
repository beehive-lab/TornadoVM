all: build

# Variable passed for the build process. List of backend/s to use { opencl, cuda, metal }. The default one is `opencl`.
# make BACKEND=<comma_separated_backend_list>
BACKEND ?= opencl

build jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND)

rebuild-deps-jdk21:
	bin/compile --jdk jdk21 --rebuild --backend $(BACKEND)

jdk27:
	bin/compile --jdk jdk27 --backend $(BACKEND)

rebuild-deps-jdk27:
	bin/compile --jdk jdk27 --rebuild --backend $(BACKEND)

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


metal:
	bin/compile --jdk jdk21 --backend metal,opencl

cuda:
	bin/compile --jdk jdk21 --backend cuda

sdk:
	bin/compile --jdk jdk21 --sdk --backend $(BACKEND)

checkstyle:
	./mvnw checkstyle:check

# Pure-JVM (no-GPU) unit tests for the reflection JVMCI layer. Every JDK profile skips surefire,
# so force it on here. Override the profile with JDK=jdk25|jdk26|jdk27 to run under another JDK.
JDK ?= jdk21
unit-tests:
	./mvnw -P$(JDK) -pl tornado-runtime test -DskipTests=false

# Only the reflection JVMCI-layer suites (uk.ac.manchester.tornado.runtime.jvmci.reflection.*Test) —
# the standalone metadata API. Same JDK override as unit-tests.
test-reflection:
	./mvnw -P$(JDK) -pl tornado-runtime test -DskipTests=false -Dtest="uk.ac.manchester.tornado.runtime.jvmci.reflection.*Test"

clean:
	./mvnw -Popencl-backend,cuda-backend,metal-backend clean

example:
	tornado --printKernel --debug -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params="8192"

tests:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --verbose
	tornado-test -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

fast-tests:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --verbose --quickPass
	tornado-test -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

tests-uncompressed:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --verbose --uncompressed
	tornado-test -V --uncompressed -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	test-native.sh

fast-tests-uncompressed:
	rm -f tornado_unittests.log
	tornado --devices
	tornado-test --verbose --quickPass --uncompressed
	tornado-test -V --uncompressed -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
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
