package tornado.drivers.opencl.graal;

import com.oracle.graal.api.meta.ResolvedJavaMethod;

public final class OCLUtils {

	public static String makeMethodName(ResolvedJavaMethod method) {
		final String declaringClass = method.getDeclaringClass().toJavaName().replace(".", "_");
		return String.format("%s_%s", declaringClass,method.getName());
	}

}
