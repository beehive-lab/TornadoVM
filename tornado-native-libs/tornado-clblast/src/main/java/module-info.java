open module tornado.clblast {
    requires transitive tornado.api;
    requires tornado.runtime;

    exports uk.ac.manchester.tornado.clblast;
    exports uk.ac.manchester.tornado.clblast.enums;
    exports uk.ac.manchester.tornado.clblast.provider;

    provides uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider with
            uk.ac.manchester.tornado.clblast.provider.CLBlastLibraryProvider;
}
