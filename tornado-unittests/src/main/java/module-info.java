open module tornado.unittests {
    requires transitive junit;
    requires transitive tornado.api;
    requires lucene.core;
    requires java.desktop;
    requires jdk.incubator.vector;

    exports uk.ac.manchester.tornado.unittests;
    exports uk.ac.manchester.tornado.unittests.api;
    exports uk.ac.manchester.tornado.unittests.arrays;
    exports uk.ac.manchester.tornado.unittests.atomics;
    exports uk.ac.manchester.tornado.unittests.batches;
    exports uk.ac.manchester.tornado.unittests.bitsets;
    exports uk.ac.manchester.tornado.unittests.branching;
    exports uk.ac.manchester.tornado.unittests.common;
    exports uk.ac.manchester.tornado.unittests.fields;
    exports uk.ac.manchester.tornado.unittests.flatmap;
    exports uk.ac.manchester.tornado.unittests.functional;
    exports uk.ac.manchester.tornado.unittests.images;
    exports uk.ac.manchester.tornado.unittests.kernelcontext.api;
    exports uk.ac.manchester.tornado.unittests.kernelcontext.matrices;
    exports uk.ac.manchester.tornado.unittests.kernelcontext.reductions;
    exports uk.ac.manchester.tornado.unittests.instances;
    exports uk.ac.manchester.tornado.unittests.lambdas;
    exports uk.ac.manchester.tornado.unittests.logic;
    exports uk.ac.manchester.tornado.unittests.loops;
    exports uk.ac.manchester.tornado.unittests.math;
    exports uk.ac.manchester.tornado.unittests.matrices;
    exports uk.ac.manchester.tornado.unittests.prebuilt;
    exports uk.ac.manchester.tornado.unittests.profiler;
    exports uk.ac.manchester.tornado.unittests.reductions;
    exports uk.ac.manchester.tornado.unittests.slam;
    exports uk.ac.manchester.tornado.unittests.tasks;
    exports uk.ac.manchester.tornado.unittests.temporary.values;
    exports uk.ac.manchester.tornado.unittests.tools;
    exports uk.ac.manchester.tornado.unittests.vectortypes;
    exports uk.ac.manchester.tornado.unittests.virtualization;
    exports uk.ac.manchester.tornado.unittests.memory.leak;
}
