package tornado.drivers.opencl.graal;

import com.oracle.graal.api.meta.ResolvedJavaMethod;
import java.util.concurrent.atomic.AtomicInteger;

public final class OCLUtils {
    
    private static final AtomicInteger id = new AtomicInteger(0);

	public static String makeMethodName(ResolvedJavaMethod method) {
            if(method != null){
		final String declaringClass = method.getDeclaringClass().toJavaName().replace(".", "_");
		return String.format("%s_%s", declaringClass,method.getName());
            } else {
                return String.format("unknown_%d",id.incrementAndGet());
            }
	}

}
