all: build

# Variable passed for the build process. List of backend/s to use { opencl, cuda, metal }. The default one is `opencl`.
# make BACKEND=<comma_separated_backend_list>
BACKEND ?= opencl

# JDK profile used by the `sdk`, `test-reflection`, and `test-reflection-only` targets { jdk21, jdk22plus }.
# Default is `jdk21` to preserve historical bare `make sdk`/`make test-reflection` behavior.
# make sdk JDK=<jdk21|jdk22plus> BACKEND=<comma_separated_backend_list>
# Prefer the sdk-jdkNN targets below over `make sdk JDK=...` when building a specific JDK's SDK.
JDK ?= jdk21

# jdk25/jdk26/jdk27 no longer exist as Maven profiles: one jdk22plus profile serves every JDK
# from 22 up. Map the legacy names onto it so `JDK=jdk25` and CI callers keep working.
JDK_PROFILE = $(if $(filter jdk21 graal-jdk-21,$(JDK)),$(JDK),jdk22plus)

build jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND)

rebuild-deps-jdk21:
	bin/compile --jdk jdk21 --rebuild --backend $(BACKEND)

# One SDK for every JDK from 22 up. jdk25/jdk26/jdk27 are kept as aliases so existing
# scripts and muscle memory keep working; they all produce the same artifact.
jdk22plus jdk25 jdk26 jdk27:
	bin/compile --jdk jdk22plus --backend $(BACKEND)

rebuild-deps-jdk22plus rebuild-deps-jdk25 rebuild-deps-jdk26 rebuild-deps-jdk27:
	bin/compile --jdk jdk22plus --rebuild --backend $(BACKEND)

graal-jdk-21:
	bin/compile --jdk graal-jdk-21 --backend $(BACKEND)

mvn-single-threaded-jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-jdk22plus:
	bin/compile --jdk jdk22plus --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-graal-jdk-21:
	bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --mvn_single_threaded


metal:
	bin/compile --jdk jdk21 --backend metal,opencl

cuda:
	bin/compile --jdk jdk21 --backend cuda

sdk:
	bin/compile --jdk $(JDK_PROFILE) --sdk --backend $(BACKEND)

sdk-jdk21:
	bin/compile --jdk jdk21 --sdk --backend $(BACKEND)

sdk-jdk22plus sdk-jdk25 sdk-jdk26 sdk-jdk27:
	bin/compile --jdk jdk22plus --sdk --backend $(BACKEND)

checkstyle:
	./mvnw checkstyle:check

# Pure-JVM (no-GPU) unit tests for the reflection JVMCI layer. Every JDK profile skips surefire,
# so force it on here. `clean` + `-am` rebuilds tornado-api and tornado-runtime together in the
# same reactor so stale class files from a previously-used JDK profile (source/target release
# differs per profile, e.g. jdk21 compiles at 21, jdk22plus at 22) never leak into the test
# classpath — this target is then safe to run right after a different JDK profile's build, with
# no manual `mvn clean` in between. Override the profile with JDK=jdk22plus to run under
# another JDK (JDK is declared once, near BACKEND, at the top of this file).
test-reflection:
	./mvnw -P$(JDK_PROFILE) -pl tornado-api,tornado-runtime -am clean test -DskipTests=false

# Only the reflection JVMCI-layer suites (uk.ac.manchester.tornado.runtime.jvmci.reflection.*Test) —
# the standalone metadata API, a subset of what test-reflection runs. Same JDK override and
# clean+-am rationale as test-reflection.
test-reflection-only:
	./mvnw -P$(JDK_PROFILE) -pl tornado-api,tornado-runtime -am clean test -DskipTests=false -Dtest="uk.ac.manchester.tornado.runtime.jvmci.reflection.*Test"

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
