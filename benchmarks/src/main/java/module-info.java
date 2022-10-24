module tornado.benchmarks {
    requires org.apache.commons.lang3;
    requires java.logging;
    requires org.apache.logging.log4j;
    requires jmh.core;

    requires transitive junit;
    requires transitive lucene.core;
    requires transitive tornado.api;
    requires transitive tornado.matrices;

    exports uk.ac.manchester.tornado.benchmarks;
    exports uk.ac.manchester.tornado.benchmarks.addImage;
    exports uk.ac.manchester.tornado.benchmarks.blackscholes;
    exports uk.ac.manchester.tornado.benchmarks.blurFilter;
    exports uk.ac.manchester.tornado.benchmarks.convolvearray;
    exports uk.ac.manchester.tornado.benchmarks.convolveimage;
    exports uk.ac.manchester.tornado.benchmarks.dft;
    exports uk.ac.manchester.tornado.benchmarks.dgemm;
    exports uk.ac.manchester.tornado.benchmarks.dotimage;
    exports uk.ac.manchester.tornado.benchmarks.dotvector;
    exports uk.ac.manchester.tornado.benchmarks.euler;
    exports uk.ac.manchester.tornado.benchmarks.hilbert;
    exports uk.ac.manchester.tornado.benchmarks.mandelbrot;
    exports uk.ac.manchester.tornado.benchmarks.montecarlo;
    exports uk.ac.manchester.tornado.benchmarks.nbody;
    exports uk.ac.manchester.tornado.benchmarks.rotateimage;
    exports uk.ac.manchester.tornado.benchmarks.rotatevector;
    exports uk.ac.manchester.tornado.benchmarks.saxpy;
    exports uk.ac.manchester.tornado.benchmarks.sgemm;
    exports uk.ac.manchester.tornado.benchmarks.sgemv;
    exports uk.ac.manchester.tornado.benchmarks.spmv;
    exports uk.ac.manchester.tornado.benchmarks.stencil;
}
