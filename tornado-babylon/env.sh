# Source from tornado-babylon/: Babylon JDK + TornadoVM spike SDK + HAT jars.
# Override BABYLON (Babylon checkout with a built JDK and hat/build) and TORNADOVM_HOME as needed.
BABYLON=${BABYLON:-$HOME/src/babylon-new}
export JAVA_HOME=${BABYLON_JDK:-$BABYLON/build/linux-x86_64-server-release/images/jdk}
export PATH=$JAVA_HOME/bin:$PATH
HAT_BUILD=$BABYLON/hat/build
TORNADO_JARS=$TORNADOVM_HOME/share/java/tornado
