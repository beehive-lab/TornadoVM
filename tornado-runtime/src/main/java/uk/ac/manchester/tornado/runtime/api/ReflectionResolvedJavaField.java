package uk.ac.manchester.tornado.runtime.api;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Reflection-based implementation of ResolvedJavaField for JDK 21.
 * Provides access to field metadata using Java Reflection.
 */
public class ReflectionResolvedJavaField implements ResolvedJavaField {
    private final Field field;
    private final ReflectionMetaAccessProvider metaAccess;

    public ReflectionResolvedJavaField(Field field, ReflectionMetaAccessProvider metaAccess) {
        this.field = field;
        this.metaAccess = metaAccess;
    }

    public Field getField() {
        return field;
    }

    // ========== REAL IMPLEMENTATIONS - JavaField interface ==========

    @Override
    public String getName() {
        return field.getName();
    }

    @Override
    public JavaType getType() {
        return metaAccess.lookupJavaType(field.getType());
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return metaAccess.lookupJavaType(field.getDeclaringClass());
    }

    // ========== REAL IMPLEMENTATIONS - ModifiersProvider ==========

    @Override
    public int getModifiers() {
        return field.getModifiers();
    }

    // ========== REAL IMPLEMENTATIONS - ResolvedJavaField ==========

    @Override
    public int getOffset() {
        // STUB: Cannot get field offset without VM support (Unsafe)
        // Return -1 to indicate offset is not available
        return -1;
    }

    @Override
    public boolean isInternal() {
        // STUB: Assume no fields are internal VM fields
        return false;
    }

    @Override
    public boolean isSynthetic() {
        return field.isSynthetic();
    }

    // ========== STUBS - Constant value ==========

    @Override
    public JavaConstant getConstantValue() {
        // STUB: Could implement for static final fields, but not needed for TornadoVM
        return null;
    }

    // ========== REAL IMPLEMENTATIONS - Annotations ==========

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return field.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return field.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return field.getDeclaredAnnotations();
    }

    @Override
    public String toString() {
        return "ReflectionResolvedJavaField<" + field.getDeclaringClass().getName() + "." + field.getName() + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ReflectionResolvedJavaField) {
            return field.equals(((ReflectionResolvedJavaField) obj).field);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return field.hashCode();
    }
}
