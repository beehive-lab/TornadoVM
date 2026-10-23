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
package uk.ac.manchester.tornado.tc.frontend;

import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.ALOAD;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.ALOAD_3;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.ILOAD;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.ILOAD_0;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.INVOKESTATIC;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.IRETURN;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.LDC;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.LDC_W;
import static uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode.RETURN;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * Support for kernels written in Kotlin. It only applies to classes compiled by kotlinc (kotlinc
 * annotates every class it emits with {@code kotlin.Metadata}), and only while
 * {@code -Dtornado.kotlin.support} is not {@code false}: Java classes always take the existing paths.
 *
 * <ul>
 * <li>Calls to the null-check helpers of {@code kotlin.jvm.internal.Intrinsics}, which kotlinc inserts
 * into public functions and before unboxing a platform-typed {@code Integer}, are dropped: kernel
 * arguments and the {@code KernelContext} indices are never null.</li>
 * <li>{@code Number.intValue()} (and the other {@code xxxValue()} methods) on a value that already has
 * that type, which kotlinc emits to unbox a {@code KernelContext} index kept boxed in a local, is the
 * value itself, as {@code Integer.intValue()} is.</li>
 * <li>{@code ParallelLoops.parallelIndex(i)}, which the Kotlin API's {@code parallelFor} places on its
 * loop index (kotlinc keeps no {@code @Parallel} on locals), marks the loop as parallel.</li>
 * <li>A function reference passed as a task ({@code task("t0", ::vectorAdd, ...)}) is compiled to a
 * static method that only forwards its arguments to the function: {@link #forwardingTarget} names the
 * function, which the runtime compiles in its place.</li>
 * </ul>
 */
public final class Kotlin {

    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("tornado.kotlin.support", "true"));

    private static final String METADATA = "Lkotlin/Metadata;";

    private static final String INTRINSICS = "kotlin/jvm/internal/Intrinsics";

    private static final String PARALLEL_LOOPS = "uk/ac/manchester/tornado/kotlin/api/ParallelLoops";

    private static final Map<String, Type> NUMBER_UNBOX = Map.of("intValue()I", Type.I32, "longValue()J", Type.I64, "floatValue()F", Type.F32, "doubleValue()D", Type.F64);

    private Kotlin() {
    }

    /** Whether Kotlin support is enabled and {@code classFile} was compiled by kotlinc. */
    static boolean isKotlin(ClassFile classFile) {
        return ENABLED && classFile.annotations().contains(METADATA);
    }

    /** A void null-check helper of {@code kotlin.jvm.internal.Intrinsics}: the call is dropped. */
    static boolean isNullCheck(MemberRef ref) {
        if (!ref.owner().equals(INTRINSICS) || !ref.descriptor().endsWith(")V")) {
            return false;
        }
        var name = ref.name();
        return name.startsWith("checkNotNull") || name.equals("checkParameterIsNotNull") || name.equals("checkExpressionValueIsNotNull") || name.equals("checkFieldIsNotNull")
                || name.equals("checkReturnedValueIsNotNull");
    }

    /** {@code ParallelLoops.parallelIndex(I)I}: its argument is the index of a parallel loop. */
    static boolean isParallelIndex(MemberRef ref) {
        return ref.owner().equals(PARALLEL_LOOPS) && ref.name().equals("parallelIndex") && ref.descriptor().equals("(I)I");
    }

    /** {@code Number.xxxValue()} on a value of that same type: the call is the value itself. */
    static boolean isNumberUnbox(MemberRef ref, Type receiver) {
        return ref.owner().equals("java/lang/Number") && receiver.equals(NUMBER_UNBOX.get(ref.name() + ref.descriptor()));
    }

    /**
     * The function a Kotlin task method forwards to, if {@code className.methodName} is a static method
     * of a kotlinc class whose only work is to pass its parameters, in order, to one static method with
     * the same parameter and return types (besides kotlinc's null checks). Empty for anything else, and
     * for every Java method.
     */
    public static Optional<Frontend.MethodRef> forwardingTarget(Frontend.BytecodeSource source, String className, String methodName, String descriptor) {
        if (!ENABLED) {
            return Optional.empty();
        }
        var bytes = source.classBytes(className.replace('.', '/'));
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        var classFile = ClassFile.parse(bytes.get());
        var method = classFile.method(methodName, descriptor);
        if (!isKotlin(classFile) || method.isEmpty() || !method.get().isStatic() || method.get().code().isEmpty()) {
            return Optional.empty();
        }
        var parameterSlots = new ArrayList<Integer>();
        int slot = 0;
        for (var parameter : Translator.parameterDescriptors(descriptor)) {
            parameterSlots.add(slot);
            slot += parameter.equals("J") || parameter.equals("D") ? 2 : 1;
        }
        MemberRef target = null;
        var loaded = new ArrayList<Integer>();
        for (var insn : Bytecode.decode(method.get().code().get().bytes())) {
            int op = insn.opcode();
            if (op >= ILOAD && op <= ALOAD) {
                loaded.add(insn.index());
            } else if (op >= ILOAD_0 && op <= ALOAD_3) {
                loaded.add((op - ILOAD_0) % 4);
            } else if (op == LDC || op == LDC_W) {
                continue; // the parameter name a null check reports
            } else if (op == INVOKESTATIC) {
                var ref = classFile.constants().member(insn.index());
                if (isNullCheck(ref)) {
                    loaded.clear();
                    continue;
                }
                if (target != null || !loaded.equals(parameterSlots)) {
                    return Optional.empty();
                }
                target = ref;
                loaded.clear();
            } else if (op < IRETURN || op > RETURN) {
                return Optional.empty();
            }
        }
        if (target == null || !target.descriptor().equals(descriptor)) {
            return Optional.empty();
        }
        return Optional.of(new Frontend.MethodRef(target.owner(), target.name(), target.descriptor()));
    }
}
