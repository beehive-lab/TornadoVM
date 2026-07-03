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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;

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

/**
 * TornadoVM-owned {@link ConstantReflectionProvider}. It delegates to a backing
 * provider (the HotSpot one today), but reads constant field values for the
 * reflection-backed metadata layer via {@link java.lang.reflect.Field}, so
 * constant folding of (e.g.) {@code static final} fields works without routing
 * a {@link ReflectionResolvedJavaField} into a HotSpot-only implementation that
 * would fail with a {@code ClassCastException}. This is the JDK-neutral counter
 * part to {@code HotSpotConstantReflectionProvider} used with
 * {@code -Dtornado.jvmci.reflection=true}.
 */
public class TornadoConstantReflectionProvider implements ConstantReflectionProvider {

    private final ConstantReflectionProvider backing;
    private final MetaAccessProvider hostMetaAccess;
    private final SnippetReflectionProvider snippetReflection;

    /**
     * @param backing           the host (HotSpot) constant reflection provider
     * @param hostMetaAccess    the host (HotSpot) meta-access, used to bridge the
     *                          remaining object constant reads (instance fields,
     *                          {@code asJavaClass}/{@code asObjectHub}) still
     *                          pending a full JDK-neutral object-constant story.
     * @param snippetReflection materialises JDK-neutral object constants
     *                          ({@code TornadoObjectConstant}) for static object
     *                          field folds (e.g. {@code Integer.TYPE}).
     */
    public TornadoConstantReflectionProvider(ConstantReflectionProvider backing, MetaAccessProvider hostMetaAccess, SnippetReflectionProvider snippetReflection) {
        this.backing = backing;
        this.hostMetaAccess = hostMetaAccess;
        this.snippetReflection = snippetReflection;
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        if (field instanceof ReflectionResolvedJavaField reflectionField) {
            return readReflectionFieldValue(reflectionField, receiver);
        }
        return backing.readFieldValue(field, receiver);
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
        ResolvedJavaField hostField = hostMetaAccess.lookupJavaField(javaField);
        return backing.readFieldValue(hostField, receiver);
    }

    // ---- everything else delegates to the backing provider ----

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return backing.constantEquals(x, y);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        return backing.readArrayLength(array);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        return backing.readArrayElement(array, index);
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        return backing.boxPrimitive(source);
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        return backing.unboxPrimitive(source);
    }

    @Override
    public JavaConstant forString(String value) {
        return backing.forString(value);
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        return backing.asJavaType(constant);
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        // Bridged to the host provider: a native TornadoObjectConstant(Class) here
        // breaks downstream constant-propagation in the OpenCL intrinsics phase
        // (static-field-base / array sizing), so keep the VM Class constant until
        // that phase is taught to consume Tornado object constants.
        return backing.asJavaClass(bridgeType(type));
    }

    @Override
    public Constant asObjectHub(ResolvedJavaType type) {
        // The object hub (klass pointer) is genuinely VM specific; still bridged
        // to the host provider until a Tornado hub-constant is introduced.
        return backing.asObjectHub(bridgeType(type));
    }

    /** Resolve a reflection type to the host type before the host provider materialises a Class/hub constant. */
    private ResolvedJavaType bridgeType(ResolvedJavaType type) {
        if (type instanceof ReflectionResolvedJavaType reflectionType) {
            return hostMetaAccess.lookupJavaType(reflectionType.getMirror());
        }
        return type;
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return backing.getMemoryAccessProvider();
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return backing.getMethodHandleAccess();
    }
}
