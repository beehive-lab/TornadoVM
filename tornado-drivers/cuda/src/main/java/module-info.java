import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.cuda {
    requires transitive jdk.internal.vm.ci;
    requires transitive jdk.internal.vm.compiler;
    requires transitive org.graalvm.collections;
    requires transitive org.graalvm.word;
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;

    exports uk.ac.manchester.tornado.drivers.cuda;
    exports uk.ac.manchester.tornado.drivers.cuda.builtins;
    exports uk.ac.manchester.tornado.drivers.cuda.enums;
    exports uk.ac.manchester.tornado.drivers.cuda.exceptions;
    exports uk.ac.manchester.tornado.drivers.cuda.graal;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.asm;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.backend;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.compiler;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.lir;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.meta;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.nodes;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.nodes.logic;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.phases;
    exports uk.ac.manchester.tornado.drivers.cuda.graal.snippets;
    exports uk.ac.manchester.tornado.drivers.cuda.mm;
    exports uk.ac.manchester.tornado.drivers.cuda.runtime;
    exports uk.ac.manchester.tornado.drivers.cuda.tests;
    exports uk.ac.manchester.tornado.drivers.cuda.power;
    exports uk.ac.manchester.tornado.drivers.cuda.scheduler;
    exports uk.ac.manchester.tornado.drivers.cuda.natives;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.cuda.CUDATornadoDriverProvider;
}
