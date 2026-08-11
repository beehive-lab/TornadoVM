all: build

# Variable passed for the build process. List of backend/s to use { opencl, cuda, metal }. The default one is `opencl`.
# make BACKEND=<comma_separated_backend_list>
BACKEND ?= opencl

# JDK used by the `sdk`, `test-reflection`, and `test-reflection-only` targets { jdk21, jdk25, jdk26, jdk27 }.
# Default is `jdk21` to preserve historical bare `make sdk`/`make test-reflection` behavior.
# make sdk JDK=<jdk21|jdk25|jdk26|jdk27> BACKEND=<comma_separated_backend_list>
# Prefer the sdk-jdkNN targets below over `make sdk JDK=...` when building a specific JDK's SDK.
JDK ?= jdk21

build jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND)

rebuild-deps-jdk21:
	bin/compile --jdk jdk21 --rebuild --backend $(BACKEND)

jdk25:
	bin/compile --jdk jdk25 --backend $(BACKEND)

rebuild-deps-jdk25:
	bin/compile --jdk jdk25 --rebuild --backend $(BACKEND)

jdk26:
	bin/compile --jdk jdk26 --backend $(BACKEND)

rebuild-deps-jdk26:
	bin/compile --jdk jdk26 --rebuild --backend $(BACKEND)

jdk27:
	bin/compile --jdk jdk27 --backend $(BACKEND)

rebuild-deps-jdk27:
	bin/compile --jdk jdk27 --rebuild --backend $(BACKEND)

graal-jdk-21:
	bin/compile --jdk graal-jdk-21 --backend $(BACKEND)

mvn-single-threaded-jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-graal-jdk-21:
	bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --mvn_single_threaded


metal:
	bin/compile --jdk jdk21 --backend metal,opencl

cuda:
	bin/compile --jdk jdk21 --backend cuda

sdk:
	bin/compile --jdk $(JDK) --sdk --backend $(BACKEND)

sdk-jdk21:
	bin/compile --jdk jdk21 --sdk --backend $(BACKEND)

sdk-jdk25:
	bin/compile --jdk jdk25 --sdk --backend $(BACKEND)

sdk-jdk26:
	bin/compile --jdk jdk26 --sdk --backend $(BACKEND)

sdk-jdk27:
	bin/compile --jdk jdk27 --sdk --backend $(BACKEND)

checkstyle:
	./mvnw checkstyle:check

# Pure-JVM (no-GPU) unit tests for the reflection JVMCI layer. Every JDK profile skips surefire,
# so force it on here. `clean` + `-am` rebuilds tornado-api and tornado-runtime together in the
# same reactor so stale class files from a previously-used JDK profile (source/target release
# differs per profile, e.g. jdk21 compiles at 21, jdk25/26/27 at 22) never leak into the test
# classpath — this target is then safe to run right after a different JDK profile's build, with
# no manual `mvn clean` in between. Override the profile with JDK=jdk25|jdk26|jdk27 to run under
# another JDK (JDK is declared once, near BACKEND, at the top of this file).
test-reflection:
	./mvnw -P$(JDK) -pl tornado-api,tornado-runtime -am clean test -DskipTests=false

# Only the reflection JVMCI-layer suites (uk.ac.manchester.tornado.runtime.jvmci.reflection.*Test) —
# the standalone metadata API, a subset of what test-reflection runs. Same JDK override and
# clean+-am rationale as test-reflection.
test-reflection-only:
	./mvnw -P$(JDK) -pl tornado-api,tornado-runtime -am clean test -DskipTests=false -Dtest="uk.ac.manchester.tornado.runtime.jvmci.reflection.*Test"

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
