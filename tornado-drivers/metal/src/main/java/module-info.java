import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.metal {
    requires transitive jdk.internal.vm.ci;
    requires transitive jdk.internal.vm.compiler;
    requires transitive org.graalvm.collections;
    requires transitive org.graalvm.word;
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;

    requires java.management;
    requires jdk.management;

    exports uk.ac.manchester.tornado.drivers.metal;
    exports uk.ac.manchester.tornado.drivers.metal.builtins;
    exports uk.ac.manchester.tornado.drivers.metal.enums;
    exports uk.ac.manchester.tornado.drivers.metal.exceptions;
    exports uk.ac.manchester.tornado.drivers.metal.graal;
    exports uk.ac.manchester.tornado.drivers.metal.graal.asm;
    exports uk.ac.manchester.tornado.drivers.metal.graal.backend;
    exports uk.ac.manchester.tornado.drivers.metal.graal.compiler;
    exports uk.ac.manchester.tornado.drivers.metal.graal.compiler.plugins;
    exports uk.ac.manchester.tornado.drivers.metal.graal.lir;
    exports uk.ac.manchester.tornado.drivers.metal.graal.meta;
    exports uk.ac.manchester.tornado.drivers.metal.graal.nodes;
    exports uk.ac.manchester.tornado.drivers.metal.graal.nodes.logic;
    exports uk.ac.manchester.tornado.drivers.metal.graal.nodes.vector;
    exports uk.ac.manchester.tornado.drivers.metal.graal.phases;
    exports uk.ac.manchester.tornado.drivers.metal.graal.snippets;
    exports uk.ac.manchester.tornado.drivers.metal.mm;
    exports uk.ac.manchester.tornado.drivers.metal.runtime;
    exports uk.ac.manchester.tornado.drivers.metal.tests;
    exports uk.ac.manchester.tornado.drivers.metal.power;
    exports uk.ac.manchester.tornado.drivers.metal.scheduler;
    exports uk.ac.manchester.tornado.drivers.metal.natives;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.metal.MetalTornadoDriverProvider;
}
