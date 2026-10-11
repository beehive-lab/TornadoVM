all: build

# Variable passed for the build process. List of backend/s to use { opencl, cuda, metal }. The default one is `opencl`.
# nmake BACKENDS="<comma_separated_backend_list>"
BACKEND = opencl

# JDK profile used by the `sdk` and `test-nogpu` targets { jdk21, jdk22plus }.
# Default is `jdk21` to preserve historical bare `nmake sdk`/`nmake test-nogpu` behavior.
# nmake sdk JDK=<jdk21|jdk22plus> BACKEND=<comma_separated_backend_list>
# Prefer the sdk-jdkNN targets below over `nmake sdk JDK=...` when building a specific JDK's SDK.
JDK = jdk21

# jdk25/jdk26/jdk27 no longer exist as Maven profiles: one jdk22plus profile serves every JDK
# from 22 up. Map the legacy names onto it so `JDK=jdk25` and CI callers keep working.
!IF "$(JDK)" == "jdk21"
JDK_PROFILE = $(JDK)
!ELSE
JDK_PROFILE = jdk22plus
!ENDIF

build jdk21:
	python bin\compile --jdk jdk21 --backend $(BACKEND)

rebuild-deps-jdk21:
	python bin\compile --jdk jdk21 --rebuild --backend $(BACKEND)

# One SDK for every JDK from 22 up. jdk25/jdk26/jdk27 are kept as aliases so existing
# scripts and muscle memory keep working; they all produce the same artifact.
jdk22plus jdk25 jdk26 jdk27:
	python bin\compile --jdk jdk22plus --backend $(BACKEND)

rebuild-deps-jdk22plus rebuild-deps-jdk25 rebuild-deps-jdk26 rebuild-deps-jdk27:
	python bin\compile --jdk jdk22plus --rebuild --backend $(BACKEND)

rebuild-deps:
	python bin\compile --jdk jdk21 --rebuild --backend $(BACKEND)

mvn-single-threaded-jdk21:
	python bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-jdk22plus:
	python bin/compile --jdk jdk22plus --backend $(BACKEND) --mvn_single_threaded


cuda:
	python bin\compile --jdk jdk21 --backend cuda

sdk:
	python bin\compile --jdk $(JDK_PROFILE) --sdk --backend $(BACKEND)

sdk-jdk21:
	python bin\compile --jdk jdk21 --sdk --backend $(BACKEND)

sdk-jdk22plus:
	python bin\compile --jdk jdk22plus --sdk --backend $(BACKEND)

checkstyle:
	.\mvnw checkstyle:check

# The unit tests that need no GPU: TC's (the compiler's lit file tests and API tests; every build
# runs them too) and tornado-api/tornado-runtime's. The JDK profiles skip surefire elsewhere, so force
# it on here. `clean` + `-am` rebuilds the modules in one reactor so stale class files from another
# JDK profile (jdk21 compiles at 21, jdk22plus at 22) never reach the test classpath; the target is
# safe to run right after a different profile's build. Override the profile with JDK=jdk22plus
# (JDK is declared once, near BACKEND, at the top of this file). `test-reflection` is the old name.
TC_MODULES = tornado-compiler/tc-check,tornado-compiler/tc-ir,tornado-compiler/tc-frontend,tornado-compiler/tc-opt,tornado-compiler/tc-lower,tornado-compiler/tc-codegen,tornado-compiler/tc-tornado,tornado-compiler/tc-runtime,tornado-compiler/tc-debug,tornado-compiler/tc-gpu
test-nogpu:
	.\mvnw -P$(JDK_PROFILE) -pl tornado-api,tornado-runtime,$(TC_MODULES) -am clean test -DskipTests=false

test-reflection: test-nogpu

clean:
	.\mvnw -Popencl-backend,cuda-backend clean

example:
	%TORNADOVM_HOME%\bin\tornado.exe --printKernel --debug -m tornado.examples/uk.ac.manchester.tornado.examples.VectorAddInt --params="8192"

tests:
	del /f tornado_unittests.log
	%TORNADOVM_HOME%\bin\tornado.exe --devices
	%TORNADOVM_HOME%\bin\tornado-test.exe --verbose
	%TORNADOVM_HOME%\bin\tornado-test.exe -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

fast-tests:
	del /f tornado_unittests.log
	%TORNADOVM_HOME%\bin\tornado.exe --devices
	%TORNADOVM_HOME%\bin\tornado-test.exe --verbose --quickPass
	%TORNADOVM_HOME%\bin\tornado-test.exe -V -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

tests-uncompressed:
	del /f tornado_unittests.log
	%TORNADOVM_HOME%\bin\tornado.exe --devices
	%TORNADOVM_HOME%\bin\tornado-test.exe --verbose --uncompressed
	%TORNADOVM_HOME%\bin\tornado-test.exe -V --uncompressed -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd

fast-tests-uncompressed:
	del /f tornado_unittests.log
	%TORNADOVM_HOME%\bin\tornado.exe --devices
	%TORNADOVM_HOME%\bin\tornado-test.exe --verbose --quickPass --uncompressed
	%TORNADOVM_HOME%\bin\tornado-test.exe -V --uncompressed -J"-Dtornado.device.memory=1MB" uk.ac.manchester.tornado.unittests.fails.HeapFail#test03
	%TORNADOVM_HOME%\bin\test-native.cmd


test-slam:
	%TORNADOVM_HOME%\bin\tornado-test.exe -V --fast uk.ac.manchester.tornado.unittests.slam.GraphicsTests

docs:
	sphinx-build -M html docs/source/ docs/build

# Generate IntelliJ IDEA project files (developer-only)
# Prerequisites: build TornadoVM first and run setvars.cmd
intellijinit:
	python bin\tornadovm-intellij-init