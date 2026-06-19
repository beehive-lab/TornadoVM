import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

module tornado.drivers.cuda {
    requires transitive jdk.internal.vm.ci;
    requires transitive jdk.internal.vm.compiler;
    requires transitive org.graalvm.collections;
    requires transitive org.graalvm.word;
    requires transitive tornado.api;
    requires transitive tornado.runtime;
    requires tornado.drivers.common;

    exports uk.ac.manchester.tornado.drivers.cuda;

    provides TornadoBackendProvider with
            uk.ac.manchester.tornado.drivers.cuda.CUDATornadoDriverProvider;
}
