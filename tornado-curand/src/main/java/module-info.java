open module tornado.curand {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.curand;
    exports uk.ac.manchester.tornado.curand.enums;
    exports uk.ac.manchester.tornado.curand.provider;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with //
            uk.ac.manchester.tornado.curand.provider.CuRandLibraryProvider;
}
