package tornado.drivers.opencl;

import java.lang.reflect.Method;

import com.oracle.graal.api.meta.ResolvedJavaMethod;

import tornado.common.DeviceMapping;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OpenCLInstalledCode;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompiler;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.drivers.opencl.OCLDriver;
import tornado.meta.Meta;
import tornado.runtime.TornadoRuntime;

public class OCLJIT {

	public static void main(String[] args) {
		String className = args[0];
		String methodName = args[1];
		
		try {
		Class<?> declaringClass = Class.forName(className);
		
		Class<?>[] parameterTypes = null;
		if(args.length > 2){
			parameterTypes = new Class<?>[args.length - 2];
			for(int i=2;i<args.length;i++)
				if(args[i].equals("int"))
					parameterTypes[i-2] = int.class;
				else if(args[i].equals("float"))
					parameterTypes[i-2] = float.class;
				else 
					parameterTypes[i-2] = Class.forName(args[i]);
		}
		
		Method method = declaringClass.getDeclaredMethod(methodName, parameterTypes);
		
		ResolvedJavaMethod resolvedMethod = TornadoRuntime.runtime.resolveMethod(method);
		
		System.out.printf("method: name=%s, signature=%s\n",resolvedMethod.getName(),resolvedMethod.getSignature());
		
		final OCLBackend backend = ((OCLDriver) TornadoRuntime.runtime.getDriver(0)).getDefaultBackend();

		
		Meta meta = new Meta();
		meta.addProvider(DeviceMapping.class, new OCLDeviceMapping(0,0));
		
		OpenCLInstalledCode code = OCLCompiler.compileCodeForDevice(resolvedMethod, new Object[] {}, meta, (OCLProviders) backend.getProviders(), backend);
		
		System.out.printf("Installed Code:\n");
		for(byte b : code.getCode())
			System.out.printf("%c",b);
		
		} catch( NoSuchMethodException | SecurityException | ClassNotFoundException e){
			e.printStackTrace();
		}

	}

}
