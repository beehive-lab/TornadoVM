import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;
import uk.ac.manchester.tornado.runtime.autotune.spi.AutoTuneSearchStrategy;

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
    exports uk.ac.manchester.tornado.drivers.metal.autotune;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.metal.MetalTornadoDriverProvider;
    provides AutoTuneSearchStrategy with
            uk.ac.manchester.tornado.drivers.metal.autotune.MetalAutoTuneSearchStrategy;
}
