package uk.ac.manchester.tornado.runtime.api;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;

/**
 * Reflection-based implementation of Signature for JDK 21.
 * Provides access to method signature information using Java Reflection.
 */
public class ReflectionSignature implements Signature {
    private final Class<?>[] parameterTypes;
    private final Class<?> returnType;
    private final ReflectionMetaAccessProvider metaAccess;

    public ReflectionSignature(Executable executable, ReflectionMetaAccessProvider metaAccess) {
        this.parameterTypes = executable.getParameterTypes();
        this.returnType = (executable instanceof Method) ? ((Method) executable).getReturnType() : void.class;
        this.metaAccess = metaAccess;
    }

    public ReflectionSignature(Class<?>[] parameterTypes, Class<?> returnType, ReflectionMetaAccessProvider metaAccess) {
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
        this.metaAccess = metaAccess;
    }

    // ========== REAL IMPLEMENTATIONS ==========

    @Override
    public int getParameterCount(boolean receiver) {
        return parameterTypes.length;
    }

    @Override
    public JavaType getParameterType(int index, ResolvedJavaType accessingClass) {
        if (index < 0 || index >= parameterTypes.length) {
            return null;
        }
        return metaAccess.lookupJavaType(parameterTypes[index]);
    }

    @Override
    public JavaType getReturnType(ResolvedJavaType accessingClass) {
        return metaAccess.lookupJavaType(returnType);
    }

    @Override
    public JavaKind getParameterKind(int index) {
        if (index < 0 || index >= parameterTypes.length) {
            return JavaKind.Illegal;
        }
        return JavaKind.fromJavaClass(parameterTypes[index]);
    }

    @Override
    public JavaKind getReturnKind() {
        return JavaKind.fromJavaClass(returnType);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(parameterTypes[i].getName());
        }
        sb.append(")");
        sb.append(returnType.getName());
        return sb.toString();
    }
}
