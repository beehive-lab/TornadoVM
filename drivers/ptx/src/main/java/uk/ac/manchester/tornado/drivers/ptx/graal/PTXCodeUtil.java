package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;

import java.lang.reflect.Array;

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ROUND_NEAREST_EVEN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants.ROUND_NEAREST_EVEN_INTEGER;

public class PTXCodeUtil {


    /**
     * Create a calling convention from a {@link ResolvedJavaMethod}.
     */
    public static CallingConvention getCallingConvention(CodeCacheProvider codeCache, ResolvedJavaMethod method) {
        Signature sig = method.getSignature();
        JavaType retType = sig.getReturnType(method.getDeclaringClass());
        int sigCount = sig.getParameterCount(false);
        JavaType[] argTypes;
        int argIndex = 0;
        if (!method.isStatic()) {
            argTypes = new JavaType[sigCount + 1];
            argTypes[argIndex++] = method.getDeclaringClass();
        } else {
            argTypes = new JavaType[sigCount];
        }
        for (int i = 0; i < sigCount; i++) {
            argTypes[argIndex++] = sig.getParameterType(i, null);
        }
        return getCallingConvention(retType, argTypes, codeCache.getTarget());
    }

    private static CallingConvention getCallingConvention(JavaType returnType, JavaType[] argTypes, TargetDescription target) {
        int variableIndex = 0;

        Variable[] inputParameters = new Variable[argTypes.length];
        for (int i = 0; i < argTypes.length; i++, variableIndex++) {
            inputParameters[i] = new Variable(LIRKind.value(target.arch.getPlatformKind(argTypes[i].getJavaKind())), variableIndex);

        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : returnType.getJavaKind();
        LIRKind lirKind = LIRKind.value(target.arch.getPlatformKind(returnKind));

        Variable returnParameter = new Variable(lirKind, variableIndex);
        variableIndex++;

        return new CallingConvention(0, returnParameter, inputParameters);
    }

    public static String buildKernelName(String methodName, SchedulableTask task) {
        StringBuilder sb = new StringBuilder(methodName);

        for (Object arg : task.getArguments()) {
            // Object is either array or primitive
            sb.append('_');
            Class<?> argClass = arg.getClass();
            if (RuntimeUtilities.isBoxedPrimitiveClass(argClass)) {
                // Only need to append value.
                // If negative value, remove the minus sign in front
                sb.append(arg.toString().replace('.', '_').replaceAll("-", ""));
            } else if (argClass.isArray() && RuntimeUtilities.isPrimitiveArray(argClass)) {
                // Need to append type and length
                sb.append(argClass.getComponentType().getName());
                sb.append(Array.getLength(arg));
            } else {
                sb.append(argClass.getName().replace('.', '_'));

                // Since with objects there is no way to know what will be a
                // constant differentiate using the hashcode of the object
                sb.append('_');
                sb.append(arg.hashCode());
            }
        }

        return sb.toString();
    }

    public static String makeMethodName(ResolvedJavaMethod method) {
        if (method != null) {
            final String declaringClass = method.getDeclaringClass().toJavaName().replace(".", "_");
            return String.format("%s_%s", declaringClass, method.getName());
        } else {
            TornadoInternalError.unimplemented();
            return null;
        }
    }

    public static String getFPURoundingMode(PTXKind lhs, PTXKind rhs) {
        String roundingMode = ROUND_NEAREST_EVEN;

        if (!lhs.isFloating() && rhs.isFloating()) {
            roundingMode = ROUND_NEAREST_EVEN_INTEGER;
        }
        if ((lhs.isF64() && rhs.isF32())) {
            return null;
        }
        return roundingMode;
    }
}
