/**
 * TC source emitters: the C core printer, target dialects and the intrinsic registry.
 */
module tornado.tc.codegen {
    requires transitive tornado.tc.lower;

    exports uk.ac.manchester.tornado.tc.codegen;
    exports uk.ac.manchester.tornado.tc.codegen.tools;
}
