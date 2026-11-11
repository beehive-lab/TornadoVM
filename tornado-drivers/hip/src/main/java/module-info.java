import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.hip {
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;

    exports uk.ac.manchester.tornado.drivers.hip;
    exports uk.ac.manchester.tornado.drivers.hip.enums;
    exports uk.ac.manchester.tornado.drivers.hip.exceptions;
    exports uk.ac.manchester.tornado.drivers.hip.mm;
    exports uk.ac.manchester.tornado.drivers.hip.runtime;
    exports uk.ac.manchester.tornado.drivers.hip.power;
    exports uk.ac.manchester.tornado.drivers.hip.scheduler;
    exports uk.ac.manchester.tornado.drivers.hip.natives;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.hip.HIPTornadoDriverProvider;
}
