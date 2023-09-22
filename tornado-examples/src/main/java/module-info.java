open module tornado.examples {
    requires transitive java.desktop;
    requires transitive tornado.api;
    requires org.graalvm.polyglot;

    exports uk.ac.manchester.tornado.examples;
    exports uk.ac.manchester.tornado.examples.arrays;
    exports uk.ac.manchester.tornado.examples.common;
    exports uk.ac.manchester.tornado.examples.compute;
    exports uk.ac.manchester.tornado.examples.dynamic;
    exports uk.ac.manchester.tornado.examples.fft;
    exports uk.ac.manchester.tornado.examples.flatmap;
    exports uk.ac.manchester.tornado.examples.kernelcontext.compute;
    exports uk.ac.manchester.tornado.examples.kernelcontext.matrices;
    exports uk.ac.manchester.tornado.examples.kernelcontext.reductions;
    exports uk.ac.manchester.tornado.examples.matrices;
    exports uk.ac.manchester.tornado.examples.polyglot;
    exports uk.ac.manchester.tornado.examples.reductions;
    exports uk.ac.manchester.tornado.examples.vectors;
}
