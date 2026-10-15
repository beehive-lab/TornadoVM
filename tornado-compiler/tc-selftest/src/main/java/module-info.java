/**
 * tornado-selftest: checks TornadoVM's kernel compiler (TC) on the devices of a machine and reports
 * what TC can use on each ({@link uk.ac.manchester.tornado.tc.selftest.SelfTest}). Open: TornadoVM
 * reads the kernels' bytecode and arguments reflectively.
 */
open module tornado.tc.selftest {
    requires tornado.api;
    requires tornado.drivers.common;

    exports uk.ac.manchester.tornado.tc.selftest;
}
