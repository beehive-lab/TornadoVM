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

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;

import tornado.graal.compiler.api.replacements.SnippetReflectionProvider;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.runtime.jvmci.reflection.ReflectionResolvedJavaField;
import uk.ac.manchester.tornado.runtime.jvmci.reflection.ReflectionResolvedJavaType;
import uk.ac.manchester.tornado.runtime.jvmci.reflection.ReflectionUniverse;

/**
 * TornadoVM-owned {@link ConstantReflectionProvider}. It reads constant field values via
 * {@link java.lang.reflect.Field}, so constant folding of (e.g.) {@code static final} fields
 * works for the reflection-backed metadata layer without a HotSpot-only implementation. This is
 * the JDK-neutral replacement for {@code HotSpotConstantReflectionProvider}; queries that are
 * genuinely VM specific and not needed for GPU compilation throw {@link UnsupportedOperationException}.
 */
public class TornadoConstantReflectionProvider implements ConstantReflectionProvider {

    private final SnippetReflectionProvider snippetReflection;

    /**
     * @param snippetReflection materialises JDK-neutral object constants
     *                          ({@code TornadoObjectConstant}) for static object
     *                          field folds (e.g. {@code Integer.TYPE}).
     */
    public TornadoConstantReflectionProvider(SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
    }

    /**
     * Access the host constant-reflection provider, or fail clearly on a JVMCI-absent JDK
     * (JDK 27+) where there is no HotSpot backing provider. Reads still routed here have not
     * yet been ported to the JDK-neutral (reflection) implementation.
     */
    private static ConstantReflectionProvider backing() {
        String caller = Thread.currentThread().getStackTrace()[2].getMethodName();
        throw new UnsupportedOperationException("ConstantReflectionProvider." + caller + "() is not implemented on the reflection constant-reflection provider");
    }

    private static MetaAccessProvider hostMetaAccess() {
        throw new UnsupportedOperationException("host MetaAccessProvider is not available on the reflection constant-reflection provider");
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        if (field instanceof ReflectionResolvedJavaField reflectionField) {
            return readReflectionFieldValue(reflectionField, receiver);
        }
        return backing().readFieldValue(field, receiver);
    }

    private JavaConstant readReflectionFieldValue(ReflectionResolvedJavaField reflectionField, JavaConstant receiver) {
        Field javaField = reflectionField.getJavaField();
        boolean isStatic = Modifier.isStatic(javaField.getModifiers());
        JavaKind kind = JavaKind.fromJavaClass(javaField.getType());

        // JDK-neutral: static fields read purely via reflection. Primitives box
        // to a PrimitiveConstant; object references become a TornadoObjectConstant.
        if (isStatic) {
            try {
                javaField.setAccessible(true);
                Object value = javaField.get(null);
                if (kind == JavaKind.Object) {
                    return snippetReflection.forObject(value);
                }
                return JavaConstant.forBoxedPrimitive(value);
            } catch (ReflectiveOperationException | InaccessibleObjectException e) {
                return null;
            }
        }

        // Instance fields still need the receiver object; bridge to the host
        // provider by resolving the reflective field to a host ResolvedJavaField.
        ResolvedJavaField hostField = hostMetaAccess().lookupJavaField(javaField);
        return backing().readFieldValue(hostField, receiver);
    }

