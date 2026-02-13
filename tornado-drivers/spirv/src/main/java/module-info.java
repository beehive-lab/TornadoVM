import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.spirv {
    requires transitive jdk.internal.vm.ci;
//    requires transitive jdk.internal.vm.compiler;
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;
    requires beehive.spirv.lib;
    requires beehive.levelzero.jni;

    // FIXME: Remove dependency to tornado.drivers.opencl
    requires tornado.drivers.opencl;

    exports uk.ac.manchester.tornado.drivers.spirv;
    exports uk.ac.manchester.tornado.drivers.spirv.graal;
    exports uk.ac.manchester.tornado.drivers.spirv.graal.asm;
    exports uk.ac.manchester.tornado.drivers.spirv.graal.compiler;
    exports uk.ac.manchester.tornado.drivers.spirv.graal.lir;
    exports uk.ac.manchester.tornado.drivers.spirv.graal.meta;
    exports uk.ac.manchester.tornado.drivers.spirv.graal.nodes;
    exports uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;
    exports uk.ac.manchester.tornado.drivers.spirv.graal.phases;
    exports uk.ac.manchester.tornado.drivers.spirv.mm;
    exports uk.ac.manchester.tornado.drivers.spirv.runtime;
    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.spirv.SPIRVTornadoDriverProvider;
}
