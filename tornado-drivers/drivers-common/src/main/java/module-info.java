open module tornado.drivers.common {
    requires transitive jdk.internal.vm.ci;
    requires transitive jdk.graal.compiler;
    requires transitive tornado.runtime;
    requires org.graalvm.word;

    exports uk.ac.manchester.tornado.drivers.providers;
    exports uk.ac.manchester.tornado.drivers.common;
    exports uk.ac.manchester.tornado.drivers.common.architecture;
    exports uk.ac.manchester.tornado.drivers.common.mm;
    exports uk.ac.manchester.tornado.drivers.common.code;
    exports uk.ac.manchester.tornado.drivers.common.logging;
    exports uk.ac.manchester.tornado.drivers.common.compiler.phases.guards;
    exports uk.ac.manchester.tornado.drivers.common.compiler.phases.memalloc;
    exports uk.ac.manchester.tornado.drivers.common.compiler.phases.loops;
    exports uk.ac.manchester.tornado.drivers.common.compiler.phases.utils;
    exports uk.ac.manchester.tornado.drivers.common.compiler.phases.analysis;
    exports uk.ac.manchester.tornado.drivers.common.utils;
    exports uk.ac.manchester.tornado.drivers.common.power;
}
