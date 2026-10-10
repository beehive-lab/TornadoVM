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
package uk.ac.manchester.tornado.tc.frontend;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Opcode.ConvertOp;
import uk.ac.manchester.tornado.tc.ir.Type;

/**
 * How Java API members map to TC operations: the intrinsic-binding registry of the frontend. Members
 * are matched by owner, name and descriptor strings, so the frontend never links against the
 * TornadoVM API. A binding either produces an intrinsic, an array operation, or a value-preserving
 * no-op (unboxing of the {@code Integer} fields of {@code KernelContext}).
 */
final class Bindings {

    static final String KERNEL_CONTEXT = "uk/ac/manchester/tornado/api/KernelContext";
    private static final String ARRAYS = "uk/ac/manchester/tornado/api/types/arrays/";
    private static final String TORNADO_MATH = "uk/ac/manchester/tornado/api/math/TornadoMath";
    private static final String JAVA_MATH = "java/lang/Math";

    /** What a call or field access becomes. */
    sealed interface Binding {
    }

    /** An intrinsic with the given result type (void for effects). */
    record Intrinsic(String name, Type result) implements Binding {
    }

    /** {@code get(int)} on a TornadoVM native array. */
    record ArrayGet() implements Binding {
    }

    /** {@code set(int, value)} on a TornadoVM native array. */
    record ArraySet() implements Binding {
    }

    /** {@code getSize()} ({@code i32}) or {@code getSizeLong()} ({@code i64}) on a TornadoVM native array. */
    record ArraySize(Type type) implements Binding {
    }

    /** A value conversion, such as the raw bit casts of {@code Float.floatToRawIntBits}. */
    record Convert(ConvertOp op, Type result) implements Binding {
    }

    /** A {@code KernelContext.allocate*LocalArray(n)}: shared memory of the element type. */
    record SharedArray(Type element) implements Binding {
    }

    /** The value passes through unchanged ({@code Integer.intValue()} on an already unboxed id). */
    record Identity() implements Binding {
    }

    /**
     * Constructor of a value class: {@code new C; dup; args; invokespecial C.<init>} becomes the value
     * {@code convert(op, arg)} of type {@code result} (after narrowing the int stack value to {@code from}).
     */
    record ValueInit(Type from, ConvertOp op, Type result) implements Binding {
    }

    /** {@code HalfFloat.getHalfFloatValue()}: the raw 16 bits, sign-extended like a Java short. */
    record HalfBits() implements Binding {
    }

    /** {@code HalfFloat.add/sub/mult/div}: computed in single precision, rounded once to half (as Java does). */
    record HalfArith(Opcode.BinaryOp op) implements Binding {
    }

    /** {@code ByteArray.getHalfFloat(byteIndex)} / {@code setHalfFloat}: a typed view of a byte array at a byte index. */
    record ByteView(Type type, boolean store) implements Binding {
    }

    /**
     * A KernelContext atomic on {@code array[index]}: {@code op} is add, cas, exchange, min or max;
     * {@code value} the element type; {@code returnsPrevious} for the read-modify-write forms.
     */
    record Atomic(String op, Type value, boolean returnsPrevious) implements Binding {
    }

    /** Value classes: objects of these classes are their single scalar field. */
    static final String HALF_FLOAT = "uk/ac/manchester/tornado/api/types/HalfFloat";

    private static final Map<String, Binding> FIELDS = new HashMap<>();
    private static final Map<String, Binding> METHODS = new HashMap<>();
    private static final Map<String, Type> NATIVE_ARRAYS = Map.ofEntries(
            Map.entry("FloatArray", Type.F32),
            Map.entry("IntArray", Type.I32),
            Map.entry("DoubleArray", Type.F64),
            Map.entry("LongArray", Type.I64),
            Map.entry("ShortArray", Type.I16),
            Map.entry("ByteArray", Type.I8),
            Map.entry("Int8Array", Type.I8),
            Map.entry("FP8Array", Type.I8),
            Map.entry("BFloat16Array", Type.I16),
            Map.entry("CharArray", Type.I16),
            Map.entry("HalfFloatArray", Type.F16));

