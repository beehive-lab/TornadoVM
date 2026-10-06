all: build

# Variable passed for the build process. List of backend/s to use { opencl, cuda, metal }. The default one is `opencl`.
# nmake BACKENDS="<comma_separated_backend_list>"
BACKEND = opencl

# JDK profile for the targets below that do not name one { jdk21, jdk22plus }. jdk21 is an
# enable-preview SDK pinned to exactly JDK 21; jdk22plus serves every JDK from 22.
# nmake build JDK_PROFILE=jdk21
JDK_PROFILE = jdk22plus

build:
	python bin\compile --jdk $(JDK_PROFILE) --backend $(BACKEND)

jdk21:
	python bin\compile --jdk jdk21 --backend $(BACKEND)

# jdk25/jdk26/jdk27 are kept as aliases so existing scripts and muscle memory keep working;
# they all produce the same artifact.
jdk22plus jdk25 jdk26 jdk27:
	python bin\compile --jdk jdk22plus --backend $(BACKEND)

rebuild-deps:
	python bin\compile --jdk $(JDK_PROFILE) --rebuild --backend $(BACKEND)

rebuild-deps-jdk21:
	python bin\compile --jdk jdk21 --rebuild --backend $(BACKEND)

rebuild-deps-jdk22plus rebuild-deps-jdk25 rebuild-deps-jdk26 rebuild-deps-jdk27:
	python bin\compile --jdk jdk22plus --rebuild --backend $(BACKEND)

mvn-single-threaded:
	python bin/compile --jdk $(JDK_PROFILE) --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-jdk21:
	python bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-jdk22plus:
	python bin/compile --jdk jdk22plus --backend $(BACKEND) --mvn_single_threaded

cuda:
	python bin\compile --jdk $(JDK_PROFILE) --backend cuda

sdk:
	python bin\compile --jdk $(JDK_PROFILE) --sdk --backend $(BACKEND)

sdk-jdk21:
	python bin\compile --jdk jdk21 --sdk --backend $(BACKEND)

sdk-jdk22plus:
	python bin\compile --jdk jdk22plus --sdk --backend $(BACKEND)

checkstyle:
	.\mvnw checkstyle:check

# Pure-JVM (no-GPU) unit tests for the reflection metadata layer. The build skips surefire by
# default, so force it on here. `clean` + `-am` rebuilds tornado-api and tornado-runtime together
# in the same reactor so no stale class files leak into the test classpath.
test-reflection:
	.\mvnw -P$(JDK_PROFILE) -pl tornado-api,tornado-runtime -am clean test -DskipTests=false

# Only the reflection metadata-layer suites (uk.ac.manchester.tornado.runtime.meta.reflection.*Test) —
# the standalone metadata API, a subset of what test-reflection runs. Same clean+-am rationale as
# test-reflection.
test-reflection-only:
	.\mvnw -P$(JDK_PROFILE) -pl tornado-api,tornado-runtime -am clean test -DskipTests=false -Dtest="uk.ac.manchester.tornado.runtime.meta.reflection.*Test"

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