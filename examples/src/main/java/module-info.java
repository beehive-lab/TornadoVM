open module tornado.examples {
    requires ejml.ddense;

    requires transitive ejml.core;
    requires transitive lucene.core;
    requires transitive java.desktop;
    requires transitive tornado.api;
    requires org.graalvm.sdk;

    exports uk.ac.manchester.tornado.examples;
    exports uk.ac.manchester.tornado.examples.arrays;
    exports uk.ac.manchester.tornado.examples.common;
    exports uk.ac.manchester.tornado.examples.compression;
    exports uk.ac.manchester.tornado.examples.compute;
    exports uk.ac.manchester.tornado.examples.kernelcontext.compute;
    exports uk.ac.manchester.tornado.examples.kernelcontext.matrices;
    exports uk.ac.manchester.tornado.examples.kernelcontext.reductions;
    exports uk.ac.manchester.tornado.examples.dynamic;
    exports uk.ac.manchester.tornado.examples.fpga;
    exports uk.ac.manchester.tornado.examples.functional;
    exports uk.ac.manchester.tornado.examples.lang;
    exports uk.ac.manchester.tornado.examples.matrices;
    exports uk.ac.manchester.tornado.examples.memory;
    exports uk.ac.manchester.tornado.examples.objects;
    exports uk.ac.manchester.tornado.examples.ooo;
    exports uk.ac.manchester.tornado.examples.paralleliser;
    exports uk.ac.manchester.tornado.examples.reductions;
    exports uk.ac.manchester.tornado.examples.vectors;
    exports uk.ac.manchester.tornado.examples.polyglot;
}
