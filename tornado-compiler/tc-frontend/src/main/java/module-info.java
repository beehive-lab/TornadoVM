/**
 * The TC Java frontend: class-file reading and translation of kernel methods into TC-SSA.
 */
module tornado.tc.frontend {
    requires transitive tornado.tc.ir;

    exports uk.ac.manchester.tornado.tc.frontend;
    exports uk.ac.manchester.tornado.tc.frontend.classfile;
    exports uk.ac.manchester.tornado.tc.frontend.tools;
}
