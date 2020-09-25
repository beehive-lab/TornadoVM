#!/usr/bin/env bash

PACKAGE_LIST=(
--vm.-add-modules=ALL-SYSTEM,,tornado.runtime,tornado.annotation,tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.ci/jdk.vm.ci.common=tornado.drivers.opencl,jdk.internal.vm.compiler
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.hotspot.meta=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.replacements.classfile=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.common.alloc=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.common.util=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.common.cfg=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.lir=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.lir.framemap=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.ci/jdk.vm.ci.meta=tornado.drivers.opencl,tornado.runtime,tornado.annotation
--vm.-add-exports=jdk.internal.vm.ci/jdk.vm.ci.code=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.graph=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.graph.spi=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.lir.gen=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodeinfo=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.calc=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.spi=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.api.runtime=tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.code=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.common=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.target=tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.debug=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.hotspot=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.java=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.lir.asm=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.lir.phases=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.graphbuilderconf=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.options=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases.tiers=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases.util=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.printer=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.runtime=tornado.runtime
--vm.-add-exports=jdk.internal.vm.ci/jdk.vm.ci.runtime=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.graph.iterators=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.java=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.bytecode=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases.common=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.common.spi=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.api.replacements=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.replacements=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases.common.inlining=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.phases=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.common.type=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.extended=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.loop=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases.common.inlining.info=tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases.common.inlining.policy=tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases.common.inlining.walker=tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.loop.phases=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.debug=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.memory=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.util=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.virtual=tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.lir.constopt=tornado.runtime
--vm.-add-opens=jdk.internal.vm.ci/jdk.vm.ci.hotspot=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.asm=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.gc=tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.cfg=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.phases.schedule=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.virtual.phases.ea=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.lir.ssa=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.common.calc=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.gen=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.core.match=tornado.drivers.opencl
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.memory.address=tornado.drivers.opencl,tornado.runtime
--vm.-add-exports=jdk.internal.vm.compiler/org.graalvm.compiler.nodes.type=tornado.drivers.opencl
)

if [ -z "${TORNADO_SDK}" ]; then
    echo "Please ensure the TORNADO_SDK environment variable is set correctly"
    exit -1
fi

if [ -z "${JAVA_HOME}" ]; then
    echo "Please ensure the JAVA_HOME environment variable is set correctly"
fi

function print_usage {
    echo "usage:    bash node.sh [options] server.js "
    echo ""
    echo "              -h|--help             This message"
    echo "              -d|--debug            Enable debug output"
    echo ""
    echo "              --printBytecodes      Print TornadoVM bytecodes"
    echo "              --igv                 Dump GRAAL IR into IGV"
    echo "              --printKernel         Print autogenerated OpenCL C Kernel"
    echo "              --debug               Print debugging information"
    echo "              --version             Print tornado version information"
    echo "              --printFlags          Prints Tornado Java flags and exits"
    echo ""
    exit 0
}

function print_version {
    cat "${TORNADO_SDK}/etc/tornado.release"
    exit 0
}

NODE_CMD=${JAVA_HOME}/bin/node
JAVA_CMD=${JAVA_HOME}/bin/java
JAVA_VERSION=$("$JAVA_CMD" -version 2>&1 | awk -F[\"\.] -v OFS=. 'NR==1{print $2,$3}')

TORNADO_FLAGS="--vm.Djava.library.path=${TORNADO_SDK}/lib "
if [ "$JAVA_VERSION" = "1.8" ]; then
  TORNADO_FLAGS="${TORNADO_FLAGS} --vm.Djava.ext.dirs=${TORNADO_SDK}/share/java/tornado"
elif [ "$JAVA_VERSION" = "11.0" ]; then
  TORNADO_FLAGS="${TORNADO_FLAGS} --vm.-module-path=.:${TORNADO_SDK}/share/java/tornado:."
fi

printflags=0
printdevices=0

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    --version)
    print_version
    shift
    ;;
    -d|--debug)
    TORNADO_FLAGS="${TORNADO_FLAGS} --vm.Dtornado.debug=True"
    shift # past argument
    ;;
    --igv)
    TORNADO_FLAGS="${TORNADO_FLAGS} --vm.Dgraal.Dump=*:5 --vm.Dgraal.PrintGraph=Network --vm.Dgraal.PrintCFG=false"
    shift # past argument
    ;;
    --printKernel)
    TORNADO_FLAGS="${TORNADO_FLAGS} --vm.Dtornado.print.kernel=True "
    shift
	;;
    --printBytecodes)
    TORNADO_FLAGS="${TORNADO_FLAGS} --vm.Dtornado.print.bytecodes=True "
    shift
    ;;
    --debug)
    TORNADO_FLAGS="${TORNADO_FLAGS} --vm.Dtornado.debug=True "
    shift
    ;;
    --printFlags)
    printflags=1
    shift
    ;;
    -h|--help)
    print_usage
    shift
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

PROVIDERS=" \
--vm.Dtornado.load.api.implementation=uk.ac.manchester.tornado.runtime.tasks.TornadoTaskSchedule \
--vm.Dtornado.load.runtime.implementation=uk.ac.manchester.tornado.runtime.TornadoCoreRuntime \
--vm.Dtornado.load.tornado.implementation=uk.ac.manchester.tornado.runtime.common.Tornado \
--vm.Dtornado.load.device.implementation.opencl=uk.ac.manchester.tornado.drivers.opencl.runtime.OCLDeviceFactory \
--vm.Dtornado.load.device.implementation.ptx=uk.ac.manchester.tornado.drivers.ptx.runtime.PTXDeviceFactory \
--vm.Dtornado.load.annotation.implementation=uk.ac.manchester.tornado.annotation.ASMClassVisitor \
--vm.Dtornado.load.annotation.parallel=uk.ac.manchester.tornado.api.annotations.Parallel "

JAVA_FLAGS="--jvm --polyglot --vm.XX:-UseCompressedOops ${TORNADO_FLAGS} ${PROVIDERS} "
if [ "$JAVA_VERSION" = "1.8" ]; then
  JAVA_FLAGS="${JAVA_FLAGS} --vm.XX:-UseJVMCIClassLoader "
elif [ "$JAVA_VERSION" = "11.0" ]; then
  JAVA_FLAGS="${JAVA_FLAGS} --vm.XX:+UseParallelOldGC --vm.XX:-UseBiasedLocking ${PACKAGE_LIST[*]} "
fi

if [ $printflags -eq 1 ] ; then
	echo $NODE_CMD
	echo $JAVA_FLAGS
	exit 0
fi

# echo $NODE_CMD
# printf "\n"
# echo $JAVA_FLAGS
# printf "\n"
# echo $@

${NODE_CMD} ${JAVA_FLAGS} $@
