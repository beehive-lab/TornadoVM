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

# JDK profile used by the `sdk` and `test-nogpu` targets. TornadoVM builds for JDK 25 and newer
# only, so there is one profile, jdk25plus; jdk25/jdk26/jdk27 are accepted as aliases.
# Override explicitly with:  make sdk JDK=jdk25plus BACKEND=<backends>
JDK ?= jdk25plus
JDK_PROFILE = jdk25plus

# One SDK for every JDK from 25 up. jdk25/jdk26/jdk27 are kept as aliases so existing
# scripts and muscle memory keep working; they all produce the same artifact.
build jdk25plus jdk25 jdk26 jdk27:
	bin/compile --jdk jdk25plus --backend $(BACKEND)

rebuild-deps rebuild-deps-jdk25plus rebuild-deps-jdk25 rebuild-deps-jdk26 rebuild-deps-jdk27:
	bin/compile --jdk jdk25plus --rebuild --backend $(BACKEND)

mvn-single-threaded-jdk25plus:
	bin/compile --jdk jdk25plus --backend $(BACKEND) --mvn_single_threaded


metal:
	bin/compile --jdk jdk25plus --backend metal,opencl

cuda:
	bin/compile --jdk jdk25plus --backend cuda

sdk:
	bin/compile --jdk $(JDK_PROFILE) --sdk --backend $(BACKEND)

sdk-jdk25plus:
	bin/compile --jdk jdk25plus --sdk --backend $(BACKEND)

checkstyle:
	./mvnw checkstyle:check

# The unit tests that need no GPU: TC's (the compiler's lit file tests and API tests; every build
# runs them too) and tornado-api/tornado-runtime's. The JDK profiles skip surefire elsewhere, so force
# it on here. `clean` + `-am` rebuilds the modules in one reactor so stale class files from an
# older build never reach the test classpath. `test-reflection` is the old name.
TC_MODULES = tornado-compiler/tc-check,tornado-compiler/tc-ir,tornado-compiler/tc-frontend,tornado-compiler/tc-opt,tornado-compiler/tc-lower,tornado-compiler/tc-codegen,tornado-compiler/tc-tornado,tornado-compiler/tc-runtime,tornado-compiler/tc-debug
test-nogpu:
	./mvnw -P$(JDK_PROFILE) -pl tornado-api,tornado-runtime,$(TC_MODULES) -am clean test -DskipTests=false

test-reflection: test-nogpu

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