    static {
        var dims = new String[] { "x", "y", "z" };
        for (int i = 0; i < 3; i++) {
            var d = dims[i];
            var upper = d.toUpperCase();
            FIELDS.put(KERNEL_CONTEXT + ".globalId" + d, new Intrinsic("tornado.thread_id.global." + d, Type.I32));
            FIELDS.put(KERNEL_CONTEXT + ".localId" + d, new Intrinsic("tornado.thread_id.local." + d, Type.I32));
            FIELDS.put(KERNEL_CONTEXT + ".groupId" + d, new Intrinsic("tornado.group_id." + d, Type.I32));
            FIELDS.put(KERNEL_CONTEXT + ".globalGroupSize" + upper, new Intrinsic("tornado.global_size." + d, Type.I32));
            FIELDS.put(KERNEL_CONTEXT + ".localGroupSize" + upper, new Intrinsic("tornado.group_size.local." + d, Type.I32));
        }
        METHODS.put(KERNEL_CONTEXT + ".localBarrier()V", new Intrinsic("tornado.barrier.local", Type.VOID));
        METHODS.put(KERNEL_CONTEXT + ".globalBarrier()V", new Intrinsic("tornado.barrier.global", Type.VOID));
        METHODS.put(KERNEL_CONTEXT + ".allocateFloatLocalArray(I)[F", new SharedArray(Type.F32));
        METHODS.put(KERNEL_CONTEXT + ".allocateIntLocalArray(I)[I", new SharedArray(Type.I32));
        METHODS.put(KERNEL_CONTEXT + ".allocateDoubleLocalArray(I)[D", new SharedArray(Type.F64));
        METHODS.put(KERNEL_CONTEXT + ".allocateLongLocalArray(I)[J", new SharedArray(Type.I64));
        METHODS.put(KERNEL_CONTEXT + ".allocateByteLocalArray(I)[B", new SharedArray(Type.I8));
        METHODS.put(KERNEL_CONTEXT + ".allocateHalfFloatLocalArray(I)[L" + HALF_FLOAT + ";", new SharedArray(Type.F16));
        METHODS.put("java/lang/Integer.intValue()I", new Identity());
        // Warp (SIMD group) operations: all lanes of the warp take part; not pure (never moved or merged).
        METHODS.put(KERNEL_CONTEXT + ".simdSum(F)F", new Intrinsic("tornado.simd.sum", Type.F32));
        METHODS.put(KERNEL_CONTEXT + ".simdShuffleDown(FI)F", new Intrinsic("tornado.simd.shuffle_down", Type.F32));
        METHODS.put(KERNEL_CONTEXT + ".simdBroadcastFirst(F)F", new Intrinsic("tornado.simd.broadcast_first", Type.F32));
        METHODS.put(KERNEL_CONTEXT + ".simdAny(Z)Z", new Intrinsic("tornado.simd.any", Type.I32));
        METHODS.put(KERNEL_CONTEXT + ".simdAll(Z)Z", new Intrinsic("tornado.simd.all", Type.I32));
        METHODS.put(KERNEL_CONTEXT + ".simdBallot(Z)I", new Intrinsic("tornado.simd.ballot", Type.I32));
        // Atomics on global native arrays and on local int[] arrays.
        METHODS.put(KERNEL_CONTEXT + ".atomicAdd(L" + ARRAYS + "IntArray;II)V", new Atomic("add", Type.I32, false));
        METHODS.put(KERNEL_CONTEXT + ".atomicAdd(L" + ARRAYS + "LongArray;IJ)V", new Atomic("add", Type.I64, false));
        METHODS.put(KERNEL_CONTEXT + ".atomicAdd(L" + ARRAYS + "FloatArray;IF)V", new Atomic("add", Type.F32, false));
        METHODS.put(KERNEL_CONTEXT + ".atomicAdd(L" + ARRAYS + "DoubleArray;ID)V", new Atomic("add", Type.F64, false));
        METHODS.put(KERNEL_CONTEXT + ".atomicAdd([III)V", new Atomic("add", Type.I32, false));
        METHODS.put(KERNEL_CONTEXT + ".atomicCAS([IIII)I", new Atomic("cas", Type.I32, true));
        METHODS.put(KERNEL_CONTEXT + ".atomicExchange([III)I", new Atomic("exchange", Type.I32, true));
        METHODS.put(KERNEL_CONTEXT + ".atomicMin([III)I", new Atomic("min", Type.I32, true));
        METHODS.put(KERNEL_CONTEXT + ".atomicMax([III)I", new Atomic("max", Type.I32, true));
        for (var index : new String[] { "I", "J" }) {
            METHODS.put(ARRAYS + "ByteArray.getHalfFloat(" + index + ")L" + HALF_FLOAT + ";", new ByteView(Type.F16, false));
            METHODS.put(ARRAYS + "ByteArray.setHalfFloat(" + index + "L" + HALF_FLOAT + ";)V", new ByteView(Type.F16, true));
        }
        METHODS.put(HALF_FLOAT + ".<init>(F)V", new ValueInit(Type.F32, ConvertOp.FTRUNC, Type.F16));
        METHODS.put(HALF_FLOAT + ".<init>(S)V", new ValueInit(Type.I16, ConvertOp.BITCAST, Type.F16));
        METHODS.put(HALF_FLOAT + ".getFloat32()F", new Convert(ConvertOp.FEXT, Type.F32));
        METHODS.put(HALF_FLOAT + ".getHalfFloatValue()S", new HalfBits());
        var half = "(L" + HALF_FLOAT + ";L" + HALF_FLOAT + ";)L" + HALF_FLOAT + ";";
        METHODS.put(HALF_FLOAT + ".add" + half, new HalfArith(Opcode.BinaryOp.FADD));
        METHODS.put(HALF_FLOAT + ".sub" + half, new HalfArith(Opcode.BinaryOp.FSUB));
        METHODS.put(HALF_FLOAT + ".mult" + half, new HalfArith(Opcode.BinaryOp.FMUL));
        METHODS.put(HALF_FLOAT + ".div" + half, new HalfArith(Opcode.BinaryOp.FDIV));
        // Raw bit casts are native methods (no bytecode): bitcast keeps every bit, NaN payloads included.
        METHODS.put("java/lang/Float.floatToRawIntBits(F)I", new Convert(ConvertOp.BITCAST, Type.I32));
        METHODS.put("java/lang/Float.intBitsToFloat(I)F", new Convert(ConvertOp.BITCAST, Type.F32));
        METHODS.put("java/lang/Double.doubleToRawLongBits(D)J", new Convert(ConvertOp.BITCAST, Type.I64));
        METHODS.put("java/lang/Double.longBitsToDouble(J)D", new Convert(ConvertOp.BITCAST, Type.F64));
        for (var name : NATIVE_ARRAYS.keySet()) {
            var owner = ARRAYS + name;
            var t = descriptorOf(NATIVE_ARRAYS.get(name), name);
            for (var index : new String[] { "I", "J" }) {
                METHODS.put(owner + ".get(" + index + ")" + t, new ArrayGet());
                METHODS.put(owner + ".set(" + index + t + ")V", new ArraySet());
            }
            METHODS.put(owner + ".getSize()I", new ArraySize(Type.I32));
            METHODS.put(owner + ".getSizeLong()J", new ArraySize(Type.I64));
        }
        // Inverse and hyperbolic functions bind directly too: their JDK bytecode (FdLibm) reads static
        // tables kernels cannot access; the device library is within the same ulp bounds Graal accepts.
        for (var fn : new String[] { "sqrt", "exp", "log", "sin", "cos", "tan", "tanh", "abs", "floor", "ceil",
                "atan", "asin", "acos", "sinh", "cosh", "asinh", "acosh", "atanh", "cbrt", "log10" }) {
            METHODS.put(TORNADO_MATH + "." + fn + "(F)F", new Intrinsic("math." + fn, Type.F32));
            METHODS.put(TORNADO_MATH + "." + fn + "(D)D", new Intrinsic("math." + fn, Type.F64));
            METHODS.put(JAVA_MATH + "." + fn + "(D)D", new Intrinsic("math." + fn, Type.F64));
        }
        METHODS.put(TORNADO_MATH + ".rsqrt(F)F", new Intrinsic("math.rsqrt", Type.F32));
        METHODS.put(JAVA_MATH + ".abs(F)F", new Intrinsic("math.abs", Type.F32));
        METHODS.put(JAVA_MATH + ".abs(I)I", new Intrinsic("math.abs", Type.I32));
        METHODS.put(JAVA_MATH + ".abs(J)J", new Intrinsic("math.abs", Type.I64));
        for (var fn : new String[] { "min", "max" }) {
            METHODS.put(JAVA_MATH + "." + fn + "(II)I", new Intrinsic("math." + fn, Type.I32));
            METHODS.put(JAVA_MATH + "." + fn + "(JJ)J", new Intrinsic("math." + fn, Type.I64));
            METHODS.put(JAVA_MATH + "." + fn + "(FF)F", new Intrinsic("math." + fn, Type.F32));
            METHODS.put(JAVA_MATH + "." + fn + "(DD)D", new Intrinsic("math." + fn, Type.F64));
            METHODS.put(TORNADO_MATH + "." + fn + "(FF)F", new Intrinsic("math." + fn, Type.F32));
            METHODS.put(TORNADO_MATH + "." + fn + "(II)I", new Intrinsic("math." + fn, Type.I32));
        }
        METHODS.put(JAVA_MATH + ".pow(DD)D", new Intrinsic("math.pow", Type.F64));
        METHODS.put(JAVA_MATH + ".atan2(DD)D", new Intrinsic("math.atan2", Type.F64));
        METHODS.put(TORNADO_MATH + ".atan2(DD)D", new Intrinsic("math.atan2", Type.F64));
        METHODS.put(TORNADO_MATH + ".atan2(FF)F", new Intrinsic("math.atan2", Type.F32));
        METHODS.put(TORNADO_MATH + ".pow(FF)F", new Intrinsic("math.pow", Type.F32));
        METHODS.put(JAVA_MATH + ".fma(FFF)F", new Intrinsic("math.fma", Type.F32));
        METHODS.put(JAVA_MATH + ".fma(DDD)D", new Intrinsic("math.fma", Type.F64));
    }

