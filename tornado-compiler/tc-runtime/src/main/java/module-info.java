/**
 * The TC pipeline driver: options, stage instrumentation, dumps and JFR events.
 */
module tornado.tc.runtime {
    requires transitive tornado.tc.codegen;
    requires transitive tornado.tc.tornado;
    requires jdk.jfr;

    exports uk.ac.manchester.tornado.tc.runtime;
}
