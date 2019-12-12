module tornado.annotation {
    requires transitive jdk.internal.vm.ci;
    requires transitive org.objectweb.asm;
    requires transitive tornado.runtime;

    exports uk.ac.manchester.tornado.annotation;
}