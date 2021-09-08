package uk.ac.manchester.tornado.drivers.spirv.graal;

import java.util.concurrent.atomic.AtomicInteger;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SPIRVUtils {

    private static final AtomicInteger id = new AtomicInteger(0);

    public static String makeMethodName(ResolvedJavaMethod method) {
        if (method != null) {
            final String declaringClass = method.getDeclaringClass().toJavaName().replace(".", "_");
            return String.format("%s_%s", declaringClass, method.getName());
        } else {
            return String.format("unknown_%d", id.incrementAndGet());
        }
    }
}
