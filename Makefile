all: build

# Variable passed for the build process. List of backend/s to use { opencl, cuda, metal }. The default one is `opencl`.
# make BACKEND=<comma_separated_backend_list>
BACKEND ?= opencl

# Reject backends that are not supported, mirroring the check the installer performs on --backend
# (__SUPPORTED_BACKENDS__ in bin/tornadovm-installer). Without this an unsupported name is handed
# to bin/compile, becomes a non-existent Maven profile, and fails much later and far less clearly.
COMMA := ,
EMPTY :=
SPACE := $(EMPTY) $(EMPTY)
SUPPORTED_BACKENDS := opencl cuda metal
BACKEND_LIST := $(strip $(subst $(COMMA),$(SPACE),$(BACKEND)))
UNSUPPORTED_BACKENDS := $(filter-out $(SUPPORTED_BACKENDS),$(BACKEND_LIST))

# `make BACKEND=` sets the variable to an empty value, so `?=` above does not restore the default:
# without this it would build nothing and only fail deep inside Maven.
ifeq ($(BACKEND_LIST),)
$(error [ERROR] No backend specified in BACKEND. Provide one of the supported backends: $(subst $(SPACE),$(COMMA)$(SPACE),$(SUPPORTED_BACKENDS)) -- e.g. make BACKEND=opencl or make BACKEND=opencl$(COMMA)cuda)
endif

ifneq ($(UNSUPPORTED_BACKENDS),)
$(error [ERROR] Unsupported backends specified in BACKEND: $(subst $(SPACE),$(COMMA)$(SPACE),$(UNSUPPORTED_BACKENDS)). Supported backends: $(subst $(SPACE),$(COMMA)$(SPACE),$(SUPPORTED_BACKENDS)))
endif

# JDK profile for the targets below that do not name one: jdk21 when JAVA_HOME is JDK 21 (an
# enable-preview SDK pinned to exactly JDK 21), jdk22plus otherwise (one SDK for every JDK from 22).
JDK_PROFILE ?= $(shell v=$$("$${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1); \
                       if [ "$$v" = "21" ]; then echo jdk21; else echo jdk22plus; fi)

build:
	bin/compile --jdk $(JDK_PROFILE) --backend $(BACKEND)

jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND)

# jdk25/jdk26/jdk27 are kept as aliases so existing scripts and muscle memory keep working;
# they all produce the same artifact.
jdk22plus jdk25 jdk26 jdk27:
	bin/compile --jdk jdk22plus --backend $(BACKEND)

rebuild-deps:
	bin/compile --jdk $(JDK_PROFILE) --rebuild --backend $(BACKEND)

rebuild-deps-jdk21:
	bin/compile --jdk jdk21 --rebuild --backend $(BACKEND)

rebuild-deps-jdk22plus rebuild-deps-jdk25 rebuild-deps-jdk26 rebuild-deps-jdk27:
	bin/compile --jdk jdk22plus --rebuild --backend $(BACKEND)

mvn-single-threaded:
	bin/compile --jdk $(JDK_PROFILE) --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-jdk22plus:
	bin/compile --jdk jdk22plus --backend $(BACKEND) --mvn_single_threaded

metal:
	bin/compile --jdk $(JDK_PROFILE) --backend metal,opencl

cuda:
	bin/compile --jdk $(JDK_PROFILE) --backend cuda

sdk:
	bin/compile --jdk $(JDK_PROFILE) --sdk --backend $(BACKEND)

sdk-jdk21:
	bin/compile --jdk jdk21 --sdk --backend $(BACKEND)

sdk-jdk22plus:
	bin/compile --jdk jdk22plus --sdk --backend $(BACKEND)

checkstyle:
	./mvnw checkstyle:check

# Pure-JVM (no-GPU) unit tests for the reflection metadata layer. The build skips surefire by
# default, so force it on here. `clean` + `-am` rebuilds tornado-api and tornado-runtime together
# in the same reactor so no stale class files leak into the test classpath.
test-reflection:
	./mvnw -P$(JDK_PROFILE) -pl tornado-api,tornado-runtime -am clean test -DskipTests=false

# No-GPU tests for the CUDA code generator (tornado-drivers/cuda/src/test): they build LIR by hand,
# so they need no device. `-am` is required -- tornado-drivers-cuda resolves tornado-drivers-common
# from the reactor, not the local repository.
test-cuda-codegen:
	./mvnw -P$(JDK_PROFILE),cuda-backend -Dtornado.backend=cuda -pl tornado-drivers/cuda -am test -DskipTests=false

# Only the reflection metadata-layer suites (uk.ac.manchester.tornado.runtime.meta.reflection.*Test) —
# the standalone metadata API, a subset of what test-reflection runs. Same clean+-am rationale as
# test-reflection.
test-reflection-only:
	./mvnw -P$(JDK_PROFILE) -pl tornado-api,tornado-runtime -am clean test -DskipTests=false -Dtest="uk.ac.manchester.tornado.runtime.meta.reflection.*Test"

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
