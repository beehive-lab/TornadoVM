import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.opencl {
    requires transitive jdk.internal.vm.ci;
    requires transitive jdk.internal.vm.compiler;
    requires transitive org.graalvm.collections;
    requires transitive org.graalvm.word;
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;

    exports uk.ac.manchester.tornado.drivers.opencl;
    exports uk.ac.manchester.tornado.drivers.opencl.builtins;
    exports uk.ac.manchester.tornado.drivers.opencl.enums;
    exports uk.ac.manchester.tornado.drivers.opencl.exceptions;
    exports uk.ac.manchester.tornado.drivers.opencl.graal;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.asm;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.backend;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.compiler;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.lir;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.meta;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.nodes;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.nodes.logic;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.phases;
    exports uk.ac.manchester.tornado.drivers.opencl.graal.snippets;
    exports uk.ac.manchester.tornado.drivers.opencl.mm;
    exports uk.ac.manchester.tornado.drivers.opencl.runtime;
    exports uk.ac.manchester.tornado.drivers.opencl.tests;
    exports uk.ac.manchester.tornado.drivers.opencl.power;
    exports uk.ac.manchester.tornado.drivers.opencl.scheduler;
    exports uk.ac.manchester.tornado.drivers.opencl.natives;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.opencl.OCLTornadoDriverProvider;
}
