import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;

open module tornado.runtime {
    requires java.logging;
    requires jdk.unsupported;
    requires org.graalvm.collections;

    requires transitive jdk.internal.vm.ci;
    requires transitive jdk.internal.vm.compiler;
    requires transitive tornado.api;
    requires commons.math3;
    requires snmp4j;
    requires java.management;

    exports uk.ac.manchester.tornado.runtime;
    exports uk.ac.manchester.tornado.runtime.analyzer;
    exports uk.ac.manchester.tornado.runtime.common;
    exports uk.ac.manchester.tornado.runtime.common.enums;
    exports uk.ac.manchester.tornado.runtime.common.exceptions;
    exports uk.ac.manchester.tornado.runtime.directives;
    exports uk.ac.manchester.tornado.runtime.domain;
    exports uk.ac.manchester.tornado.runtime.graal;
    exports uk.ac.manchester.tornado.runtime.graal.backend;
    exports uk.ac.manchester.tornado.runtime.graal.compiler;
    exports uk.ac.manchester.tornado.runtime.graal.nodes;
    exports uk.ac.manchester.tornado.runtime.graal.nodes.logic;
    exports uk.ac.manchester.tornado.runtime.graal.nodes.calc;
    exports uk.ac.manchester.tornado.runtime.graal.phases;
    exports uk.ac.manchester.tornado.runtime.graph;
    exports uk.ac.manchester.tornado.runtime.graph.nodes;
    exports uk.ac.manchester.tornado.runtime.profiler;
    exports uk.ac.manchester.tornado.runtime.sketcher;
    exports uk.ac.manchester.tornado.runtime.tasks;
    exports uk.ac.manchester.tornado.runtime.tasks.meta;
    exports uk.ac.manchester.tornado.runtime.utils;
    exports uk.ac.manchester.tornado.runtime.graal.phases.sketcher;
    exports uk.ac.manchester.tornado.runtime.graal.nodes.interfaces;

    uses TornadoBackendProvider;
}
