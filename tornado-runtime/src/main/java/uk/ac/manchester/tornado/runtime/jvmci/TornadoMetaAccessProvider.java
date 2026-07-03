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
package uk.ac.manchester.tornado.runtime.jvmci;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import sun.misc.Unsafe;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import uk.ac.manchester.tornado.runtime.jvmci.reflection.ReflectionUniverse;

/**
 * TornadoVM-owned {@link MetaAccessProvider}. It is the seam through which
 * TornadoVM feeds Java type/metadata information into its Graal-based GPU
 * compilation pipeline, without the rest of the code being coupled to the
 * concrete HotSpot JVMCI implementation ({@code HotSpotMetaAccessProvider}).
 *
 * <p>
 * In this first (delegating) form, every query is forwarded to a backing
 * {@link MetaAccessProvider} obtained from the running VM. This decouples the
 * backend factories from the concrete {@code HotSpot*} types (Phase 3) while
 * leaving behaviour identical. Subsequent phases replace individual delegated
 * queries with a reflection + ASM + {@code Unsafe} implementation so TornadoVM
 * can keep working even if a future JDK removes JVMCI entirely (Phase 4).
 */
public class TornadoMetaAccessProvider implements MetaAccessProvider {

    private static final Unsafe UNSAFE = initUnsafe();

    /**
     * When {@code true}, type/method/field lookups are answered by the
     * reflection/ASM/{@code Unsafe} implementation instead of delegating to the
     * HotSpot JVMCI backing provider. Opt-in bring-up flag; default path stays
     * on the verified delegating seam.
     */
    private static final boolean USE_REFLECTION = Boolean.getBoolean("tornado.jvmci.reflection") || Boolean.getBoolean("tornado.jvmci.reflection.full");

    /**
     * When set, the runtime-level metaAccess (kernel entry-method resolution) is
     * also routed through this reflection provider, not just per-backend
     * compilation. Requires the reflection {@code ConstantPool}.
     */
    public static final boolean USE_REFLECTION_FULL = Boolean.getBoolean("tornado.jvmci.reflection.full");

    private final MetaAccessProvider backing;
    private final ReflectionUniverse reflectionUniverse;

    public TornadoMetaAccessProvider(MetaAccessProvider backing) {
        this.backing = backing;
        this.reflectionUniverse = USE_REFLECTION ? new ReflectionUniverse() : null;
    }

    private static Unsafe initUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new InternalError("Unable to obtain sun.misc.Unsafe for JDK-neutral object layout", e);
        }
    }

    /**
     * Maps a primitive/reference {@link JavaKind} to the corresponding array
     * class, so array layout can be queried from {@link Unsafe} instead of the
     * HotSpot JVMCI implementation.
     */
    private static Class<?> arrayClassFor(JavaKind kind) {
        return switch (kind) {
            case Boolean -> boolean[].class;
            case Byte -> byte[].class;
            case Short -> short[].class;
            case Char -> char[].class;
            case Int -> int[].class;
            case Long -> long[].class;
            case Float -> float[].class;
            case Double -> double[].class;
            case Object -> Object[].class;
            default -> null;
        };
    }

    /**
     * @return the backing provider this seam currently delegates to. Retained so
     *         that, during the incremental migration to a reflection-based
     *         implementation, individual queries can be validated (A/B) against
     *         the HotSpot provider.
     */
    public MetaAccessProvider getBackingProvider() {
        return backing;
    }

    @Override
    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        if (reflectionUniverse != null) {
            return reflectionUniverse.lookupType(clazz);
        }
        return backing.lookupJavaType(clazz);
    }

    @Override
    public ResolvedJavaType[] lookupJavaTypes(Class<?>[] classes) {
        return backing.lookupJavaTypes(classes);
    }

    @Override
    public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        if (reflectionUniverse != null) {
            return reflectionUniverse.lookupMethod(reflectionMethod);
        }
        return backing.lookupJavaMethod(reflectionMethod);
    }

    @Override
    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        if (reflectionUniverse != null) {
            return reflectionUniverse.lookupField(reflectionField);
        }
        return backing.lookupJavaField(reflectionField);
    }

    @Override
    public ResolvedJavaType lookupJavaType(JavaConstant constant) {
        return backing.lookupJavaType(constant);
    }

    @Override
    public long getMemorySize(JavaConstant constant) {
        return backing.getMemorySize(constant);
    }

    @Override
    public Signature parseMethodDescriptor(String methodDescriptor) {
        return backing.parseMethodDescriptor(methodDescriptor);
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId) {
        return backing.encodeDeoptActionAndReason(action, reason, debugId);
    }

    @Override
    public JavaConstant encodeSpeculation(Speculation speculation) {
        return backing.encodeSpeculation(speculation);
    }

    @Override
    public Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
        return backing.decodeSpeculation(constant, speculationLog);
    }

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        return backing.decodeDeoptReason(constant);
    }

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        return backing.decodeDeoptAction(constant);
    }

    @Override
    public int decodeDebugId(JavaConstant constant) {
        return backing.decodeDebugId(constant);
    }

    /**
     * JDK-neutral array base offset sourced from {@link Unsafe} rather than the
     * HotSpot JVMCI implementation. The value is the VM's real array-data offset,
     * identical to what {@code HotSpotMetaAccessProvider} reports.
     */
    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        Class<?> arrayClass = arrayClassFor(elementKind);
        if (arrayClass == null) {
            return backing.getArrayBaseOffset(elementKind);
        }
        return UNSAFE.arrayBaseOffset(arrayClass);
    }

    /**
     * JDK-neutral array index scale sourced from {@link Unsafe} rather than the
     * HotSpot JVMCI implementation.
     */
    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        Class<?> arrayClass = arrayClassFor(elementKind);
        if (arrayClass == null) {
            return backing.getArrayIndexScale(elementKind);
        }
        return UNSAFE.arrayIndexScale(arrayClass);
    }
}