    private Bindings() {
    }

    static Optional<Binding> field(String owner, String name) {
        return Optional.ofNullable(FIELDS.get(owner + "." + name));
    }

    static Optional<Binding> method(String owner, String name, String descriptor) {
        return Optional.ofNullable(METHODS.get(owner + "." + name + descriptor));
    }

    /** The TC type of a field or parameter descriptor ({@code I}, {@code [F}, {@code Lpkg/Cls;}, …). */
    static Type type(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'I', 'Z', 'B', 'C', 'S' -> Type.I32;
            case 'J' -> Type.I64;
            case 'F' -> Type.F32;
            case 'D' -> Type.F64;
            case 'V' -> Type.VOID;
            case '[' -> new Type.ArrayType(elementType(descriptor.substring(1)));
            case 'L' -> {
                var cls = descriptor.substring(1, descriptor.length() - 1);
                if (cls.equals(HALF_FLOAT)) {
                    yield Type.F16;
                }
                if (cls.startsWith(ARRAYS) && NATIVE_ARRAYS.containsKey(cls.substring(ARRAYS.length()))) {
                    yield new Type.ArrayType(NATIVE_ARRAYS.get(cls.substring(ARRAYS.length())));
                }
                yield new Type.ObjectType(cls.substring(cls.lastIndexOf('/') + 1));
            }
            default -> throw new IllegalArgumentException("bad descriptor " + descriptor);
        };
    }

    /** Element type of a Java array: sub-int types keep their width in memory. */
    static Type elementType(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'Z', 'B' -> Type.I8;
            case 'C', 'S' -> Type.I16;
            default -> type(descriptor);
        };
    }

    private static String descriptorOf(Type type, String arrayName) {
        if (arrayName.equals("CharArray")) {
            return "C";
        }
        return switch (type.text()) {
            case "f32" -> "F";
            case "i32" -> "I";
            case "f64" -> "D";
            case "i64" -> "J";
            case "i16" -> "S";
            case "i8" -> "B";
            case "f16" -> "Luk/ac/manchester/tornado/api/types/HalfFloat;";
            default -> throw new IllegalArgumentException(type.text());
        };
    }
}
