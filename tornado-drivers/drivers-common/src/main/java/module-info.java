open module tornado.drivers.common {
    requires transitive jdk.internal.vm.ci;
    requires transitive jdk.internal.vm.compiler;
    requires transitive tornado.runtime;

    exports uk.ac.manchester.tornado.drivers.providers;
    exports uk.ac.manchester.tornado.drivers.common;
    exports uk.ac.manchester.tornado.drivers.common.architecture;
    exports uk.ac.manchester.tornado.drivers.common.mm;
    exports uk.ac.manchester.tornado.drivers.common.code;
    exports uk.ac.manchester.tornado.drivers.common.compiler.phases;
    exports uk.ac.manchester.tornado.drivers.common.logging;
}
