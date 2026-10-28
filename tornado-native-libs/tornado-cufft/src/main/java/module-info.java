open module tornado.cufft {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.cufft;
    exports uk.ac.manchester.tornado.cufft.provider;
    exports uk.ac.manchester.tornado.cufft.tests;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with //
            uk.ac.manchester.tornado.cufft.provider.CuFftLibraryProvider;
}
