import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.ptx {
    requires transitive jdk.internal.vm.ci;
//    requires transitive jdk.internal.vm.compiler;
    requires transitive org.graalvm.collections;
    requires transitive org.graalvm.word;
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;
    requires java.desktop;

    exports uk.ac.manchester.tornado.drivers.ptx;
    exports uk.ac.manchester.tornado.drivers.ptx.enums;
    exports uk.ac.manchester.tornado.drivers.ptx.graal;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.asm;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.backend;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.compiler;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.lir;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.meta;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.nodes;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.nodes.calc;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.nodes.vector;
    exports uk.ac.manchester.tornado.drivers.ptx.graal.phases;
    exports uk.ac.manchester.tornado.drivers.ptx.mm;
    exports uk.ac.manchester.tornado.drivers.ptx.runtime;
    exports uk.ac.manchester.tornado.drivers.ptx.power;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.ptx.PTXTornadoDriverProvider;
}
