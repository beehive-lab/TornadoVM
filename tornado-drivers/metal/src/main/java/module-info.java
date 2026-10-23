import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.metal {
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;

    requires java.management;
    requires jdk.management;

    exports uk.ac.manchester.tornado.drivers.metal;
    exports uk.ac.manchester.tornado.drivers.metal.enums;
    exports uk.ac.manchester.tornado.drivers.metal.ffm;
    exports uk.ac.manchester.tornado.drivers.metal.exceptions;
    exports uk.ac.manchester.tornado.drivers.metal.mm;
    exports uk.ac.manchester.tornado.drivers.metal.runtime;
    exports uk.ac.manchester.tornado.drivers.metal.power;
    exports uk.ac.manchester.tornado.drivers.metal.scheduler;
    exports uk.ac.manchester.tornado.drivers.metal.natives;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.metal.MetalTornadoDriverProvider;
}
