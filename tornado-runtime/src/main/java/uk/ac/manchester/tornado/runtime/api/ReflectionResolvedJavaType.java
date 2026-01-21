package uk.ac.manchester.tornado.runtime.api;

import jdk.vm.ci.meta.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reflection-based implementation of ResolvedJavaType for JDK 21.
 * This class provides a JVMCI-independent way to access type metadata using Java Reflection.
 * 
 * Methods marked as REAL IMPLEMENTATION use reflection to provide actual functionality.
 * Methods marked as STUB return safe default values for features not used by TornadoVM.
 */
public class ReflectionResolvedJavaType implements ResolvedJavaType {
    private final Class<?> javaClass;
    private final ReflectionMetaAccessProvider metaAccess;

    public ReflectionResolvedJavaType(Class<?> javaClass, ReflectionMetaAccessProvider metaAccess) {
        this.javaClass = javaClass;
        this.metaAccess = metaAccess;
    }

    public Class<?> getJavaClass() {
        return javaClass;
    }

    // ========== REAL IMPLEMENTATIONS - JavaType interface ==========

    @Override
    public String getName() {
        return javaClass.getName();
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.fromJavaClass(javaClass);
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public boolean isArray() {
        return javaClass.isArray();
    }

    @Override
    public ResolvedJavaType getComponentType() {
        if (!javaClass.isArray()) {
            return null;
        }
        return metaAccess.lookupJavaType(javaClass.getComponentType());
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        return metaAccess.lookupJavaType(java.lang.reflect.Array.newInstance(javaClass, 0).getClass());
    }

    // ========== REAL IMPLEMENTATIONS - ResolvedJavaType interface ==========

    @Override
    public boolean isPrimitive() {
        return javaClass.isPrimitive();
    }

    @Override
    public boolean isInterface() {
        return javaClass.isInterface();
    }

    @Override
    public boolean isInstanceClass() {
        return !javaClass.isArray() && !javaClass.isPrimitive() && !javaClass.isInterface();
    }

    @Override
    public boolean isEnum() {
        return javaClass.isEnum();
    }

    @Override
    public boolean isInitialized() {
        // Assume all classes we work with are initialized
        return true;
    }

    @Override
    public void initialize() {
        // Classes are already initialized through normal class loading
    }

    @Override
    public boolean isLinked() {
        // Assume all classes are linked
        return true;
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        if (other instanceof ReflectionResolvedJavaType) {
            return javaClass.isAssignableFrom(((ReflectionResolvedJavaType) other).javaClass);
        }
        return false;
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        if (obj.isNull()) {
            return false;
        }
        // Cannot easily check instanceof with JavaConstant without HotSpot internals
        return false;
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        Class<?> superclass = javaClass.getSuperclass();
        if (superclass == null) {
            return null;
        }
        return metaAccess.lookupJavaType(superclass);
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        Class<?>[] interfaces = javaClass.getInterfaces();
        return Arrays.stream(interfaces)
                .map(metaAccess::lookupJavaType)
                .toArray(ResolvedJavaType[]::new);
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        if (includeSuperclasses) {
            return getAllFields(false);
        } else {
            return getDeclaredFields(false);
        }
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        return getDeclaredFields(true);
    }

    private ResolvedJavaField[] getDeclaredFields(boolean staticOnly) {
        Field[] fields = javaClass.getDeclaredFields();
        return Arrays.stream(fields)
                .filter(f -> staticOnly == Modifier.isStatic(f.getModifiers()))
                .map(metaAccess::lookupJavaField)
                .toArray(ResolvedJavaField[]::new);
    }

    private ResolvedJavaField[] getAllFields(boolean staticOnly) {
        return getAllFieldsStream(javaClass, staticOnly)
                .map(metaAccess::lookupJavaField)
                .toArray(ResolvedJavaField[]::new);
    }

    private Stream<Field> getAllFieldsStream(Class<?> clazz, boolean staticOnly) {
        if (clazz == null) {
            return Stream.empty();
        }
        Stream<Field> fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> staticOnly == Modifier.isStatic(f.getModifiers()));
        return Stream.concat(fields, getAllFieldsStream(clazz.getSuperclass(), staticOnly));
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return getDeclaredConstructors(false);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        return Arrays.stream(javaClass.getDeclaredConstructors())
                .map(metaAccess::lookupJavaMethod)
                .toArray(ResolvedJavaMethod[]::new);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(false);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        return Arrays.stream(javaClass.getDeclaredMethods())
                .map(metaAccess::lookupJavaMethod)
                .toArray(ResolvedJavaMethod[]::new);
    }

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        return List.of();
    }

    @Override
    public int getModifiers() {
        return javaClass.getModifiers();
    }

    // ========== STUBS - Annotation methods ==========

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return javaClass.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return javaClass.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return javaClass.getDeclaredAnnotations();
    }

    // ========== STUBS - Advanced VM features ==========

    @Override
    public boolean hasFinalizer() {
        // STUB: Check if class has finalize() method
        try {
            Method finalize = javaClass.getDeclaredMethod("finalize");
            return finalize.getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Override
    public Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
        // STUB: Cannot determine without VM support
        return new Assumptions.AssumptionResult<>(false);
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        // STUB: Cannot determine without VM support
        return new Assumptions.AssumptionResult<>(this);
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        // STUB: Cannot determine without VM support
        return null;
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        // STUB: Simple implementation
        if (isAssignableFrom(otherType)) {
            return this;
        }
        if (otherType.isAssignableFrom(this)) {
            return otherType;
        }
        return null;
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        // STUB: Just return the method
        return method;
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        // STUB: Cannot determine without VM support
        return new Assumptions.AssumptionResult<>(method);
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        // STUB: Cannot determine field by offset without VM support
        return null;
    }

    @Override
    public String getSourceFileName() {
        // STUB: Cannot determine without class file parsing
        return null;
    }

    @Override
    public boolean isLocal() {
        return javaClass.isLocalClass();
    }

    @Override
    public boolean isMember() {
        return javaClass.isMemberClass();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        Class<?> enclosingClass = javaClass.getEnclosingClass();
        if (enclosingClass == null) {
            return null;
        }
        return metaAccess.lookupJavaType(enclosingClass);
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        // STUB: Cannot easily get <clinit> method
        return null;
    }

    @Override
    public boolean isCloneableWithAllocation() {
        // STUB: Assume cloneable types can be cloned
        return Cloneable.class.isAssignableFrom(javaClass);
    }

    @Override
    public String toString() {
        return "ReflectionResolvedJavaType<" + javaClass.getName() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ReflectionResolvedJavaType) {
            return javaClass.equals(((ReflectionResolvedJavaType) obj).javaClass);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return javaClass.hashCode();
    }
}