    // ---- everything else delegates to the backing provider ----

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return backing().constantEquals(x, y);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        return backing().readArrayLength(array);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        return backing().readArrayElement(array, index);
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        return backing().boxPrimitive(source);
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        return backing().unboxPrimitive(source);
    }

    @Override
    public JavaConstant forString(String value) {
        return backing().forString(value);
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        // JVMCI-absent path: inverse of asJavaClass - recover the ResolvedJavaType from a Class
        // object constant. Used by the same local/private-array sizing phase.
        if (constant instanceof JavaConstant javaConstant) {
            Object object = snippetReflection.asObject(Object.class, javaConstant);
            if (object instanceof Class<?> clazz) {
                return reflectionUniverse().lookupType(clazz);
            }
        }
        return backing().asJavaType(constant);
    }

    // Lazily created only on the JVMCI-absent path (asJavaType); avoids the Unsafe init on JDK <=26.
    private ReflectionUniverse reflectionUniverse;

    private ReflectionUniverse reflectionUniverse() {
        if (reflectionUniverse == null) {
            reflectionUniverse = new ReflectionUniverse();
        }
        return reflectionUniverse;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        // JVMCI-absent path: there is no host provider to bridge to, so materialise the
        // java.lang.Class of the reflection type directly as an object constant. Used by the
        // local/private-array sizing phase to recover the element type.
        if (type instanceof ReflectionResolvedJavaType reflectionType) {
            return (JavaConstant) snippetReflection.forObject(reflectionType.getMirror());
        }
        return backing().asJavaClass(bridgeType(type));
    }

    @Override
    public Constant asObjectHub(ResolvedJavaType type) {
        // The object hub (klass pointer) is genuinely VM specific; still bridged
        // to the host provider until a Tornado hub-constant is introduced.
        return backing().asObjectHub(bridgeType(type));
    }

    /** Resolve a reflection type to the host type before the host provider materialises a Class/hub constant. */
    private ResolvedJavaType bridgeType(ResolvedJavaType type) {
        if (type instanceof ReflectionResolvedJavaType reflectionType) {
            return hostMetaAccess().lookupJavaType(reflectionType.getMirror());
        }
        return type;
    }

    // Lazily created only on the JVMCI-absent path.
    private MemoryAccessProvider memoryAccessProvider;

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        if (memoryAccessProvider == null) {
            memoryAccessProvider = new ReflectionMemoryAccessProvider(snippetReflection);
        }
        return memoryAccessProvider;
    }

    /**
     * JDK-neutral {@link MemoryAccessProvider} for the JVMCI-absent (reflection) path. Constant-folds
     * reads from an object constant at a JVMCI field displacement using {@code sun.misc.Unsafe}, matching
     * {@code HotSpotMemoryAccessProviderImpl} semantics (the displacement IS an {@code Unsafe} field offset).
     * A non-object base (raw address / primitive) is not foldable here and returns {@code null}, which Graal
     * treats as "leave the read in place" - safe, never a wrong fold.
     */
    private static final class ReflectionMemoryAccessProvider implements MemoryAccessProvider {

        private static final sun.misc.Unsafe UNSAFE = initUnsafe();

        private final SnippetReflectionProvider snippetReflection;

        ReflectionMemoryAccessProvider(SnippetReflectionProvider snippetReflection) {
            this.snippetReflection = snippetReflection;
        }

        private static sun.misc.Unsafe initUnsafe() {
            try {
                Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return (sun.misc.Unsafe) f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new InternalError("Unable to obtain sun.misc.Unsafe for reflection MemoryAccessProvider", e);
            }
        }

        private Object baseObject(Constant base) {
            if (base instanceof TornadoObjectConstant tornadoObjectConstant) {
                return tornadoObjectConstant.getObject();
            }
            if (base instanceof JavaConstant javaConstant && javaConstant.getJavaKind() == JavaKind.Object) {
                return snippetReflection.asObject(Object.class, javaConstant);
            }
            return null;
        }

        @Override
        public JavaConstant readPrimitiveConstant(JavaKind kind, Constant base, long displacement, int bits) throws IllegalArgumentException {
            Object object = baseObject(base);
            if (object == null) {
                return null;
            }
            return switch (kind) {
                case Boolean -> JavaConstant.forBoolean(UNSAFE.getBoolean(object, displacement));
                case Byte -> JavaConstant.forByte(UNSAFE.getByte(object, displacement));
                case Short -> JavaConstant.forShort(UNSAFE.getShort(object, displacement));
                case Char -> JavaConstant.forChar(UNSAFE.getChar(object, displacement));
                case Int -> JavaConstant.forInt(UNSAFE.getInt(object, displacement));
                case Long -> JavaConstant.forLong(UNSAFE.getLong(object, displacement));
                case Float -> JavaConstant.forFloat(UNSAFE.getFloat(object, displacement));
                case Double -> JavaConstant.forDouble(UNSAFE.getDouble(object, displacement));
                default -> null;
            };
        }

        @Override
        public JavaConstant readObjectConstant(Constant base, long displacement) {
            Object object = baseObject(base);
            if (object == null) {
                return null;
            }
            Object value = UNSAFE.getObject(object, displacement);
            return value == null ? JavaConstant.NULL_POINTER : snippetReflection.forObject(value);
        }
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return backing().getMethodHandleAccess();
    }
}
