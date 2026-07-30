all: build

# Variable passed for the build process. List of backend/s to use { opencl, cuda, metal }. The default one is `opencl`.
# nmake BACKENDS="<comma_separated_backend_list>"
BACKEND = opencl

build jdk21:
	python bin\compile --jdk jdk21 --backend $(BACKEND)

rebuild-deps-jdk21:
	python bin\compile --jdk jdk21 --rebuild --backend $(BACKEND)

jdk27:
	python bin\compile --jdk jdk27 --backend $(BACKEND)

rebuild-deps-jdk27:
	python bin\compile --jdk jdk27 --rebuild --backend $(BACKEND)

rebuild-deps:
	python bin\compile --jdk graal-jdk-21 --rebuild --backend $(BACKEND)

graal-jdk-21:
	python bin\compile --jdk graal-jdk-21 --backend $(BACKEND)

polyglot:
	python bin\compile --jdk graal-jdk-21 --backend $(BACKEND) --polyglot

mvn-single-threaded-jdk21:
	python bin/compile --jdk jdk21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-graal-jdk-21:
	python bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --mvn_single_threaded

mvn-single-threaded-polyglot:
	python bin/compile --jdk graal-jdk-21 --backend $(BACKEND) --mvn_single_threaded --polyglot


cuda:
	python bin\compile --jdk jdk21 --backend cuda

sdk:
	python bin\compile --jdk jdk21 --sdk --backend $(BACKEND)

checkstyle:
	.\mvnw checkstyle:check

# Pure-JVM (no-GPU) unit tests for the reflection JVMCI layer. Every JDK profile skips surefire,
# so force it on here. Override the profile with JDK=jdk25|jdk26|jdk27 to run under another JDK.
JDK = jdk21
unit-tests:
	.\mvnw -P$(JDK) -pl tornado-runtime test -DskipTests=false

# Only the reflection JVMCI-layer suites (uk.ac.manchester.tornado.runtime.jvmci.reflection.*Test) —
# the standalone metadata API. Same JDK override as unit-tests.
test-reflection:
	.\mvnw -P$(JDK) -pl tornado-runtime test -DskipTests=false -Dtest="uk.ac.manchester.tornado.runtime.jvmci.reflection.*Test"

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