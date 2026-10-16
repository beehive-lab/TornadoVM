/**
 * TornadoVM-specific lowering on TC-SSA (ABI lowering, parallel loops, KernelContext, reductions).
 */
module tornado.tc.tornado {
    requires transitive tornado.tc.ir;

    exports uk.ac.manchester.tornado.tc.tornado;
    exports uk.ac.manchester.tornado.tc.tornado.tools;
}
