package tornado.drivers.opencl;

import java.lang.reflect.Method;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.common.TornadoDevice;
import tornado.drivers.opencl.graal.OCLInstalledCode;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import tornado.drivers.opencl.graal.compiler.OCLCompiler;
import tornado.drivers.opencl.runtime.OCLMeta;
import tornado.meta.Meta;

import static tornado.runtime.TornadoRuntime.getTornadoRuntime;

public class OCLJIT {

    public static void main(String[] args) {
        String className = args[0];
        String methodName = args[1];

        try {
            Class<?> declaringClass = Class.forName(className);

            Class<?>[] parameterTypes = null;
            if (args.length > 2) {
                parameterTypes = new Class<?>[args.length - 2];
                for (int i = 2; i < args.length; i++) {
                    if (args[i].equals("int")) {
                        parameterTypes[i - 2] = int.class;
                    } else if (args[i].equals("float")) {
                        parameterTypes[i - 2] = float.class;
                    } else {
                        parameterTypes[i - 2] = Class.forName(args[i]);
                    }
                }
            }

            Method method = declaringClass.getDeclaredMethod(methodName, parameterTypes);

            ResolvedJavaMethod resolvedMethod = getTornadoRuntime().resolveMethod(method);

            System.out.printf("method: name=%s, signature=%s\n", resolvedMethod.getName(), resolvedMethod.getSignature());

            final OCLBackend backend = getTornadoRuntime().getDriver(OCLDriver.class).getDefaultBackend();

            Meta meta = new OCLMeta(method, false);
            meta.addProvider(TornadoDevice.class, OpenCL.defaultDevice());

            OCLCompilationResult result = OCLCompiler.compileCodeForDevice(resolvedMethod, new Object[]{}, meta, (OCLProviders) backend.getProviders(), backend);
            OCLInstalledCode code = OpenCL.defaultDevice().getDeviceContext().installCode(result);
            System.out.printf("Installed Code:\n");
            for (byte b : code.getCode()) {
                System.out.printf("%c", b);
            }

        } catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

}
