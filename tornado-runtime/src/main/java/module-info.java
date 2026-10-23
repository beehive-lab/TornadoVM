import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;

open module tornado.runtime {
    requires java.logging;
    requires jdk.unsupported;

    requires transitive tornado.api;
    requires java.management;
    requires jdk.management;

    exports uk.ac.manchester.tornado.runtime;
    exports uk.ac.manchester.tornado.runtime.ffm;
    exports uk.ac.manchester.tornado.runtime.analyzer;
    exports uk.ac.manchester.tornado.runtime.common;
    exports uk.ac.manchester.tornado.runtime.common.enums;
    exports uk.ac.manchester.tornado.runtime.common.exceptions;
    exports uk.ac.manchester.tornado.runtime.directives;
    exports uk.ac.manchester.tornado.runtime.domain;
    exports uk.ac.manchester.tornado.runtime.graph;
    exports uk.ac.manchester.tornado.runtime.graph.nodes;
    exports uk.ac.manchester.tornado.runtime.library;
    exports uk.ac.manchester.tornado.runtime.library.spi;
    exports uk.ac.manchester.tornado.runtime.profiler;
    exports uk.ac.manchester.tornado.runtime.tasks;
    exports uk.ac.manchester.tornado.runtime.tasks.meta;
    exports uk.ac.manchester.tornado.runtime.utils;

    uses TornadoBackendProvider;
    uses uk.ac.manchester.tornado.runtime.common.KernelAccess.Provider;
    uses TornadoLibraryProvider;
}
