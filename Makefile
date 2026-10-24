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

# Opt-in Kotlin support: `make KOTLIN=1 BACKEND=...` also builds the tornado-kotlin modules
# (Kotlin API, unit tests) and ships them, with kotlin-stdlib, in the SDK. Off by default.
KOTLIN ?= 0
KOTLIN_ARG = $(if $(filter 1 true yes,$(KOTLIN)),--kotlin,)

# JDK profile used by the `sdk` and `test-nogpu` targets
# { jdk21, jdk22plus }, derived from JAVA_HOME rather than hardcoded.
#
# It has to be derived: bin/compile checks that JAVA_HOME matches the requested profile, so a fixed
# default of jdk21 can only ever fail once JAVA_HOME is a newer JDK -- and it fails confusingly,
# because the goal that failed (`sdk`) is not the profile the user named. `make jdk22plus sdk` is
# two independent goals, and the second one does not inherit the first one's profile.
#
# Override explicitly with:  make sdk JDK=<jdk21|jdk22plus> BACKEND=<backends>
# or use the sdk-jdkNN targets below, which pin the profile and ignore this variable.
JDK ?= $(shell v=$$("$${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1); \
               if [ "$$v" = "21" ] || [ -z "$$v" ]; then echo jdk21; else echo jdk22plus; fi)

# jdk25/jdk26/jdk27 no longer exist as Maven profiles: one jdk22plus profile serves every JDK
# from 22 up. Map the legacy names onto it so `JDK=jdk25` and CI callers keep working.
JDK_PROFILE = $(if $(filter jdk21,$(JDK)),$(JDK),jdk22plus)

build jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND) $(KOTLIN_ARG)

rebuild-deps-jdk21:
	bin/compile --jdk jdk21 --rebuild --backend $(BACKEND) $(KOTLIN_ARG)

# One SDK for every JDK from 22 up. jdk25/jdk26/jdk27 are kept as aliases so existing
# scripts and muscle memory keep working; they all produce the same artifact.
jdk22plus jdk25 jdk26 jdk27:
	bin/compile --jdk jdk22plus --backend $(BACKEND) $(KOTLIN_ARG)

rebuild-deps-jdk22plus rebuild-deps-jdk25 rebuild-deps-jdk26 rebuild-deps-jdk27:
	bin/compile --jdk jdk22plus --rebuild --backend $(BACKEND) $(KOTLIN_ARG)

mvn-single-threaded-jdk21:
	bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded $(KOTLIN_ARG)

mvn-single-threaded-jdk22plus:
	bin/compile --jdk jdk22plus --backend $(BACKEND) --mvn_single_threaded $(KOTLIN_ARG)


metal:
	bin/compile --jdk jdk21 --backend metal,opencl $(KOTLIN_ARG)

cuda:
	bin/compile --jdk jdk21 --backend cuda $(KOTLIN_ARG)

sdk:
	bin/compile --jdk $(JDK_PROFILE) --sdk --backend $(BACKEND) $(KOTLIN_ARG)

sdk-jdk21:
	bin/compile --jdk jdk21 --sdk --backend $(BACKEND) $(KOTLIN_ARG)

sdk-jdk22plus:
	bin/compile --jdk jdk22plus --sdk --backend $(BACKEND) $(KOTLIN_ARG)

checkstyle:
	./mvnw checkstyle:check

# The unit tests that need no GPU: TC's (the compiler's lit file tests and API tests; every build
# runs them too) and tornado-api/tornado-runtime's. The JDK profiles skip surefire elsewhere, so force
# it on here. `clean` + `-am` rebuilds the modules in one reactor so stale class files from another
# JDK profile (jdk21 compiles at 21, jdk22plus at 22) never reach the test classpath; the target is
# safe to run right after a different profile's build. Override the profile with JDK=jdk22plus
# (JDK is declared once, near BACKEND, at the top of this file). `test-reflection` is the old name.
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
