/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.jvmci.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Reflection + {@code Unsafe}-backed {@link ResolvedJavaField}. Field name,
 * type, modifiers and (crucially) memory offset are sourced from
 * {@link java.lang.reflect.Field} + {@code Unsafe} rather than HotSpot JVMCI.
 */
public final class ReflectionResolvedJavaField implements ResolvedJavaField {

    private final ReflectionUniverse universe;
    private final Field field;

    ReflectionResolvedJavaField(ReflectionUniverse universe, Field field) {
        this.universe = universe;
        this.field = field;
    }

    /** The underlying reflective field, used by the reflection ConstantReflectionProvider to read constant values. */
    public Field getJavaField() {
        return field;
    }

    @Override
    public String getName() {
        return field.getName();
    }

    @Override
    public JavaType getType() {
        return universe.lookupType(field.getType());
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return universe.lookupType(field.getDeclaringClass());
    }

    @Override
    public int getModifiers() {
        return field.getModifiers();
    }

    @Override
    public int getOffset() {
        return (int) ReflectionUniverse.fieldOffset(field);
    }

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public boolean isSynthetic() {
        return field.isSynthetic();
    }

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
    public boolean equals(Object obj) {
        return obj instanceof ReflectionResolvedJavaField other && field.equals(other.field);
    }

    @Override
    public int hashCode() {
        return field.hashCode();
    }

    @Override
    public String toString() {
        return "ReflectionField<" + field + ">";
    }
}
