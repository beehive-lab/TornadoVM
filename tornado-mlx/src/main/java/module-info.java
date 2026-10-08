open module tornado.mlx {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.mlx;
    exports uk.ac.manchester.tornado.mlx.provider;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with //
            uk.ac.manchester.tornado.mlx.provider.MlxLibraryProvider;
}
