open module tornado.cudf {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.cudf;
    exports uk.ac.manchester.tornado.cudf.provider;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with //
            uk.ac.manchester.tornado.cudf.provider.CudfLibraryProvider;
}
