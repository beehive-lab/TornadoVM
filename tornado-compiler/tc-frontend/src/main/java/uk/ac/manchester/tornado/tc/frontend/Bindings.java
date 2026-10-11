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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import uk.ac.manchester.tornado.tc.ir.ApiTypes;
import uk.ac.manchester.tornado.tc.ir.Immediates;
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
    static final String DEVICE_KERNEL = "uk/ac/manchester/tornado/api/DeviceKernel";
    static final String DEBUG = "uk/ac/manchester/tornado/api/exceptions/Debug";
    static final String DEVICE_LAUNCH_MODE = "uk/ac/manchester/tornado/api/enums/DeviceLaunchMode";
    private static final String ARRAYS = "uk/ac/manchester/tornado/api/types/arrays/";
    private static final String TORNADO_MATH = "uk/ac/manchester/tornado/api/math/TornadoMath";
    private static final String JAVA_MATH = "java/lang/Math";

    /** What a call or field access becomes. */
    sealed interface Binding {
    }

    /** An intrinsic with the given result type (void for effects). */
    record Intrinsic(String name, Type result, Immediates immediates) implements Binding {
        Intrinsic(String name, Type result) {
            this(name, result, Immediates.NONE);
        }

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

    /**
     * A KernelContext method whose Java body is also its device semantics (the swizzled tile
     * accessors): translated and inlined like any helper. Most KernelContext bodies are CPU
     * stand-ins, so only methods listed here are inlined.
     */
    record InlineJava() implements Binding {
    }

    /** Constructor of a TornadoVM vector type: {@code new Float4(x, y, z, w)} or the zero vector {@code new Float4()}. */
    record VectorInit(Type.VectorType type) implements Binding {
    }

    /** {@code v.getX()} ... {@code v.get(i)} (constant {@code i}): a lane of a vector value. */
    record VectorGet(int lane) implements Binding {
    }

    /** {@code v.setX(s)} ... {@code v.set(i, s)}: a new vector with one lane replaced, rebound to every slot holding {@code v}. */
    /** An intrinsic on an instance method that keeps its receiver as operand 0 (a tile view). */
    record ReceiverIntrinsic(String name, Type type) implements Binding {
    }

    /** Whole-vector operations on a vector value: {@code set(v)}, {@code fill(s)}, {@code size()}. */
    record VectorWhole(String op) implements Binding {
    }

    /** {@code v.asFloat3()}, {@code v.getLow()}, {@code v.getHigh()}: {@code count} lanes of a vector from {@code start}, as a new vector. */
    record VectorSlice(int start, int count) implements Binding {
    }

    record VectorSet(int lane) implements Binding {
    }

    /** TornadoVM vector types (api.types.vectors) as register vectors: class simple name -> type. */
    static final Map<String, Type.VectorType> VECTORS = new HashMap<>();
    static final String VECTOR_PACKAGE = "uk/ac/manchester/tornado/api/types/vectors/";

    /** {@code new AtomicInteger(v)}: a counter in the kernel's atomics region (initial value {@code v}). */
    record AtomicNew() implements Binding {
    }

    /** An operation on an AtomicInteger: {@code tornado.atomic_int.<op>(counter[, operand])}. */
    record AtomicOp(String op, int delta) implements Binding {
    }

    private static final Map<String, Type> BOXES = Map.of("java/lang/Integer", Type.I32, "java/lang/Long", Type.I64, "java/lang/Float", Type.F32, "java/lang/Double", Type.F64);

    static final String ATOMIC_INTEGER = "java/util/concurrent/atomic/AtomicInteger";

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
            var dim = Immediates.of("dim", i);
            FIELDS.put(KERNEL_CONTEXT + ".globalId" + d, new Intrinsic("tornado.thread_id.global", Type.I32, dim));
            FIELDS.put(KERNEL_CONTEXT + ".localId" + d, new Intrinsic("tornado.thread_id.local", Type.I32, dim));
            FIELDS.put(KERNEL_CONTEXT + ".groupId" + d, new Intrinsic("tornado.group_id", Type.I32, dim));
            FIELDS.put(KERNEL_CONTEXT + ".globalGroupSize" + upper, new Intrinsic("tornado.global_size", Type.I32, dim));
            FIELDS.put(KERNEL_CONTEXT + ".localGroupSize" + upper, new Intrinsic("tornado.group_size.local", Type.I32, dim));
        }
        METHODS.put(KERNEL_CONTEXT + ".localBarrier()V", new Intrinsic("tornado.barrier.local", Type.VOID));
        METHODS.put(KERNEL_CONTEXT + ".globalBarrier()V", new Intrinsic("tornado.barrier.global", Type.VOID));
        METHODS.put(KERNEL_CONTEXT + ".allocateFloatLocalArray(I)[F", new SharedArray(Type.F32));
        METHODS.put(KERNEL_CONTEXT + ".allocateIntLocalArray(I)[I", new SharedArray(Type.I32));
        METHODS.put(KERNEL_CONTEXT + ".allocateDoubleLocalArray(I)[D", new SharedArray(Type.F64));
        METHODS.put(KERNEL_CONTEXT + ".allocateLongLocalArray(I)[J", new SharedArray(Type.I64));
        METHODS.put(KERNEL_CONTEXT + ".allocateByteLocalArray(I)[B", new SharedArray(Type.I8));
        METHODS.put(KERNEL_CONTEXT + ".allocateHalfFloatLocalArray(I)[L" + HALF_FLOAT + ";", new SharedArray(Type.F16));
        METHODS.put(KERNEL_CONTEXT + ".allocateHalf2LocalArray(I)[L" + VECTOR_PACKAGE + "Half2;", new SharedArray(new Type.VectorType(Type.F16, 2)));
        METHODS.put("java/lang/Integer.intValue()I", new Identity());
        METHODS.put("java/lang/Long.longValue()J", new Identity());
        METHODS.put("java/lang/Float.floatValue()F", new Identity());
        METHODS.put("java/lang/Double.doubleValue()D", new Identity());
        METHODS.put("java/lang/Integer.valueOf(I)Ljava/lang/Integer;", new Identity());
        METHODS.put("java/lang/Long.valueOf(J)Ljava/lang/Long;", new Identity());
        METHODS.put("java/lang/Float.valueOf(F)Ljava/lang/Float;", new Identity());
        METHODS.put("java/lang/Double.valueOf(D)Ljava/lang/Double;", new Identity());
        // Warp (SIMD group) operations: all lanes of the warp take part; not pure (never moved or merged).
        METHODS.put(KERNEL_CONTEXT + ".simdSum(F)F", new Intrinsic("tornado.simd.sum", Type.F32));
        METHODS.put(KERNEL_CONTEXT + ".simdShuffleDown(FI)F", new Intrinsic("tornado.simd.shuffle_down", Type.F32));
        METHODS.put(KERNEL_CONTEXT + ".simdBroadcastFirst(F)F", new Intrinsic("tornado.simd.broadcast_first", Type.F32));
        METHODS.put(KERNEL_CONTEXT + ".simdAny(Z)Z", new Intrinsic("tornado.simd.any", Type.I32));
        METHODS.put(KERNEL_CONTEXT + ".simdAll(Z)Z", new Intrinsic("tornado.simd.all", Type.I32));
        METHODS.put(KERNEL_CONTEXT + ".simdBallot(Z)I", new Intrinsic("tornado.simd.ballot", Type.I32));
        for (var e : Map.of("Float", Type.F32, "Double", Type.F64, "Int", Type.I32, "Short", Type.I16, "Byte", Type.I8).entrySet()) {
            // 8 and 16 lanes exist for Float, Double and Int (OpenCL C has them; CUDA refuses them in the dialect).
            for (int lanes : new int[] { 2, 3, 4, 8, 16 }) {
                var name = e.getKey() + lanes;
                if (name.equals("Short4") || name.equals("Byte2") || (lanes > 4 && !(e.getKey().equals("Float") || e.getKey().equals("Double") || e.getKey().equals("Int")))) {
                    continue; // not in the API
                }
                var vec = new Type.VectorType(e.getValue(), lanes);
                VECTORS.put(name, vec);
                var owner = VECTOR_PACKAGE + name;
                var d = switch (e.getKey()) {
                    case "Float" -> "F";
                    case "Double" -> "D";
                    case "Int" -> "I";
                    case "Short" -> "S";
                    default -> "B";
                };
                METHODS.put(owner + ".<init>()V", new VectorInit(vec));
                METHODS.put(owner + ".<init>(" + d.repeat(lanes) + ")V", new VectorInit(vec));
                var lanesNames = List.of("X", "Y", "Z", "W");
                for (int l = 0; lanes <= 4 && l < lanes; l++) {
                    METHODS.put(owner + ".get" + lanesNames.get(l) + "()" + d, new VectorGet(l));
                    METHODS.put(owner + ".set" + lanesNames.get(l) + "(" + d + ")V", new VectorSet(l));
                }
                METHODS.put(owner + ".get(I)" + d, new VectorGet(-1));
                METHODS.put(owner + ".set(I" + d + ")V", new VectorSet(-1));
                // OpenCL-style lane names, and the whole-vector operations (vectors are values).
                for (int l = 0; l < lanes; l++) {
                    METHODS.put(owner + ".getS" + l + "()" + d, new VectorGet(l));
                    METHODS.put(owner + ".setS" + l + "(" + d + ")V", new VectorSet(l));
                }
                METHODS.put(owner + ".set(L" + owner + ";)V", new VectorWhole("set"));
                METHODS.put(owner + ".fill(" + d + ")V", new VectorWhole("fill"));
                METHODS.put(owner + ".size()I", new VectorWhole("size"));
                METHODS.put(owner + ".duplicate()L" + owner + ";", new Identity());
                // The leading lanes (asFloat2, asFloat3) and the halves (getLow, getHigh), where the API has them.
                boolean slices = !e.getKey().equals("Short") && !e.getKey().equals("Byte");
                for (int k = 2; slices && k < lanes && lanes <= 4; k++) {
                    METHODS.put(owner + ".as" + e.getKey() + k + "()L" + VECTOR_PACKAGE + e.getKey() + k + ";", new VectorSlice(0, k));
                }
                if (slices && (lanes == 8 || lanes == 4 && !e.getKey().equals("Int"))) {
                    var half = VECTOR_PACKAGE + e.getKey() + lanes / 2;
                    METHODS.put(owner + ".getLow()L" + half + ";", new VectorSlice(0, lanes / 2));
                    METHODS.put(owner + ".getHigh()L" + half + ";", new VectorSlice(lanes / 2, lanes / 2));
                }
            }
        }
        // QuantizationUtils.dp4a: four signed int8 products accumulated (CUDA __dp4a, sm_61+). The Java
        // bodies' bounds checks throw, which kernels cannot; the operands are 4-byte words at the offsets.
        var quant = "uk/ac/manchester/tornado/api/utils/QuantizationUtils";
        var int8 = "L" + ARRAYS + "Int8Array;";
        METHODS.put(quant + ".dp4a(" + int8 + "J" + int8 + "JI)I", new Intrinsic("tornado.dp4a", Type.I32));
        METHODS.put(quant + ".dp4a(" + int8 + "J[BJI)I", new Intrinsic("tornado.dp4a", Type.I32));
        METHODS.put(quant + ".dp4a_packed(III)I", new Intrinsic("tornado.dp4a.packed", Type.I32));
        registerTiles();
        // HalfN: f16 lanes (CUDA half2; OpenCL halfN); their arithmetic is Java code over the lane
        // accessors and the constructors.
        var h = "L" + HALF_FLOAT + ";";
        for (int lanes : new int[] { 2, 3, 4, 8, 16 }) {
            var vec = new Type.VectorType(Type.F16, lanes);
            var name = "Half" + lanes;
            VECTORS.put(name, vec);
            var owner = VECTOR_PACKAGE + name;
            METHODS.put(owner + ".<init>()V", new VectorInit(vec));
            METHODS.put(owner + ".<init>(" + h.repeat(lanes) + ")V", new VectorInit(vec));
            var lanesNames = List.of("X", "Y", "Z", "W");
            for (int l = 0; lanes <= 4 && l < lanes; l++) {
                METHODS.put(owner + ".get" + lanesNames.get(l) + "()" + h, new VectorGet(l));
                METHODS.put(owner + ".set" + lanesNames.get(l) + "(" + h + ")V", new VectorSet(l));
            }
            METHODS.put(owner + ".get(I)" + h, new VectorGet(-1));
            METHODS.put(owner + ".set(I" + h + ")V", new VectorSet(-1));
            METHODS.put(owner + ".set(L" + owner + ";)V", new VectorWhole("set"));
            if (lanes > 2) {
                for (int l = 0; l < lanes; l++) {
                    METHODS.put(owner + ".getS" + l + "()" + h, new VectorGet(l));
                    METHODS.put(owner + ".setS" + l + "(" + h + ")V", new VectorSet(l));
                }
                METHODS.put(owner + ".size()I", new VectorWhole("size"));
            }
            if (lanes < 16) {
                METHODS.put(owner + ".duplicate()L" + owner + ";", new Identity());
            }
        }
        // java.util.concurrent.atomic.AtomicInteger: counters in the kernel's atomics region; the JDK's
        // own implementation (Unsafe) is never translated.
        METHODS.put(ATOMIC_INTEGER + ".<init>(I)V", new AtomicNew());
        METHODS.put(ATOMIC_INTEGER + ".<init>()V", new AtomicNew());
        METHODS.put(ATOMIC_INTEGER + ".incrementAndGet()I", new AtomicOp("add_get", 1));
        METHODS.put(ATOMIC_INTEGER + ".getAndIncrement()I", new AtomicOp("get_add", 1));
        METHODS.put(ATOMIC_INTEGER + ".decrementAndGet()I", new AtomicOp("sub_get", 1));
        METHODS.put(ATOMIC_INTEGER + ".getAndDecrement()I", new AtomicOp("get_sub", 1));
        METHODS.put(ATOMIC_INTEGER + ".addAndGet(I)I", new AtomicOp("add_get", 0));
        METHODS.put(ATOMIC_INTEGER + ".getAndAdd(I)I", new AtomicOp("get_add", 0));
        METHODS.put(ATOMIC_INTEGER + ".get()I", new AtomicOp("get", 0));
        METHODS.put(ATOMIC_INTEGER + ".set(I)V", new AtomicOp("set", 0));
        // Tensor cores (m16n8k16, f16 inputs, f32 accumulators): fragments are register vectors -
        // C/D four floats per lane, A four and B two 32-bit registers of packed halves.
        var half = "L" + HALF_FLOAT + ";";
        var vecC = new Type.VectorType(Type.F32, 4);
        METHODS.put(KERNEL_CONTEXT + ".mmaFragment(F)[F", new Intrinsic("tornado.mma.fragment", vecC));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadA([II)[" + half, new Intrinsic("tornado.mma.load_a", new Type.VectorType(Type.I32, 4)));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadB([II)[" + half, new Intrinsic("tornado.mma.load_b", new Type.VectorType(Type.I32, 2)));
        // Fragments straight from a row-major HalfFloatArray in global memory (no shared staging).
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadA(L" + ARRAYS + "HalfFloatArray;III)[" + half, new Intrinsic("tornado.mma.load_a.global", new Type.VectorType(Type.I32, 4)));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadB(L" + ARRAYS + "HalfFloatArray;III)[" + half, new Intrinsic("tornado.mma.load_b.global", new Type.VectorType(Type.I32, 2)));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadA([III)[" + half, new Intrinsic("tornado.mma.load_a.offset", new Type.VectorType(Type.I32, 4)));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadB([III)[" + half, new Intrinsic("tornado.mma.load_b.offset", new Type.VectorType(Type.I32, 2)));
        var tile = "[" + half;
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadBSwizzled(" + tile + "I)" + tile, new Intrinsic("tornado.mma.load_b.swizzled", new Type.VectorType(Type.I32, 2)));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadBSwizzled(" + tile + "II)" + tile, new Intrinsic("tornado.mma.load_b.swizzled.offset", new Type.VectorType(Type.I32, 2)));
        METHODS.put(KERNEL_CONTEXT + ".mmaStoreBSwizzled(" + tile + "III" + half + "I)V", new Intrinsic("tornado.mma.store_b.swizzled", Type.VOID));
        for (var accessor : new String[] { "swizzleLoadFp16Stride32(" + tile + "III)" + half, "swizzleStoreFp16Stride32(" + tile + "III" + half + ")V",
                "swizzleLoadFp16Stride16(" + tile + "III)" + half, "swizzleStoreFp16Stride16(" + tile + "III" + half + ")V", "swizzleLoadInt8([BIII)B",
                "swizzleStoreInt8([BIIIB)V" }) {
            METHODS.put(KERNEL_CONTEXT + "." + accessor, new InlineJava());
        }
        METHODS.put(KERNEL_CONTEXT + ".mma([" + half + "[" + half + "[FLuk/ac/manchester/tornado/api/enums/MMAShape;)[F", new Intrinsic("tornado.mma.m16n8k16", vecC));
        METHODS.put(KERNEL_CONTEXT + ".mmaStore([FL" + ARRAYS + "FloatArray;III)V", new Intrinsic("tornado.mma.store", Type.VOID));
        // Apple simdgroup matrices: a fragment is an opaque value (MSL simdgroup_float8x8) held by the
        // whole SIMD group. matrixMultiply8x8 is Java over these primitives.
        var matrix8x8 = "Luk/ac/manchester/tornado/api/types/matrix/Matrix8x8Float;";
        var fragment8x8 = ApiTypes.MATRIX_8X8_FLOAT;
        var half8x8 = "Luk/ac/manchester/tornado/api/types/matrix/Matrix8x8Half;";
        var halfFragment8x8 = ApiTypes.MATRIX_8X8_HALF;
        METHODS.put(KERNEL_CONTEXT + ".simdgroupMatrixZero()" + matrix8x8, new Intrinsic("tornado.simdgroup_matrix.zero", fragment8x8));
        // Loads: float or half, from a device array or a local (threadgroup) array, plain or transposed.
        for (var transposed : new boolean[] { false, true }) {
            var name = transposed ? ".simdgroupMatrixLoadTransposed(" : ".simdgroupMatrixLoad(";
            var suffix = transposed ? "_transposed" : "";
            METHODS.put(KERNEL_CONTEXT + name + "L" + ARRAYS + "FloatArray;II)" + matrix8x8, new Intrinsic("tornado.simdgroup_matrix.load" + suffix, fragment8x8));
            METHODS.put(KERNEL_CONTEXT + name + "[FII)" + matrix8x8, new Intrinsic("tornado.simdgroup_matrix.load_local" + suffix, fragment8x8));
            METHODS.put(KERNEL_CONTEXT + name + "L" + ARRAYS + "HalfFloatArray;II)" + half8x8, new Intrinsic("tornado.simdgroup_matrix.load_half" + suffix, halfFragment8x8));
            METHODS.put(KERNEL_CONTEXT + name + "[L" + HALF_FLOAT + ";II)" + half8x8, new Intrinsic("tornado.simdgroup_matrix.load_half_local" + suffix, halfFragment8x8));
        }
        // Multiply-accumulate into a float fragment; half and mixed half/float inputs too.
        for (var ab : new String[][] { { matrix8x8, matrix8x8 }, { half8x8, half8x8 }, { matrix8x8, half8x8 }, { half8x8, matrix8x8 } }) {
            METHODS.put(KERNEL_CONTEXT + ".simdgroupMatrixMultiplyAccumulate(" + ab[0] + ab[1] + matrix8x8 + ")" + matrix8x8, new Intrinsic("tornado.simdgroup_matrix.mma", fragment8x8));
        }
        METHODS.put(KERNEL_CONTEXT + ".simdgroupMatrixStore(" + matrix8x8 + "L" + ARRAYS + "FloatArray;II)V", new Intrinsic("tornado.simdgroup_matrix.store", Type.VOID));
        METHODS.put(KERNEL_CONTEXT + ".simdgroupMatrixStore(" + matrix8x8 + "[FII)V", new Intrinsic("tornado.simdgroup_matrix.store_local", Type.VOID));
        METHODS.put(KERNEL_CONTEXT + ".matrixMultiply8x8(L" + ARRAYS + "FloatArray;IIL" + ARRAYS + "FloatArray;IIL" + ARRAYS + "FloatArray;III)V", new InlineJava());
        var vecI = new Type.VectorType(Type.I32, 4);
        METHODS.put(KERNEL_CONTEXT + ".mmaFragmentInt(I)[I", new Intrinsic("tornado.mma.fragment_int", vecI));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadAInt8([II)[B", new Intrinsic("tornado.mma.load_a_s8", vecI));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadAInt8([III)[B", new Intrinsic("tornado.mma.load_a_s8.offset", vecI));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadBInt8([II)[B", new Intrinsic("tornado.mma.load_b_s8", new Type.VectorType(Type.I32, 2)));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadBInt8([III)[B", new Intrinsic("tornado.mma.load_b_s8.offset", new Type.VectorType(Type.I32, 2)));
        METHODS.put(KERNEL_CONTEXT + ".mmaInt8([B[B[ILuk/ac/manchester/tornado/api/enums/MMAShape;)[I", new Intrinsic("tornado.mma.m16n8k32_s8", vecI));
        // FP8 tiles are bytes like int8 tiles (same loads); bf16 fragments travel as HalfFloat[] like f16.
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadAFP8([II)[B", new Intrinsic("tornado.mma.load_a_s8", vecI));
        METHODS.put(KERNEL_CONTEXT + ".mmaLoadBFP8([II)[B", new Intrinsic("tornado.mma.load_b_s8", new Type.VectorType(Type.I32, 2)));
        METHODS.put(KERNEL_CONTEXT + ".mmaFP8E4M3([B[B[FLuk/ac/manchester/tornado/api/enums/MMAShape;)[F", new Intrinsic("tornado.mma.m16n8k32_e4m3", vecC));
        METHODS.put(KERNEL_CONTEXT + ".mmaFP8E5M2([B[B[FLuk/ac/manchester/tornado/api/enums/MMAShape;)[F", new Intrinsic("tornado.mma.m16n8k32_e5m2", vecC));
        METHODS.put(KERNEL_CONTEXT + ".mmaBF16([" + half + "[" + half + "[FLuk/ac/manchester/tornado/api/enums/MMAShape;)[F", new Intrinsic("tornado.mma.m16n8k16_bf16", vecC));
        // cp.async: global to shared copies of one packed b32 slot (4 bytes) or four of them (16 bytes).
        for (var source : List.of("HalfFloatArray", "FP8Array", "ByteArray")) {
            var bytes = source.equals("HalfFloatArray") ? 2 : 1;
            METHODS.put(KERNEL_CONTEXT + ".asyncCopyToLocal([IIL" + ARRAYS + source + ";I)V", new Intrinsic("tornado.async_copy", Type.VOID, Immediates.of("bytes", 4, "elem_bytes", bytes)));
            if (!source.equals("FP8Array")) {
                METHODS.put(KERNEL_CONTEXT + ".asyncCopyToLocal16([IIL" + ARRAYS + source + ";I)V", new Intrinsic("tornado.async_copy", Type.VOID, Immediates.of("bytes", 16, "elem_bytes", bytes)));
            }
        }
        METHODS.put(KERNEL_CONTEXT + ".asyncCopyCommit()V", new Intrinsic("tornado.async_copy.commit", Type.VOID));
        METHODS.put(KERNEL_CONTEXT + ".asyncCopyWaitGroup(I)V", new Intrinsic("tornado.async_copy.wait", Type.VOID));
        METHODS.put(KERNEL_CONTEXT + ".mmaStoreInt([IL" + ARRAYS + "IntArray;III)V", new Intrinsic("tornado.mma.store_int", Type.VOID));
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
            // 32- and 64-bit words at a byte offset, read or written whole (the caller aligns the offset).
            METHODS.put(ARRAYS + "ByteArray.getInt(" + index + ")I", new ByteView(Type.I32, false));
            METHODS.put(ARRAYS + "ByteArray.setInt(" + index + "I)V", new ByteView(Type.I32, true));
            METHODS.put(ARRAYS + "ByteArray.getLong(" + index + ")J", new ByteView(Type.I64, false));
            METHODS.put(ARRAYS + "ByteArray.setLong(" + index + "J)V", new ByteView(Type.I64, true));
            // Packed half pairs: one 32-bit access at element index (even, so 4-byte aligned).
            METHODS.put(ARRAYS + "HalfFloatArray.getHalf2(" + index + ")L" + VECTOR_PACKAGE + "Half2;", new ByteView(new Type.VectorType(Type.F16, 2), false));
            METHODS.put(ARRAYS + "HalfFloatArray.setHalf2(" + index + "L" + VECTOR_PACKAGE + "Half2;)V", new ByteView(new Type.VectorType(Type.F16, 2), true));
        }
        // Vector-width loads at an element index (a multiple of 4, so the access is aligned).
        METHODS.put(ARRAYS + "FloatArray.getFloat4(I)L" + VECTOR_PACKAGE + "Float4;", new ByteView(new Type.VectorType(Type.F32, 4), false));
        METHODS.put(ARRAYS + "HalfFloatArray.getHalf4(I)L" + VECTOR_PACKAGE + "Half4;", new ByteView(new Type.VectorType(Type.F16, 4), false));
        METHODS.put(HALF_FLOAT + ".<init>(F)V", new ValueInit(Type.F32, ConvertOp.FTRUNC, Type.F16));
        METHODS.put(HALF_FLOAT + ".<init>(S)V", new ValueInit(Type.I16, ConvertOp.BITCAST, Type.F16));
        METHODS.put(HALF_FLOAT + ".getFloat32()F", new Convert(ConvertOp.FEXT, Type.F32));
        METHODS.put(HALF_FLOAT + ".getHalfFloatValue()S", new HalfBits());
        var halfOp = "(L" + HALF_FLOAT + ";L" + HALF_FLOAT + ";)L" + HALF_FLOAT + ";";
        METHODS.put(HALF_FLOAT + ".add" + halfOp, new HalfArith(Opcode.BinaryOp.FADD));
        METHODS.put(HALF_FLOAT + ".sub" + halfOp, new HalfArith(Opcode.BinaryOp.FSUB));
        METHODS.put(HALF_FLOAT + ".mult" + halfOp, new HalfArith(Opcode.BinaryOp.FMUL));
        METHODS.put(HALF_FLOAT + ".div" + halfOp, new HalfArith(Opcode.BinaryOp.FDIV));
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
        // Each owner's own functions (BindingsTest checks them against the classes).
        for (var fn : List.of("sqrt", "exp", "log", "sin", "cos", "tan", "tanh", "abs", "floor", "ceil", "atan", "asin", "acos", "asinh", "acosh")) {
            METHODS.put(TORNADO_MATH + "." + fn + "(F)F", new Intrinsic("math." + fn, Type.F32));
            METHODS.put(TORNADO_MATH + "." + fn + "(D)D", new Intrinsic("math." + fn, Type.F64));
        }
        // rint too: the JDK's version rounds by adding and subtracting 2^52, which fast-math device
        // compilers (TornadoVM's OpenCL default, -cl-fast-relaxed-math) fold away; the built-in is exact.
        for (var fn : List.of("sqrt", "exp", "log", "sin", "cos", "tan", "tanh", "abs", "floor", "ceil", "rint", "atan", "asin", "acos", "sinh", "cosh", "cbrt", "log10")) {
            METHODS.put(JAVA_MATH + "." + fn + "(D)D", new Intrinsic("math." + fn, Type.F64));
        }
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
        // Their Java bodies compute in double (Math.sin(x * Math.PI)): device built-ins keep a float
        // kernel in single precision, so it also runs on devices without fp64.
        for (var fn : List.of("sinpi", "cospi", "toRadians")) {
            METHODS.put(TORNADO_MATH + "." + fn + "(F)F", new Intrinsic("math." + fn, Type.F32));
            METHODS.put(TORNADO_MATH + "." + fn + "(D)D", new Intrinsic("math." + fn, Type.F64));
        }
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

    /** Every bound method, as {@code owner.name(descriptor)} (checked against the API by BindingsTest). */
    static java.util.Set<String> methodKeys() {
        return java.util.Collections.unmodifiableSet(METHODS.keySet());
    }

    /** Every bound field, as {@code owner.name}. */
    static java.util.Set<String> fieldKeys() {
        return java.util.Collections.unmodifiableSet(FIELDS.keySet());
    }

    // ---------------------------------------------------------------- CUDA Tile (TileContext)

    static final String TILE_API = "uk/ac/manchester/tornado/api/tile/";

    /**
     * TileContext and PartitionView become {@code tile.*} intrinsics on opaque tile values; their
     * static types (element type and shape) are computed after specialisation by TileTyping. A
     * DType argument arrives as its enum ordinal, shapes and axes as constants.
     */
    private static void registerTiles() {
        // Locals, not constants: this runs from the static initialiser, before later fields are set.
        final Type tileType = ApiTypes.TILE;
        final Type viewType = ApiTypes.TENSOR_VIEW;
        final Type partitionType = ApiTypes.PARTITION_VIEW;
        var ctx = TILE_API + "TileContext.";
        var tile = "L" + TILE_API + "Tile;";
        var view = "L" + TILE_API + "TensorView;";
        var partition = "L" + TILE_API + "PartitionView;";
        var dtype = "L" + TILE_API + "DType;";
        var axes = List.of("X", "Y", "Z");
        for (int i = 0; i < axes.size(); i++) {
            var dim = Immediates.of("dim", i);
            METHODS.put(ctx + "bid" + axes.get(i) + "()I", new Intrinsic("tile.bid", Type.I32, dim));
            METHODS.put(ctx + "numBlocks" + axes.get(i) + "()I", new Intrinsic("tile.num_blocks", Type.I32, dim));
        }
        var views = Map.of("FloatArray", "F32", "HalfFloatArray", "F16", "BFloat16Array", "BF16", "IntArray", "S32", "Int8Array", "S8", "DoubleArray", "F64", "ByteArray", "S8");
        views.forEach((array, element) -> {
            var a = "L" + ARRAYS + array + ";";
            // One to three extents; ByteArray views have no three-extent form.
            for (var dims : array.equals("ByteArray") ? List.of("I", "II") : List.of("I", "II", "III")) {
                METHODS.put(ctx + "view(" + a + dims + ")" + view, new Intrinsic("tile.view", viewType, Immediates.of("elem", element)));
            }
            METHODS.put(ctx + "viewStrided(" + a + "IIII)" + view, new Intrinsic("tile.view_strided", viewType, Immediates.of("elem", element)));
        });
        METHODS.put(ctx + "viewStrided(L" + ARRAYS + "ByteArray;" + dtype + "IIII)" + view, new Intrinsic("tile.view_strided", viewType));
        METHODS.put(ctx + "view(L" + ARRAYS + "FP8Array;" + dtype + "I)" + view, new Intrinsic("tile.view", viewType));
        METHODS.put(ctx + "view(L" + ARRAYS + "FP8Array;" + dtype + "II)" + view, new Intrinsic("tile.view", viewType));
        for (var dims : List.of("I", "II", "III")) {
            METHODS.put(ctx + "partition(" + view + dims + ")" + partition, new Intrinsic("tile.partition", partitionType));
        }
        for (var dims : List.of("I", "II")) {
            METHODS.put(ctx + "zeros(" + dtype + dims + ")" + tile, new Intrinsic("tile.zeros", tileType));
            METHODS.put(ctx + "ones(" + dtype + dims + ")" + tile, new Intrinsic("tile.ones", tileType));
            METHODS.put(ctx + "full(" + dtype + "D" + dims + ")" + tile, new Intrinsic("tile.full", tileType));
            METHODS.put(ctx + "iota(" + dtype + dims + ")" + tile, new Intrinsic("tile.iota", tileType));
            METHODS.put(ctx + "broadcast(" + tile + dims + ")" + tile, new Intrinsic("tile.broadcast", tileType));
        }
        for (var dims : List.of("I", "II", "III")) {
            METHODS.put(ctx + "reshape(" + tile + dims + ")" + tile, new Intrinsic("tile.reshape", tileType));
        }
        METHODS.put(ctx + "extract(" + tile + "II)" + tile, new Intrinsic("tile.extract", tileType, Immediates.of("rank", 1)));
        METHODS.put(ctx + "extract(" + tile + "IIII)" + tile, new Intrinsic("tile.extract", tileType, Immediates.of("rank", 2)));
        var tt = "(" + tile + tile + ")" + tile;
        for (var op : List.of("add", "sub", "mul", "div", "bitwiseAnd", "bitwiseOr", "bitwiseXor", "shiftLeft", "shiftRight", "maximum", "minimum", "atan2", "pow", "remainder",
                "floorDiv", "ceilDiv", "mulhi", "logicalAnd", "logicalOr", "matmul")) {
            METHODS.put(ctx + op + tt, new Intrinsic("tile.binary." + op, tileType));
        }
        for (var op : List.of("lessThan", "lessOrEqual", "greaterThan", "greaterOrEqual", "equalTo", "notEqualTo")) {
            METHODS.put(ctx + op + tt, new Intrinsic("tile.compare." + op, tileType));
            METHODS.put(ctx + op + "(" + tile + "D)" + tile, new Intrinsic("tile.compare_scalar." + op, tileType));
        }
        for (var op : List.of("exp", "sin", "cos", "sqrt", "rsqrt", "log", "log2", "exp2", "tanh", "abs", "floor", "ceil", "tan", "sinh", "cosh", "bitwiseNot", "isNaN",
                "isInfinite", "logicalNot", "transpose")) {
            METHODS.put(ctx + op + "(" + tile + ")" + tile, new Intrinsic("tile.unary." + op, tileType));
        }
        for (var op : List.of("sum", "max", "min", "prod", "reduceBitAnd", "reduceBitOr", "reduceBitXor", "allOf", "anyOf", "prefixSum", "prefixProduct")) {
            METHODS.put(ctx + op + "(" + tile + "I)" + tile, new Intrinsic("tile.reduce." + op, tileType));
        }
        METHODS.put(ctx + "select(" + tile + tile + tile + ")" + tile, new Intrinsic("tile.select", tileType));
        METHODS.put(ctx + "fma(" + tile + tile + tile + ")" + tile, new Intrinsic("tile.fma", tileType));
        METHODS.put(ctx + "mma(" + tile + tile + tile + ")" + tile, new Intrinsic("tile.mma", tileType));
        METHODS.put(ctx + "scale(" + tile + "D)" + tile, new Intrinsic("tile.scale", tileType));
        METHODS.put(ctx + "cast(" + tile + dtype + ")" + tile, new Intrinsic("tile.cast", tileType));
        METHODS.put(ctx + "bitcast(" + tile + dtype + ")" + tile, new Intrinsic("tile.bitcast", tileType));
        METHODS.put(ctx + "concat(" + tile + tile + "I)" + tile, new Intrinsic("tile.concat", tileType));
        var pv = TILE_API + "PartitionView.";
        for (var dims : List.of("I", "II", "III")) {
            METHODS.put(pv + "load(" + dims + ")" + tile, new ReceiverIntrinsic("tile.load", tileType));
            METHODS.put(pv + "loadMasked(" + dims + ")" + tile, new ReceiverIntrinsic("tile.load_masked", tileType));
            METHODS.put(pv + "store(" + tile + dims + ")V", new ReceiverIntrinsic("tile.store", Type.VOID));
            METHODS.put(pv + "storeMasked(" + tile + dims + ")V", new ReceiverIntrinsic("tile.store_masked", Type.VOID));
        }
        for (var dims : List.of("I", "II")) {
            METHODS.put(pv + "atomicLoad(" + dims + ")" + tile, new ReceiverIntrinsic("tile.atomic_load", tileType));
            METHODS.put(pv + "atomicStore(" + tile + dims + ")V", new ReceiverIntrinsic("tile.atomic_store", Type.VOID));
            for (var op : List.of("Add", "Sub", "Min", "Max", "And", "Or", "Xor", "Exchange")) {
                METHODS.put(pv + "atomic" + op + "(" + tile + dims + ")V", new ReceiverIntrinsic("tile.atomic." + op.toLowerCase(), Type.VOID));
            }
        }
    }

    /** A vector collection ({@code VectorFloat2}, ...): its element type and the lanes per element. */
    record Collection(Type element, int width) {
    }

    private static final String COLLECTIONS = "uk/ac/manchester/tornado/api/types/collections/";

    static Optional<Collection> collection(String internalName) {
        if (!internalName.startsWith(COLLECTIONS)) {
            return Optional.empty();
        }
        var m = java.util.regex.Pattern.compile("Vector(Float|Double|Int|Half)(\\d+)").matcher(internalName.substring(COLLECTIONS.length()));
        if (!m.matches()) {
            return Optional.empty();
        }
        var element = switch (m.group(1)) {
            case "Float" -> Type.F32;
            case "Double" -> Type.F64;
            case "Int" -> Type.I32;
            default -> Type.F16;
        };
        return Optional.of(new Collection(element, Integer.parseInt(m.group(2))));
    }

    /** The TC type of a field or parameter descriptor ({@code I}, {@code [F}, {@code Lpkg/Cls;}, ...). */
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
                // Boxed scalars are their primitive (the runtime passes boxed task arguments as values).
                if (BOXES.containsKey(cls)) {
                    yield BOXES.get(cls);
                }
                if (cls.startsWith(VECTOR_PACKAGE) && VECTORS.containsKey(cls.substring(VECTOR_PACKAGE.length()))) {
                    yield VECTORS.get(cls.substring(VECTOR_PACKAGE.length()));
                }
                if (cls.startsWith(ARRAYS) && NATIVE_ARRAYS.containsKey(cls.substring(ARRAYS.length()))) {
                    yield new Type.ArrayType(NATIVE_ARRAYS.get(cls.substring(ARRAYS.length())));
                }
                yield ApiTypes.of(cls);
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
