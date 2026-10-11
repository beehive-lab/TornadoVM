open module tornado.drivers.common {
    requires transitive tornado.runtime;
    requires transitive tornado.tc.runtime;
    requires tornado.tc.debug;
    
    exports uk.ac.manchester.tornado.drivers.common;
    exports uk.ac.manchester.tornado.drivers.common.mm;
    exports uk.ac.manchester.tornado.drivers.common.logging;
    exports uk.ac.manchester.tornado.drivers.common.utils;
    exports uk.ac.manchester.tornado.drivers.common.power;
    exports uk.ac.manchester.tornado.drivers.common.ffm;
    exports uk.ac.manchester.tornado.drivers.common.tc;

    provides uk.ac.manchester.tornado.runtime.common.KernelAccess.Provider with uk.ac.manchester.tornado.drivers.common.tc.TcAccessProvider;
}
