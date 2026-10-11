import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.opencl {
    requires jdk.unsupported;
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;

    exports uk.ac.manchester.tornado.drivers.opencl;
    exports uk.ac.manchester.tornado.drivers.opencl.enums;
    exports uk.ac.manchester.tornado.drivers.opencl.ffm;
    exports uk.ac.manchester.tornado.drivers.opencl.exceptions;
    exports uk.ac.manchester.tornado.drivers.opencl.mm;
    exports uk.ac.manchester.tornado.drivers.opencl.runtime;
    exports uk.ac.manchester.tornado.drivers.opencl.power;
    exports uk.ac.manchester.tornado.drivers.opencl.scheduler;
    exports uk.ac.manchester.tornado.drivers.opencl.natives;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.opencl.OCLTornadoDriverProvider;
}
