/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.babylon.hat;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;

import hat.ComputeContext;
import jdk.incubator.code.Value;
import jdk.incubator.code.dialect.core.CoreOp;
import jdk.incubator.code.dialect.java.JavaOp;
import jdk.incubator.code.dialect.java.JavaType;
import jdk.incubator.code.dialect.java.MethodRef;
import optkl.ifacemapper.MappableIface;

/**
 * Buffer tracking for {@link TornadoHatBackend}, injected into a compute method's code model: the
 * backend learns of each host access to a HAT buffer, so that buffers can stay on the device between
 * dispatches. Finer than a whole-buffer hook: a read of a scalar member ({@code grid.width()}) passes
 * the member's name, and only those bytes are copied back when the device copy is newer.
 *
 * <ul>
 * <li>an accessor that returns a value: {@link #preAccess}, with the member and whether it is indexed;</li>
 * <li>a setter: {@code preMutate} and {@code postMutate} around it;</li>
 * <li>any other call (not on the {@link ComputeContext}) that receives buffers: {@code preMutate}
 * and {@code postMutate} around it for each of them.</li>
 * </ul>
 */
public final class ComputeTracking {

    private static final MethodRef PRE_ACCESS = MethodRef.method(ComputeTracking.class, "preAccess", void.class, ComputeContext.class, MappableIface.class, String.class,
            boolean.class);
    private static final MethodRef PRE_MUTATE = MethodRef.method(ComputeTracking.class, "preMutate", void.class, ComputeContext.class, MappableIface.class);
    private static final MethodRef POST_MUTATE = MethodRef.method(ComputeTracking.class, "postMutate", void.class, ComputeContext.class, MappableIface.class);

    private ComputeTracking() {
    }

    static CoreOp.FuncOp inject(MethodHandles.Lookup lookup, CoreOp.FuncOp compute) {
        if (compute.parameters().isEmpty()) {
            return compute;
        }
        var context = compute.parameters().getFirst();
        return compute.transform((builder, op) -> {
            // Not inside lambdas: a dispatch's kernel lambda (() -> Kernels.k(a, b)) must stay as HAT wrote it.
            if (op instanceof JavaOp.InvokeOp invoke && !invoke.operands().isEmpty() && !insideLambda(invoke)) {
                Class<?> owner = owner(lookup, invoke);
                Value cc = builder.context().getValue(context);
                if (owner != null && MappableIface.class.isAssignableFrom(owner) && invoke.invokeKind() != JavaOp.InvokeOp.InvokeKind.STATIC) {
                    Value buffer = builder.context().getValue(invoke.operands().getFirst());
                    if (invoke.resultType().equals(JavaType.VOID)) {
                        builder.add(JavaOp.invoke(PRE_MUTATE, cc, buffer));
                        builder.add(op);
                        builder.add(JavaOp.invoke(POST_MUTATE, cc, buffer));
                    } else {
                        var member = builder.add(CoreOp.constant(JavaType.J_L_STRING, invoke.invokeReference().name()));
                        var indexed = builder.add(CoreOp.constant(JavaType.BOOLEAN, invoke.operands().size() > 1));
                        builder.add(JavaOp.invoke(PRE_ACCESS, cc, buffer, member, indexed));
                        builder.add(op);
                    }
                    return builder;
                }
                if (owner != null && !ComputeContext.class.isAssignableFrom(owner) && !hat.Accelerator.class.isAssignableFrom(owner)) {
                    var buffers = new ArrayList<Value>();
                    for (var operand : invoke.operands()) {
                        if (operand.type() instanceof JavaType j && isMappable(lookup, j)) {
                            buffers.add(builder.context().getValue(operand));
                        }
                    }
                    if (!buffers.isEmpty()) {
                        buffers.forEach(b -> builder.add(JavaOp.invoke(PRE_MUTATE, cc, b)));
                        builder.add(op);
                        buffers.forEach(b -> builder.add(JavaOp.invoke(POST_MUTATE, cc, b)));
                        return builder;
                    }
                }
            }
            builder.add(op);
            return builder;
        });
    }

    private static boolean insideLambda(jdk.incubator.code.Op op) {
        for (var a = op.ancestorOp(); a != null; a = a.ancestorOp()) {
            if (a instanceof JavaOp.LambdaOp) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> owner(MethodHandles.Lookup lookup, JavaOp.InvokeOp invoke) {
        try {
            return invoke.invokeReference().resolveToMethod(lookup).getDeclaringClass();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isMappable(MethodHandles.Lookup lookup, JavaType type) {
        try {
            return !type.toNominalDescriptor().isPrimitive() && type.erasure().toNominalDescriptor().resolveConstantDesc(lookup) instanceof Class<?> c
                    && MappableIface.class.isAssignableFrom(c);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Before a host read of {@code member} of {@code buffer} ({@code indexed}: an array element). */
    public static void preAccess(ComputeContext cc, MappableIface buffer, String member, boolean indexed) {
        if (cc.accelerator().backend instanceof TornadoHatBackend backend) {
            backend.preAccessMember(buffer, member, indexed);
        } else {
            cc.preAccess(buffer);
        }
    }

    public static void preMutate(ComputeContext cc, MappableIface buffer) {
        cc.preMutate(buffer);
    }

    public static void postMutate(ComputeContext cc, MappableIface buffer) {
        cc.postMutate(buffer);
    }

    /** The byte range of a scalar member of a buffer's layout, or null (an array, a struct, unknown). */
    static long[] scalarMember(MappableIface buffer, String member) {
        if (!(buffer instanceof optkl.ifacemapper.Buffer b && MappableIface.getLayout(b) instanceof GroupLayout layout)) {
            return null;
        }
        try {
            var path = MemoryLayout.PathElement.groupElement(member);
            if (layout.select(path) instanceof ValueLayout value) {
                return new long[] { layout.byteOffset(path), value.byteSize() };
            }
        } catch (IllegalArgumentException e) {
            // not a member of this layout
        }
        return null;
    }
}
