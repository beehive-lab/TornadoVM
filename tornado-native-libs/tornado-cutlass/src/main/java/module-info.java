open module tornado.cutlass {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.cutlass;
    exports uk.ac.manchester.tornado.cutlass.provider;
    exports uk.ac.manchester.tornado.cutlass.tests;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with //
            uk.ac.manchester.tornado.cutlass.provider.CutlassLibraryProvider;
}
