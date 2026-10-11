import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.cuda {
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;

    exports uk.ac.manchester.tornado.drivers.cuda;
    exports uk.ac.manchester.tornado.drivers.cuda.enums;
    exports uk.ac.manchester.tornado.drivers.cuda.exceptions;
    exports uk.ac.manchester.tornado.drivers.cuda.mm;
    exports uk.ac.manchester.tornado.drivers.cuda.runtime;
    exports uk.ac.manchester.tornado.drivers.cuda.power;
    exports uk.ac.manchester.tornado.drivers.cuda.scheduler;
    exports uk.ac.manchester.tornado.drivers.cuda.natives;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.cuda.CUDATornadoDriverProvider;
}
