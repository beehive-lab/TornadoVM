package uk.ac.manchester.tornado.runtime.api;

import jdk.vm.ci.meta.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reflection-based implementation of ResolvedJavaMethod for JDK 21.
 * Provides access to method metadata using Java Reflection.
 * 
 * Methods marked as REAL IMPLEMENTATION provide actual functionality.
 * Methods marked as STUB return safe defaults for bytecode/profiling features not used by TornadoVM.
 */
public class ReflectionResolvedJavaMethod implements ResolvedJavaMethod {
    private final Executable executable;
    private final ReflectionMetaAccessProvider metaAccess;
    private final ReflectionSignature signature;

    public ReflectionResolvedJavaMethod(Executable executable, ReflectionMetaAccessProvider metaAccess) {
        this.executable = executable;
        this.metaAccess = metaAccess;
        this.signature = new ReflectionSignature(executable, metaAccess);
    }

    public Executable getExecutable() {
        return executable;
    }

    // ========== REAL IMPLEMENTATIONS - JavaMethod interface ==========

    @Override
    public String getName() {
        return executable.getName();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return metaAccess.lookupJavaType(executable.getDeclaringClass());
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    // ========== REAL IMPLEMENTATIONS - ModifiersProvider ==========

    @Override
    public int getModifiers() {
        return executable.getModifiers();
    }

    // ========== REAL IMPLEMENTATIONS - Method properties ==========

    @Override
    public boolean isSynthetic() {
        return executable.isSynthetic();
    }

    @Override
    public boolean isVarArgs() {
        return executable.isVarArgs();
    }

    @Override
    public boolean isBridge() {
        return executable instanceof Method && ((Method) executable).isBridge();
    }

    @Override
    public boolean isDefault() {
        return executable instanceof Method && ((Method) executable).isDefault();
    }

    @Override
    public boolean isClassInitializer() {
        return executable.getName().equals("<clinit>");
    }

    @Override
    public boolean isConstructor() {
        return executable.getName().equals("<init>");
    }

    @Override
    public boolean canBeStaticallyBound() {
        int modifiers = executable.getModifiers();
        return Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers) || isConstructor();
    }

    // ========== REAL IMPLEMENTATIONS - Annotations ==========

    @Override
    public Annotation[][] getParameterAnnotations() {
        return executable.getParameterAnnotations();
    }

    @Override
    public java.lang.reflect.Type[] getGenericParameterTypes() {
        return executable.getGenericParameterTypes();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return executable.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return executable.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return executable.getDeclaredAnnotations();
    }

    // ========== STUBS - Bytecode methods ==========

    @Override
    public byte[] getCode() {
        // STUB: Cannot get bytecode without ASM or similar library
        return null;
    }

    @Override
    public int getCodeSize() {
        // STUB: Cannot determine without bytecode
        return 0;
    }

    @Override
    public int getMaxLocals() {
        // STUB: Cannot determine without bytecode
        return 0;
    }

    @Override
    public int getMaxStackSize() {
        // STUB: Cannot determine without bytecode
        return 0;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        // STUB: Cannot determine without bytecode
        return new ExceptionHandler[0];
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        // STUB: Create basic stack trace element
        return new StackTraceElement(
            executable.getDeclaringClass().getName(),
            executable.getName(),
            null,
            -1
        );
    }

    @Override
    public ConstantPool getConstantPool() {
        // STUB: Cannot access constant pool without VM support
        return null;
    }

    // ========== STUBS - Profiling methods ==========

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        // STUB: No profiling information available
        return null;
    }

    @Override
    public void reprofile() {
        // STUB: No profiling support
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        // STUB: No speculation log available
        return null;
    }

    // ========== STUBS - Inlining hints ==========

    @Override
    public boolean canBeInlined() {
        // STUB: Assume all methods can be inlined
        return true;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        // STUB: No directive information available
        return false;
    }

    @Override
    public boolean shouldBeInlined() {
        // STUB: Assume methods should be inlined
        return true;
    }

    // ========== STUBS - Debug information ==========

    @Override
    public LineNumberTable getLineNumberTable() {
        // STUB: Cannot get line numbers without class file parsing
        return null;
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        // STUB: Cannot get local variables without class file parsing
        return null;
    }

    @Override
    public Constant getEncoding() {
        // STUB: No encoding available
        return null;
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        // STUB: Cannot determine without VM support
        return false;
    }

    @Override
    public String toString() {
        return "ReflectionResolvedJavaMethod<" + executable.getDeclaringClass().getName() + "." + executable.getName() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ReflectionResolvedJavaMethod) {
            return executable.equals(((ReflectionResolvedJavaMethod) obj).executable);
        }
        return false;
    }

    // ========== STUB - JDK 25 JVMCI addition ==========

    @Override
    public boolean isDeclared() {
        // Returns true if method is declared directly in its declaring class (not inherited)
        // For reflection-based method, we always have the declaring class, so return true
        return true;
    }

    @Override
    public int hashCode() {
        return executable.hashCode();
    }
}
