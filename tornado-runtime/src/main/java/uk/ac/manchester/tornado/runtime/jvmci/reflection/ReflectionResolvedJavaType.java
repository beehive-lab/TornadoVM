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
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Reflection + {@code Unsafe}-backed {@link ResolvedJavaType}. Type structure
 * (name, kind, hierarchy, fields, declared methods) is derived from
 * {@link Class} and {@code Unsafe} field offsets rather than HotSpot JVMCI.
 * Whole-program/speculative queries (leaf-concrete-subtype, unique-concrete
 * method, assumptions) are marked {@code REFLECTION-TODO}: TornadoVM compiles a
 * closed kernel world and does not speculate, so most resolve to conservative
 * answers once reached during bring-up.
 */
public final class ReflectionResolvedJavaType implements ResolvedJavaType {

    private final ReflectionUniverse universe;
    private final Class<?> clazz;

    ReflectionResolvedJavaType(ReflectionUniverse universe, Class<?> clazz) {
        this.universe = universe;
        this.clazz = clazz;
    }

    /** The underlying {@link Class}, used to bridge to the host provider where a VM-specific object is required. */
    public Class<?> getMirror() {
        return clazz;
    }

    // ---- identity / naming ----

    @Override
    public String getName() {
        return ReflectionUniverse.descriptor(clazz);
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.fromJavaClass(clazz);
    }

    @Override
    public int getModifiers() {
        return clazz.getModifiers();
    }

    // ---- category predicates ----

    @Override
    public boolean isArray() {
        return clazz.isArray();
    }

    @Override
    public boolean isPrimitive() {
        return clazz.isPrimitive();
    }

    @Override
    public boolean isInterface() {
        return clazz.isInterface();
    }

    @Override
    public boolean isEnum() {
        return clazz.isEnum();
    }

    @Override
    public boolean isInstanceClass() {
        return !clazz.isPrimitive() && !clazz.isArray() && !clazz.isInterface();
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isLinked() {
        return true;
    }

    @Override
    public void link() {
        // already linked: the Class is loaded and linked in this VM (the JVMCI
        // default throws "link is unsupported")
    }

    @Override
    public void initialize() {
        // already initialized: the Class is loaded in this VM
    }

    @Override
    public boolean isLocal() {
        return clazz.isLocalClass();
    }

    @Override
    public boolean isMember() {
        return clazz.isMemberClass();
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public boolean isCloneableWithAllocation() {
        return false;
    }

    // ---- hierarchy ----

    @Override
    public ResolvedJavaType getSuperclass() {
        return universe.lookupType(clazz.getSuperclass());
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        Class<?>[] ifaces = clazz.getInterfaces();
        ResolvedJavaType[] out = new ResolvedJavaType[ifaces.length];
        for (int i = 0; i < ifaces.length; i++) {
            out[i] = universe.lookupType(ifaces[i]);
        }
        return out;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return universe.lookupType(clazz.getComponentType());
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        return universe.lookupType(Array.newInstance(clazz, 0).getClass());
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        return universe.lookupType(clazz.getEnclosingClass());
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        if (other instanceof ReflectionResolvedJavaType o) {
            return clazz.isAssignableFrom(o.clazz);
        }
        // The other operand may be a non-reflection type (e.g. a HotSpot type
        // flowing in from a snippet/stamp): resolve it by descriptor.
        try {
            return clazz.isAssignableFrom(ReflectionUniverse.classForDescriptor(other.getName(), clazz.getClassLoader()));
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ---- members ----

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        List<ReflectionResolvedJavaField> out = new ArrayList<>();
        for (Class<?> c = clazz; c != null; c = includeSuperclasses ? c.getSuperclass() : null) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    out.add(new ReflectionResolvedJavaField(universe, f));
                }
            }
        }
        out.sort(Comparator.comparingInt(ReflectionResolvedJavaField::getOffset));
        return out.toArray(new ResolvedJavaField[0]);
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        List<ReflectionResolvedJavaField> out = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                out.add(new ReflectionResolvedJavaField(universe, f));
            }
        }
        return out.toArray(new ResolvedJavaField[0]);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        Method[] ms = clazz.getDeclaredMethods();
        ResolvedJavaMethod[] out = new ResolvedJavaMethod[ms.length];
        for (int i = 0; i < ms.length; i++) {
            // via the universe cache so a given method is one canonical instance (plugin matching)
            out[i] = universe.lookupMethod(ms[i]);
        }
        return out;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        // already linked; the JVMCI default throws UnsupportedOperationException
        return getDeclaredMethods();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        Constructor<?>[] cs = clazz.getDeclaredConstructors();
        ResolvedJavaMethod[] out = new ResolvedJavaMethod[cs.length];
        for (int i = 0; i < cs.length; i++) {
            out[i] = universe.lookupMethod(cs[i]);
        }
        return out;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink) {
        // already linked; the JVMCI default throws UnsupportedOperationException
        return getDeclaredConstructors();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return clazz.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return clazz.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return clazz.getDeclaredAnnotations();
    }

    // ---- REFLECTION-TODO: resolved/speculative queries reached later in bring-up ----

    @Override
    public String getSourceFileName() {
        // Debug metadata only: the source file is named after the top-level
        // enclosing class.
        Class<?> top = clazz;
        while (top.getEnclosingClass() != null) {
            top = top.getEnclosingClass();
        }
        return top.getSimpleName() + ".java";
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        throw ReflectionUniverse.todo("ResolvedJavaType.isInstance");
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        // Conservative (open world): no unique interface implementor assumed.
        return null;
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        if (clazz.isPrimitive()) {
            return null;
        }
        if (otherType instanceof ReflectionResolvedJavaType other && !other.clazz.isPrimitive()) {
            java.util.Set<Class<?>> ancestors = new java.util.HashSet<>();
            for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
                ancestors.add(c);
            }
            for (Class<?> c = other.clazz; c != null; c = c.getSuperclass()) {
                if (ancestors.contains(c)) {
                    return universe.lookupType(c);
                }
            }
        }
        // The ultimate common ancestor of any two reference types is Object
        // (reached e.g. when either side is an interface or array).
        return universe.lookupType(Object.class);
    }

    @Override
    public AssumptionResult<Boolean> hasFinalizableSubclass() {
        // Conservative: assume no finalizable subclass (kernels do not finalize).
        return new AssumptionResult<>(Boolean.FALSE);
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        // Conservative: no assumption-based devirtualization (TornadoVM compiles
        // a closed kernel world and does not speculate). null == unknown.
        return null;
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        // Conservative: no unique-concrete-method assumption. null == unknown.
        return null;
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        // Closed kernel world: kernel call targets are static/final/private or
        // resolve to the declared method itself; no virtual-override search.
        return method;
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        for (ResolvedJavaField field : getInstanceFields(true)) {
            if (field.getOffset() == offset) {
                return field;
            }
        }
        return null;
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        // The type is already initialized in the host VM and there is no class
        // initialization on the accelerator, so report no <clinit> (a full
        // ASM-backed <clinit> method can be provided later if a pass needs it).
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ReflectionResolvedJavaType other && clazz.equals(other.clazz);
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    @Override
    public String toString() {
        return "ReflectionType<" + clazz.getName() + ">";
    }
}
