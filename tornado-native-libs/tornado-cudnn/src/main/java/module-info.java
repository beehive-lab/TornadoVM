open module tornado.cudnn {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.cudnn;
    exports uk.ac.manchester.tornado.cudnn.provider;
    exports uk.ac.manchester.tornado.cudnn.tests;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with //
            uk.ac.manchester.tornado.cudnn.provider.CuDnnLibraryProvider;
}
