package tornado.drivers.opencl.graal;

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.JavaType;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.api.meta.Signature;
import com.oracle.graal.lir.Variable;

public class OpenCLCodeUtil {
	/**
     * Create a calling convention from a {@link ResolvedJavaMethod}.
     */
    public static CallingConvention getCallingConvention(CodeCacheProvider codeCache, CallingConvention.Type type, ResolvedJavaMethod method, boolean stackOnly) {
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

        return getCallingConvention(type, retType, argTypes, codeCache.getTarget(), stackOnly);
    }

	private static CallingConvention getCallingConvention(Type type,
			JavaType returnType, JavaType[] argTypes, TargetDescription target,
			boolean stackOnly) {
		
		int variableIndex = 0;
		
		Variable[] inputParameters = new Variable[argTypes.length];
		for(int i=0;i<argTypes.length;i++,variableIndex++){
			inputParameters[i] = new Variable(target.getLIRKind(argTypes[i].getKind()), variableIndex);
			//Tornado.info("arg[%d] : %s",i,inputParameters[i].getLIRKind().getPlatformKind().toString());
			//Tornado.info("type[%d]: %s",i,inputParameters[i].getKind().getDeclaringClass().getName());
		}
		
		Kind returnKind = returnType == null ? Kind.Void : returnType.getKind();
		
		
		Variable returnParameter = new Variable(target.getLIRKind(returnKind),variableIndex);
		variableIndex++;
	
		return new CallingConvention(0, returnParameter, inputParameters);
	}
    
    
}
