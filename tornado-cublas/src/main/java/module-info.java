open module tornado.cublas {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.cublas;
    exports uk.ac.manchester.tornado.cublas.enums;
    exports uk.ac.manchester.tornado.cublas.provider;
    exports uk.ac.manchester.tornado.cublas.tests;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with uk.ac.manchester.tornado.cublas.provider.CuBlasLibraryProvider;
}
