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
import java.lang.reflect.Modifier;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.runtime.jvmci.reflection.ClassfileParser.FieldInfo;

/**
 * A {@link ResolvedJavaField} whose metadata comes from the classfile
 * {@code field_info} rather than a {@link java.lang.reflect.Field}. Used for
 * JDK-internal fields the reflection API filters out (e.g. {@code System.security}),
 * which appear in bytecode but throw {@code NoSuchFieldException} from
 * {@code getDeclaredField}. Such fields occur on since-folded branches, so their
 * offset is not needed; requesting it fails loudly rather than returning a wrong
 * value.
 */
final class ClassfileResolvedJavaField implements ResolvedJavaField {

    private final ReflectionUniverse universe;
    private final Class<?> holder;
    private final FieldInfo info;
    private final ClassLoader loader;

    ClassfileResolvedJavaField(ReflectionUniverse universe, Class<?> holder, FieldInfo info, ClassLoader loader) {
        this.universe = universe;
        this.holder = holder;
        this.info = info;
        this.loader = loader;
    }

    @Override
    public String getName() {
        return info.name();
    }

    @Override
    public JavaType getType() {
        try {
            return universe.lookupType(ReflectionUniverse.classForDescriptor(info.descriptor(), loader));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to resolve type for " + holder.getName() + "." + info.name(), e);
        }
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return universe.lookupType(holder);
    }

    @Override
    public int getModifiers() {
        return info.accessFlags();
    }

    @Override
    public int getOffset() {
        // A reflection-filtered field has no reachable Unsafe offset; it only
        // occurs on folded branches, so the offset must never actually be read.
        throw new UnsupportedOperationException("Offset unavailable for reflection-filtered field " + holder.getName() + "." + info.name());
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public boolean isSynthetic() {
        return (info.accessFlags() & 0x1000) != 0;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    @Override
    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    boolean isStaticField() {
        return Modifier.isStatic(info.accessFlags());
    }
}
