module tornado.drivers.cuda {
    requires transitive jdk.internal.vm.ci;
    requires transitive jdk.internal.vm.compiler;
    requires transitive tornado.api;
    requires transitive tornado.runtime;

    exports uk.ac.manchester.tornado.drivers.cuda;
    exports uk.ac.manchester.tornado.drivers.cuda.enums;
    exports uk.ac.manchester.tornado.drivers.cuda.graal;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.asm;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.backend;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.compiler;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.lir;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.meta;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.nodes;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.nodes.calc;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.nodes.logic;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.phases;
    exports uk.ac.manchester.tornado.drivers.cuda.mm;
    exports uk.ac.manchester.tornado.drivers.cuda.runtime;

    provides uk.ac.manchester.tornado.runtime.TornadoDriverProvider with
            uk.ac.manchester.tornado.drivers.cuda.CUDATornadoDriverProvider;

}
