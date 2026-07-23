open module tornado.cutensor {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.cutensor;
    exports uk.ac.manchester.tornado.cutensor.provider;
    exports uk.ac.manchester.tornado.cutensor.tests;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with //
            uk.ac.manchester.tornado.cutensor.provider.CutensorLibraryProvider;
}
