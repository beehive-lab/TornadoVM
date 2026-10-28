open module tornado.cusparse {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.cusparse;
    exports uk.ac.manchester.tornado.cusparse.provider;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with //
            uk.ac.manchester.tornado.cusparse.provider.CusparseLibraryProvider;
}
