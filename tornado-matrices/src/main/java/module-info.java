module tornado.matrices {
    requires ejml.core;

    requires transitive ejml.simple;
    requires transitive tornado.api;

    exports uk.ac.manchester.tornado.matrix;

}
