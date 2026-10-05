/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.mlx.provider;

import static java.util.Map.entry;
import static uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.contiguousStrides;
import static uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.scalarBytes;
import static uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.MLX_BOOL;
import static uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.MLX_COMPLEX64;
import static uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.MLX_FLOAT32;
import static uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.MLX_INT32;
import static uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.MLX_UINT8;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.Program;
import uk.ac.manchester.tornado.mlx.provider.MlxMetalKernels.Ref;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoNativeStreamSupport;

/**
 * The MLX operations that run as MLX's own Metal kernels in place on TornadoVM's buffers
 * ({@link MlxMetalKernels}), with how each maps onto MLX's kernels. Composite operations follow the
 * primitives MLX's {@code ops.cpp} builds them from, encoded into one command buffer; where an
 * intermediate needs its own storage it goes to a scratch buffer. A route declines (returns null)
 * when the arguments are outside what it reproduces exactly, and the task then fails.
 */
final class MlxKernelRoutes {

    private static final AtomicLong DISPATCHES = new AtomicLong();

    private static final double INF = Double.POSITIVE_INFINITY;

    /** Plans the kernels for one call, or returns null if it cannot run these arguments. */
    interface Route {
        Encoder plan(View v);
    }

    /** Encodes the planned kernels into a program. */
    interface Encoder {
        void encode(Program p);
    }

    /** The arguments of one call, as MLX kernels see them. */
    static final class View {
        private final LibraryInvocation invocation;

        View(LibraryInvocation invocation) {
            this.invocation = invocation;
        }

        int args() {
            return invocation.getNumArgs();
        }

        boolean isArray(int i) {
            return i < args() && invocation.getArg(i) instanceof TornadoNativeArray && invocation.getNativeBuffer(i) != 0;
        }

        int dtype(int i) {
            return MlxMetalKernels.dtypeOf(invocation.getArg(i));
        }

        int size(int i) {
            return ((TornadoNativeArray) invocation.getArg(i)).getSize();
        }

        Ref ref(int i) {
            return new Ref(invocation.getNativeBuffer(i), invocation.getNativeOffset(i));
        }

        float floatArg(int i) {
            return ((Number) invocation.getArg(i)).floatValue();
        }

        /** The GPU architecture name of this call's device, or null if it cannot be read. */
        /** Whether mlx.metallib has kernel {@code name} on this call's device. */
        boolean hasKernel(String name) {
            if (!(invocation.getDevice() instanceof TornadoNativeStreamSupport streams)) {
                return false;
            }
            long queue = streams.getNativeStream(invocation.getExecutionPlanId());
            return queue != 0 && MlxMetalKernels.hasKernel(queue, name);
        }

        String architecture() {
            if (!(invocation.getDevice() instanceof TornadoNativeStreamSupport streams)) {
                return null;
            }
            long queue = streams.getNativeStream(invocation.getExecutionPlanId());
            return queue == 0 ? null : MlxMetalKernels.architecture(queue);
        }

        int intArg(int i) {
            return ((Number) invocation.getArg(i)).intValue();
        }

        boolean boolArg(int i) {
            Object arg = invocation.getArg(i);
            return arg instanceof Boolean b ? b : ((Number) arg).intValue() != 0;
        }

        /** Arrays {@code indices} all exist, share one type from {@code allowed} and one size; returns that type, or -1. */
        int sameType(TypeSet allowed, int... indices) {
            if (indices.length == 0 || !isArray(indices[0])) {
                return -1;
            }
            int type = dtype(indices[0]);
            int n = size(indices[0]);
            for (int i : indices) {
                if (!isArray(i) || dtype(i) != type || size(i) != n) {
                    return -1;
                }
            }
            return allowed.contains(type) && n > 0 ? type : -1;
        }

        /** Array {@code i} exists, has {@code type} and {@code n} elements. */
        boolean is(int i, int type, int n) {
            return isArray(i) && dtype(i) == type && size(i) == n;
        }
    }

    interface TypeSet {
        boolean contains(int dtype);
    }

    private static final TypeSet FLOATS = MlxMetalKernels::isFloating;
    private static final TypeSet FLOAT32 = t -> t == MLX_FLOAT32;
    private static final TypeSet INTEGERS = t -> t != MLX_BOOL && t != MLX_COMPLEX64 && !MlxMetalKernels.isFloating(t) && MlxMetalKernels.typeName(t) != null;
    private static final TypeSet NUMBERS = t -> FLOATS.contains(t) || INTEGERS.contains(t);
    private static final TypeSet ANY = t -> MlxMetalKernels.typeName(t) != null;

    private static final Map<String, Route> ROUTES = new HashMap<>();

    /** Unary math: provider name to MLX's Metal op name (floating types). */
    private static final Map<String, String> UNARY = Map.ofEntries(entry("negative", "Negative"), entry("exp", "Exp"), entry("tanh", "Tanh"), entry("erf", "Erf"), //
            entry("sigmoid", "Sigmoid"), entry("sqrt", "Sqrt"), entry("rsqrt", "Rsqrt"), entry("square", "Square"), entry("abs", "Abs"), entry("arccos", "ArcCos"), //
            entry("arccosh", "ArcCosh"), entry("arcsin", "ArcSin"), entry("arcsinh", "ArcSinh"), entry("arctan", "ArcTan"), entry("arctanh", "ArcTanh"), entry("ceil", "Ceil"), //
            entry("cos", "Cos"), entry("cosh", "Cosh"), entry("erfinv", "ErfInv"), entry("expm1", "Expm1"), entry("floor", "Floor"), entry("log", "Log"), entry("log10", "Log10"), //
            entry("log1p", "Log1p"), entry("log2", "Log2"), entry("sign", "Sign"), entry("sin", "Sin"), entry("sinh", "Sinh"), entry("tan", "Tan"));

    /** Binary arithmetic (floating types). */
    private static final Map<String, String> BINARY = Map.ofEntries(entry("add", "Add"), entry("subtract", "Subtract"), entry("multiply", "Multiply"), //
            entry("divide", "Divide"), entry("maximum", "Maximum"), entry("minimum", "Minimum"), entry("arctan2", "ArcTan2"), entry("logaddexp", "LogAddExp"), //
            entry("power", "Power"), entry("remainder", "Remainder"));

    /** Comparisons, writing MLX bools (one byte, 0 or 1) into a byte array. */
    private static final Map<String, String> COMPARISON = Map.ofEntries(entry("equal", "Equal"), entry("not_equal", "NotEqual"), entry("greater", "Greater"), //
            entry("greater_equal", "GreaterEqual"), entry("less", "Less"), entry("less_equal", "LessEqual"));

    /** Bitwise operations (integer types). */
    private static final Map<String, String> BITWISE = Map.ofEntries(entry("bitwise_and", "BitwiseAnd"), entry("bitwise_or", "BitwiseOr"), entry("bitwise_xor", "BitwiseXor"), //
            entry("left_shift", "LeftShift"), entry("right_shift", "RightShift"));

    static {
        UNARY.forEach((name, op) -> ROUTES.put("mlx_" + name, v -> {
            int t = v.sameType(FLOATS, 0, 1);
            return t < 0 || v.args() != 2 ? null : p -> p.unary(op, t, t, v.size(0), v.ref(0), v.ref(1));
        }));
        BINARY.forEach((name, op) -> ROUTES.put("mlx_" + name, v -> {
            int t = v.sameType(FLOATS, 0, 1, 2);
            return t < 0 || v.args() != 3 ? null : p -> p.binary(op, t, v.size(0), v.ref(0), v.ref(1), v.ref(2));
        }));
        COMPARISON.forEach((name, op) -> ROUTES.put("mlx_" + name, v -> {
            int t = v.sameType(NUMBERS, 0, 1);
            return t < 0 || v.args() != 3 || !v.is(2, MLX_UINT8, v.size(0)) ? null : p -> p.binary(op, t, v.size(0), v.ref(0), v.ref(1), v.ref(2));
        }));
        BITWISE.forEach((name, op) -> ROUTES.put("mlx_" + name, v -> {
            int t = v.sameType(INTEGERS, 0, 1, 2);
            return t < 0 || v.args() != 3 ? null : p -> p.binary(op, t, v.size(0), v.ref(0), v.ref(1), v.ref(2));
        }));
        ROUTES.put("mlx_bitwise_invert", v -> {
            int t = v.sameType(INTEGERS, 0, 1);
            return t < 0 ? null : p -> p.unary("BitwiseInvert", t, t, v.size(0), v.ref(0), v.ref(1));
        });

        // degrees/radians: multiply(a, array(180 / pi, dtype)); reciprocal: divide(array(1, dtype), a).
        ROUTES.put("mlx_degrees", v -> scaled(v, 180.0 / Math.PI));
        ROUTES.put("mlx_radians", v -> scaled(v, Math.PI / 180.0));
        ROUTES.put("mlx_reciprocal", v -> {
            int t = v.sameType(FLOATS, 0, 1);
            return t < 0 ? null : p -> p.binaryScalarLeft("Divide", t, v.size(0), 1.0, v.ref(0), v.ref(1));
        });
        // floor_divide: floor(divide(a, b)) for floating types; integer Divide otherwise.
        ROUTES.put("mlx_floor_divide", v -> {
            int t = v.sameType(NUMBERS, 0, 1, 2);
            if (t < 0) {
                return null;
            }
            int n = v.size(0);
            return MlxMetalKernels.isFloating(t) ? p -> {
                p.binary("Divide", t, n, v.ref(0), v.ref(1), v.ref(2));
                p.unary("Floor", t, t, n, v.ref(2), v.ref(2));
            } : p -> p.binary("Divide", t, n, v.ref(0), v.ref(1), v.ref(2));
        });
        // round(a, decimals): Round, or multiply by 10^d, Round, multiply by 1 / 10^d.
        ROUTES.put("mlx_round", v -> {
            int t = v.sameType(FLOATS, 0, 1);
            if (t < 0 || v.args() != 3) {
                return null;
            }
            int n = v.size(0);
            int decimals = v.intArg(2);
            if (decimals == 0) {
                return p -> p.unary("Round", t, t, n, v.ref(0), v.ref(1));
            }
            float scale = (float) Math.pow(10, decimals);
            return p -> {
                p.binaryScalarRight("Multiply", t, n, v.ref(0), scale, v.ref(1));
                p.unary("Round", t, t, n, v.ref(1), v.ref(1));
                p.binaryScalarRight("Multiply", t, n, v.ref(1), 1 / scale, v.ref(1));
            };
        });
        ROUTES.put("mlx_divmod", v -> {
            int t = v.sameType(NUMBERS, 0, 1, 2, 3);
            return t < 0 ? null : p -> p.binaryTwo("DivMod", t, v.size(0), v.ref(0), v.ref(1), v.ref(2), v.ref(3));
        });
        // clip: minimum(maximum(a, lo), hi).
        ROUTES.put("mlx_clip", v -> {
            int t = v.sameType(NUMBERS, 0, 1);
            if (t < 0 || v.args() != 4) {
                return null;
            }
            int n = v.size(0);
            double lo = t == MLX_INT32 ? v.intArg(2) : v.floatArg(2);
            double hi = t == MLX_INT32 ? v.intArg(3) : v.floatArg(3);
            return p -> {
                p.binaryScalarRight("Maximum", t, n, v.ref(0), lo, v.ref(1));
                p.binaryScalarRight("Minimum", t, n, v.ref(1), hi, v.ref(1));
            };
        });
        // where: Select over astype(condition, bool).
        ROUTES.put("mlx_where", v -> {
            int t = v.sameType(NUMBERS, 1, 2, 3);
            if (t < 0 || !v.isArray(0) || v.size(0) != v.size(1)) {
                return null;
            }
            int n = v.size(1);
            int conditionType = v.dtype(0);
            return p -> {
                Ref condition = p.scratch(n);
                p.copy(conditionType, MLX_BOOL, n, v.ref(0), condition);
                p.select(t, n, condition, v.ref(1), v.ref(2), v.ref(3));
            };
        });

        // Predicates on floating types, writing bools.
        ROUTES.put("mlx_isnan", v -> predicate(v, (p, t, n) -> p.binary("NotEqual", t, n, v.ref(0), v.ref(0), v.ref(1))));
        ROUTES.put("mlx_isposinf", v -> predicate(v, (p, t, n) -> p.binaryScalarRight("Equal", t, n, v.ref(0), INF, v.ref(1))));
        ROUTES.put("mlx_isneginf", v -> predicate(v, (p, t, n) -> p.binaryScalarRight("Equal", t, n, v.ref(0), -INF, v.ref(1))));
        // isinf: |a| == inf (equal to isposinf or isneginf). isfinite: |a| < inf (false for NaN and infinities).
        ROUTES.put("mlx_isinf", v -> predicate(v, (p, t, n) -> {
            Ref magnitude = p.scratch((long) n * MlxMetalKernels.itemSize(t));
            p.unary("Abs", t, t, n, v.ref(0), magnitude);
            p.binaryScalarRight("Equal", t, n, magnitude, INF, v.ref(1));
        }));
        ROUTES.put("mlx_isfinite", v -> predicate(v, (p, t, n) -> {
            Ref magnitude = p.scratch((long) n * MlxMetalKernels.itemSize(t));
            p.unary("Abs", t, t, n, v.ref(0), magnitude);
            p.binaryScalarRight("Less", t, n, magnitude, INF, v.ref(1));
        }));

        // Logical operations on astype(x, bool).
        ROUTES.put("mlx_logical_and", v -> logical(v, "LogicalAnd"));
        ROUTES.put("mlx_logical_or", v -> logical(v, "LogicalOr"));
        ROUTES.put("mlx_logical_not", v -> {
            if (!v.isArray(0) || !v.is(1, MLX_UINT8, v.size(0)) || MlxMetalKernels.typeName(v.dtype(0)) == null) {
                return null;
            }
            int n = v.size(0);
            int t = v.dtype(0);
            return p -> {
                Ref b = p.scratch(n);
                p.copy(t, MLX_BOOL, n, v.ref(0), b);
                p.unary("LogicalNot", MLX_BOOL, MLX_BOOL, n, b, v.ref(1));
            };
        });

        // nan_to_num: three Selects of scalars over isnan, isposinf and isneginf masks.
        ROUTES.put("mlx_nan_to_num", v -> {
            int t = v.sameType(FLOATS, 0, 1);
            if (t < 0 || v.args() != 5) {
                return null;
            }
            int n = v.size(0);
            double nan = v.floatArg(2);
            double posinf = v.floatArg(3);
            double neginf = v.floatArg(4);
            return p -> {
                Ref mask = p.scratch(n);
                p.binary("NotEqual", t, n, v.ref(0), v.ref(0), mask);
                p.selectScalar(t, n, mask, nan, v.ref(0), v.ref(1));
                p.binaryScalarRight("Equal", t, n, v.ref(0), INF, mask);
                p.selectScalar(t, n, mask, posinf, v.ref(1), v.ref(1));
                p.binaryScalarRight("Equal", t, n, v.ref(0), -INF, mask);
                p.selectScalar(t, n, mask, neginf, v.ref(1), v.ref(1));
            };
        });

        ROUTES.put("mlx_isclose", MlxKernelRoutes::isclose);

        // Complex parts, with complex64 data held as interleaved float pairs.
        ROUTES.put("mlx_real", v -> complexPart(v, "Real", false));
        ROUTES.put("mlx_imag", v -> complexPart(v, "Imag", false));
        ROUTES.put("mlx_conjugate", v -> complexPart(v, "Conjugate", true));

        registerShapeRoutes();
        registerConstructionRoutes();
        registerRowRoutes();
        registerReductionRoutes();
        registerCompositeRoutes();
        registerMatmulRoutes();
        registerScanRoutes();
        registerSortRoutes();
        registerRandomRoutes();
        registerSmallCompositeRoutes();
        registerFftRoutes();
        registerConvRoutes();
    }

    private MlxKernelRoutes() {
    }

    // ---------------------------------------------------------------- shape and layout

    /** Arrays {@code in} and {@code out} exist and share a type MLX's kernels take; returns it or -1. */
    private static int pairType(View v, int in, int out) {
        if (!v.isArray(in) || !v.isArray(out) || v.dtype(in) != v.dtype(out) || MlxMetalKernels.typeName(v.dtype(in)) == null) {
            return -1;
        }
        return v.dtype(in);
    }

    /** A same-size, same-type copy: the result of any reshape-like view made contiguous. */
    private static Encoder contiguousCopy(View v) {
        int t = pairType(v, 0, 1);
        if (t < 0 || v.size(0) != v.size(1)) {
            return null;
        }
        return p -> p.copy(t, t, v.size(0), v.ref(0), v.ref(1));
    }

    /** {@code out} (contiguous) = {@code in} read as {@code shape} with {@code strides} from element {@code offset}. */
    private static Encoder view(View v, int in, int out, int[] shape, long[] strides, long offset) {
        int t = pairType(v, in, out);
        long n = 1;
        for (int d : shape) {
            n *= d;
        }
        if (t < 0 || n != v.size(out)) {
            return null;
        }
        return p -> p.generalCopy(t, t, shape, strides, v.ref(in).plus(offset, t), null, v.ref(out), null);
    }

    /** The permutation of a 3D contiguous array {@code dims}: the output shape and the input strides it reads with. */
    private static Encoder permute3(View v, int[] dims, int[] perm) {
        long[] strides = contiguousStrides(dims);
        int[] shape = new int[3];
        long[] permuted = new long[3];
        for (int i = 0; i < 3; i++) {
            if (perm[i] < 0 || perm[i] > 2) {
                return null;
            }
            shape[i] = dims[perm[i]];
            permuted[i] = strides[perm[i]];
        }
        return view(v, 0, 1, shape, permuted, 0);
    }

    private static int[] dims3(View v, int first) {
        return new int[] { v.intArg(first), v.intArg(first + 1), v.intArg(first + 2) };
    }

    /** Normalises an axis of a 3D array (negative axes count from the end). */
    private static int axis3(int axis) {
        return axis < 0 ? axis + 3 : axis;
    }

    /** Copies block {@code rows x cols} of {@code src} (row stride {@code srcCols}) to {@code dst} (row stride {@code dstCols}). */
    private static void block(Program p, int t, Ref src, int srcCols, long srcOffset, Ref dst, int dstCols, long dstOffset, int rows, int cols) {
        if (rows <= 0 || cols <= 0) {
            return;
        }
        p.generalCopy(t, t, new int[] { rows, cols }, new long[] { srcCols, 1 }, src.plus(srcOffset, t), new long[] { dstCols, 1 }, dst.plus(dstOffset, t), null);
    }

    /** Rolls a {@code rows x cols} array by {@code s0} rows and {@code s1} columns (non-negative, reduced), as four block copies. */
    private static Encoder roll2(View v, int rows, int cols, int shiftRows, int shiftCols) {
        int t = pairType(v, 0, 1);
        if (t < 0 || rows <= 0 || cols <= 0 || v.size(0) != rows * cols || v.size(1) != rows * cols) {
            return null;
        }
        int s0 = Math.floorMod(shiftRows, rows);
        int s1 = Math.floorMod(shiftCols, cols);
        return p -> {
            Ref x = v.ref(0);
            Ref out = v.ref(1);
            int[][] rowParts = { { 0, s0, rows - s0 }, { rows - s0, 0, s0 } };
            int[][] colParts = { { 0, s1, cols - s1 }, { cols - s1, 0, s1 } };
            for (int[] r : rowParts) {
                for (int[] c : colParts) {
                    block(p, t, x, cols, (long) r[0] * cols + c[0], out, cols, (long) r[1] * cols + c[1], r[2], c[2]);
                }
            }
        };
    }

    private static void registerShapeRoutes() {
        for (String name : new String[] { "reshape", "flatten", "unflatten", "squeeze", "squeeze_axis", "squeeze_axes", "expand_dims", "expand_dims_axes", "atleast_1d",
                "atleast_2d", "atleast_3d", "contiguous", "copy" }) {
            ROUTES.put("mlx_" + name, MlxKernelRoutes::contiguousCopy);
        }
        ROUTES.put("mlx_astype", v -> {
            if (!v.isArray(0) || !v.isArray(1) || v.size(0) != v.size(1) || !ANY.contains(v.dtype(0)) || !ANY.contains(v.dtype(1))) {
                return null;
            }
            int from = v.dtype(0);
            int to = v.dtype(1);
            return p -> p.copy(from, to, v.size(0), v.ref(0), v.ref(1));
        });
        // view: the same bytes under another type.
        ROUTES.put("mlx_view", v -> {
            if (!v.isArray(0) || !v.isArray(1) || !ANY.contains(v.dtype(0)) || !ANY.contains(v.dtype(1))) {
                return null;
            }
            long bytes = (long) v.size(0) * MlxMetalKernels.itemSize(v.dtype(0));
            if (bytes != (long) v.size(1) * MlxMetalKernels.itemSize(v.dtype(1)) || bytes > Integer.MAX_VALUE) {
                return null;
            }
            return p -> p.copy(MLX_UINT8, MLX_UINT8, (int) bytes, v.ref(0), v.ref(1));
        });
        // number_of_elements(x[d0, d1, d2], axes (1, 2)) as int32.
        ROUTES.put("mlx_number_of_elements", v -> {
            if (!v.is(1, MLX_INT32, 1) || v.args() < 5) {
                return null;
            }
            long count = (long) v.intArg(3) * v.intArg(4);
            return p -> p.fill(MLX_INT32, 1, count, v.ref(1));
        });
        ROUTES.put("mlx_transpose_axes", v -> v.args() < 8 ? null : permute3(v, dims3(v, 2), new int[] { axis3(v.intArg(5)), axis3(v.intArg(6)), axis3(v.intArg(7)) }));
        ROUTES.put("mlx_swapaxes", v -> {
            if (v.args() < 7) {
                return null;
            }
            int[] perm = { 0, 1, 2 };
            int a = axis3(v.intArg(5));
            int b = axis3(v.intArg(6));
            if (a < 0 || a > 2 || b < 0 || b > 2) {
                return null;
            }
            perm[a] = b;
            perm[b] = a;
            return permute3(v, dims3(v, 2), perm);
        });
        ROUTES.put("mlx_moveaxis", v -> {
            if (v.args() < 7) {
                return null;
            }
            int source = axis3(v.intArg(5));
            int destination = axis3(v.intArg(6));
            if (source < 0 || source > 2 || destination < 0 || destination > 2) {
                return null;
            }
            java.util.List<Integer> order = new java.util.ArrayList<>(java.util.List.of(0, 1, 2));
            order.remove(Integer.valueOf(source));
            order.add(destination, source);
            return permute3(v, dims3(v, 2), new int[] { order.get(0), order.get(1), order.get(2) });
        });
        // broadcast_to(x[L], (s0, s1)): L == s1 or L == 1.
        ROUTES.put("mlx_broadcast_to", v -> {
            if (!v.isArray(0) || v.args() < 4) {
                return null;
            }
            int n = v.size(0);
            int s0 = v.intArg(2);
            int s1 = v.intArg(3);
            if (n != s1 && n != 1) {
                return null;
            }
            return view(v, 0, 1, new int[] { s0, s1 }, new long[] { 0, n == 1 ? 0 : 1 }, 0);
        });
        // broadcast_arrays(a[1, cols], b[rows, 1]) -> two [rows, cols] arrays.
        ROUTES.put("mlx_broadcast_arrays", v -> {
            if (v.args() < 6) {
                return null;
            }
            int rows = v.intArg(4);
            int cols = v.intArg(5);
            if (!v.isArray(0) || !v.isArray(1) || v.size(0) != cols || v.size(1) != rows) {
                return null;
            }
            Encoder first = view(v, 0, 2, new int[] { rows, cols }, new long[] { 0, 1 }, 0);
            Encoder second = view(v, 1, 3, new int[] { rows, cols }, new long[] { 1, 0 }, 0);
            return first == null || second == null ? null : p -> {
                first.encode(p);
                second.encode(p);
            };
        });
        ROUTES.put("mlx_as_strided", v -> {
            if (v.args() < 7 || !v.isArray(0)) {
                return null;
            }
            int[] shape = { v.intArg(2), v.intArg(3) };
            long[] strides = { v.intArg(4), v.intArg(5) };
            long offset = v.intArg(6);
            long last = offset + (shape[0] - 1L) * strides[0] + (shape[1] - 1L) * strides[1];
            if (offset < 0 || last >= v.size(0) || strides[0] < 0 || strides[1] < 0) {
                return null;
            }
            return view(v, 0, 1, shape, strides, offset);
        });
        ROUTES.put("mlx_concatenate", v -> {
            int t = pairType(v, 0, 2);
            if (t < 0 || !v.isArray(1) || v.dtype(1) != t || v.size(0) + v.size(1) != v.size(2)) {
                return null;
            }
            return p -> {
                p.copy(t, t, v.size(0), v.ref(0), v.ref(2));
                p.copy(t, t, v.size(1), v.ref(1), v.ref(2).plus(v.size(0), t));
            };
        });
        // concatenate_axis(a[r, ca], b[r, cb]) along axis 1.
        ROUTES.put("mlx_concatenate_axis", v -> {
            int t = pairType(v, 0, 2);
            if (t < 0 || v.args() < 6 || !v.isArray(1) || v.dtype(1) != t) {
                return null;
            }
            int r = v.intArg(3);
            int ca = v.intArg(4);
            int cb = v.intArg(5);
            if (v.size(0) != r * ca || v.size(1) != r * cb || v.size(2) != r * (ca + cb)) {
                return null;
            }
            return p -> {
                block(p, t, v.ref(0), ca, 0, v.ref(2), ca + cb, 0, r, ca);
                block(p, t, v.ref(1), cb, 0, v.ref(2), ca + cb, ca, r, cb);
            };
        });
        ROUTES.put("mlx_stack", v -> {
            int t = pairType(v, 0, 2);
            if (t < 0 || !v.isArray(1) || v.dtype(1) != t || v.size(0) != v.size(1) || v.size(2) != 2 * v.size(0)) {
                return null;
            }
            return p -> {
                p.copy(t, t, v.size(0), v.ref(0), v.ref(2));
                p.copy(t, t, v.size(1), v.ref(1), v.ref(2).plus(v.size(0), t));
            };
        });
        // stack_axis(a[n], b[n], axis 1) -> [n, 2].
        ROUTES.put("mlx_stack_axis", v -> {
            int t = pairType(v, 0, 2);
            if (t < 0 || !v.isArray(1) || v.dtype(1) != t || v.size(0) != v.size(1) || v.size(2) != 2 * v.size(0)) {
                return null;
            }
            int n = v.size(0);
            return p -> {
                p.generalCopy(t, t, new int[] { n }, new long[] { 1 }, v.ref(0), new long[] { 2 }, v.ref(2), null);
                p.generalCopy(t, t, new int[] { n }, new long[] { 1 }, v.ref(1), new long[] { 2 }, v.ref(2).plus(1, t), null);
            };
        });
        // split(x[r, c]) into two halves along axis 1; split_sections at column k.
        ROUTES.put("mlx_split", v -> v.args() < 5 ? null : splitColumns(v, v.intArg(3), v.intArg(4), v.intArg(4) / 2));
        ROUTES.put("mlx_split_sections", v -> v.args() < 6 ? null : splitColumns(v, v.intArg(3), v.intArg(4), v.intArg(5)));
        // repeat(x[n], reps): x read as [n, reps] with strides [1, 0].
        ROUTES.put("mlx_repeat", v -> !v.isArray(0) || v.args() < 3 ? null : view(v, 0, 1, new int[] { v.size(0), v.intArg(2) }, new long[] { 1, 0 }, 0));
        // repeat_axis(x[d0, d1], reps, axis 0): x read as [d0, reps, d1] with strides [d1, 0, 1].
        ROUTES.put("mlx_repeat_axis", v -> {
            if (v.args() < 5) {
                return null;
            }
            int d0 = v.intArg(2);
            int d1 = v.intArg(3);
            return view(v, 0, 1, new int[] { d0, v.intArg(4), d1 }, new long[] { d1, 0, 1 }, 0);
        });
        // tile(x[d0, d1], (r0, r1)): x read as [r0, d0, r1, d1] with strides [0, d1, 0, 1].
        ROUTES.put("mlx_tile", v -> {
            if (v.args() < 6) {
                return null;
            }
            int d0 = v.intArg(2);
            int d1 = v.intArg(3);
            return view(v, 0, 1, new int[] { v.intArg(4), d0, v.intArg(5), d1 }, new long[] { 0, d1, 0, 1 }, 0);
        });
        ROUTES.put("mlx_roll", v -> !v.isArray(0) || v.args() < 3 ? null : roll2(v, 1, v.size(0), 0, v.intArg(2)));
        ROUTES.put("mlx_roll_axis", v -> v.args() < 5 ? null : roll2(v, v.intArg(2), v.intArg(3), 0, v.intArg(4)));
        ROUTES.put("mlx_roll_axes", v -> v.args() < 6 ? null : roll2(v, v.intArg(2), v.intArg(3), v.intArg(4), v.intArg(5)));
        // pad(x[r, c], low (b0, b1), high (a0, a1), value): fill, then copy x into the interior.
        ROUTES.put("mlx_pad", v -> v.args() < 9 ? null : pad(v, v.intArg(2), v.intArg(3), v.intArg(4), v.intArg(5), v.intArg(6), v.intArg(7), v.floatArg(8)));
        ROUTES.put("mlx_pad_symmetric", v -> v.args() < 6 ? null : pad(v, v.intArg(2), v.intArg(3), v.intArg(4), v.intArg(4), v.intArg(4), v.intArg(4), v.floatArg(5)));
    }

    private static Encoder splitColumns(View v, int rows, int cols, int k) {
        int t = pairType(v, 0, 1);
        if (t < 0 || !v.isArray(2) || v.dtype(2) != t || k < 0 || k > cols || v.size(0) != rows * cols || v.size(1) != rows * k || v.size(2) != rows * (cols - k)) {
            return null;
        }
        return p -> {
            block(p, t, v.ref(0), cols, 0, v.ref(1), k, 0, rows, k);
            block(p, t, v.ref(0), cols, k, v.ref(2), cols - k, 0, rows, cols - k);
        };
    }

    private static Encoder pad(View v, int rows, int cols, int before0, int after0, int before1, int after1, double value) {
        int t = pairType(v, 0, 1);
        int outCols = cols + before1 + after1;
        int outRows = rows + before0 + after0;
        if (t < 0 || before0 < 0 || after0 < 0 || before1 < 0 || after1 < 0 || v.size(0) != rows * cols || v.size(1) != outRows * outCols) {
            return null;
        }
        return p -> {
            p.fill(t, outRows * outCols, value, v.ref(1));
            block(p, t, v.ref(0), cols, 0, v.ref(1), outCols, (long) before0 * outCols + before1, rows, cols);
        };
    }

    // ---------------------------------------------------------------- construction

    /** The single output array {@code out} (argument {@code index}) if its type is routable; -1 otherwise. */
    private static int outType(View v, int index, TypeSet allowed) {
        return v.isArray(index) && allowed.contains(v.dtype(index)) && v.size(index) > 0 ? v.dtype(index) : -1;
    }

    /** Fills a {@code rows x cols} matrix with zeros and its {@code k}-th diagonal from {@code src} (stride {@code srcStride}), or ones. */
    private static void diagonalFill(Program p, int t, Ref out, int rows, int cols, int k, Ref src, long srcStride) {
        p.fill(t, rows * cols, 0, out);
        int length = k >= 0 ? Math.min(rows, cols - k) : Math.min(rows + k, cols);
        if (length <= 0) {
            return;
        }
        long start = k >= 0 ? k : (long) -k * cols;
        Ref dst = out.plus(start, t);
        if (src == null) {
            p.generalCopy(t, t, new int[] { length }, new long[] { 0 }, null, new long[] { cols + 1 }, dst, scalarBytes(t, 1));
        } else {
            p.generalCopy(t, t, new int[] { length }, new long[] { srcStride }, src, new long[] { cols + 1 }, dst, null);
        }
    }

    /** MLX's tri(n, m, k) as a bool mask: arange(n)[:, None] >= arange(-k, m - k)[None, :]. */
    private static void triMask(Program p, int n, int m, int k, Ref mask) {
        Ref rows = p.scratch(4L * n);
        Ref cols = p.scratch(4L * m);
        p.arange(MLX_INT32, n, 0, 1, rows);
        p.arange(MLX_INT32, m, -k, 1, cols);
        p.generalBinary("GreaterEqual", MLX_INT32, new int[] { n, m }, new long[] { 1, 0 }, rows, new long[] { 0, 1 }, cols, mask);
    }

    /** A float32 window over arange(0, M): the kernel steps MLX's {@code ops.cpp} uses for it. */
    interface Window {
        void encode(Program p, int m, Ref n, Ref out);
    }

    private static Encoder window(View v, Window steps) {
        if (outType(v, 0, FLOAT32) < 0) {
            return null;
        }
        int m = v.size(0);
        return p -> {
            if (m == 1) {
                p.fill(MLX_FLOAT32, 1, 1, v.ref(0));
                return;
            }
            p.arange(MLX_FLOAT32, m, 0, 1, v.ref(0));
            steps.encode(p, m, v.ref(0), v.ref(0));
        };
    }

    private static void registerConstructionRoutes() {
        ROUTES.put("mlx_full", v -> {
            int t = outType(v, 0, NUMBERS);
            return t < 0 || v.args() < 2 ? null : p -> p.fill(t, v.size(0), v.floatArg(1), v.ref(0));
        });
        ROUTES.put("mlx_full_like", v -> {
            int t = outType(v, 1, NUMBERS);
            return t < 0 || v.args() < 3 || !v.isArray(0) || v.size(0) != v.size(1) ? null : p -> p.fill(t, v.size(1), v.floatArg(2), v.ref(1));
        });
        ROUTES.put("mlx_zeros", v -> fillRoute(v, 0, 0));
        ROUTES.put("mlx_ones", v -> fillRoute(v, 0, 1));
        ROUTES.put("mlx_zeros_like", v -> !v.isArray(0) || !v.isArray(1) || v.size(0) != v.size(1) ? null : fillRoute(v, 1, 0));
        ROUTES.put("mlx_ones_like", v -> !v.isArray(0) || !v.isArray(1) || v.size(0) != v.size(1) ? null : fillRoute(v, 1, 1));
        // arange(start, stop, step): MLX's arange kernel when the element count matches.
        ROUTES.put("mlx_arange", v -> {
            int t = outType(v, 0, NUMBERS);
            if (t < 0 || v.args() < 4) {
                return null;
            }
            double start = v.floatArg(1);
            double stop = v.floatArg(2);
            double step = v.floatArg(3);
            if (step == 0 || Double.isInfinite(step) || Math.max((int) Math.ceil((stop - start) / step), 0) != v.size(0)) {
                return null;
            }
            return p -> p.arange(t, v.size(0), start, step, v.ref(0));
        });
        // linspace(start, stop, num): t = arange(0, num) / (num - 1); (1 - t) * start + t * stop in float32.
        ROUTES.put("mlx_linspace", v -> {
            if (outType(v, 0, FLOATS) < 0 || v.args() < 3) {
                return null;
            }
            int t = v.dtype(0);
            int num = v.size(0);
            double start = v.floatArg(1);
            double stop = v.floatArg(2);
            return p -> {
                if (num == 1) {
                    p.fill(t, 1, (float) start, v.ref(0));
                    return;
                }
                Ref steps = p.scratch(4L * num);
                Ref complement = p.scratch(4L * num);
                p.arange(MLX_FLOAT32, num, 0, 1, steps);
                p.binaryScalarRight("Divide", MLX_FLOAT32, num, steps, num - 1, steps);
                p.binaryScalarLeft("Subtract", MLX_FLOAT32, num, 1, steps, complement);
                p.binaryScalarRight("Multiply", MLX_FLOAT32, num, complement, start, complement);
                p.binaryScalarRight("Multiply", MLX_FLOAT32, num, steps, stop, steps);
                if (t == MLX_FLOAT32) {
                    p.binary("Add", MLX_FLOAT32, num, complement, steps, v.ref(0));
                } else {
                    p.binary("Add", MLX_FLOAT32, num, complement, steps, complement);
                    p.copy(MLX_FLOAT32, t, num, complement, v.ref(0));
                }
            };
        });
        ROUTES.put("mlx_eye", v -> {
            int t = outType(v, 0, NUMBERS);
            if (t < 0 || v.args() < 4 || v.size(0) != v.intArg(1) * v.intArg(2)) {
                return null;
            }
            return p -> diagonalFill(p, t, v.ref(0), v.intArg(1), v.intArg(2), v.intArg(3), null, 0);
        });
        ROUTES.put("mlx_identity", v -> {
            int t = outType(v, 0, NUMBERS);
            if (t < 0 || v.args() < 2 || v.size(0) != v.intArg(1) * v.intArg(1)) {
                return null;
            }
            return p -> diagonalFill(p, t, v.ref(0), v.intArg(1), v.intArg(1), 0, null, 0);
        });
        ROUTES.put("mlx_tri", v -> {
            int t = outType(v, 0, NUMBERS);
            if (t < 0 || v.args() < 4 || v.size(0) != v.intArg(1) * v.intArg(2)) {
                return null;
            }
            int n = v.intArg(1);
            int m = v.intArg(2);
            int k = v.intArg(3);
            return p -> {
                Ref mask = p.scratch((long) n * m);
                triMask(p, n, m, k, mask);
                p.copy(MLX_BOOL, t, n * m, mask, v.ref(0));
            };
        });
        // tril: where(tri(r, c, k), x, 0); triu: where(tri(r, c, k - 1), 0, x).
        ROUTES.put("mlx_tril", v -> triangle(v, true));
        ROUTES.put("mlx_triu", v -> triangle(v, false));
        // diag(x[n], k): an (n + |k|)^2 zero matrix with x on its k-th diagonal.
        ROUTES.put("mlx_diag", v -> {
            int t = pairType(v, 0, 1);
            if (t < 0 || v.args() < 3) {
                return null;
            }
            int k = v.intArg(2);
            int n = v.size(0) + Math.abs(k);
            return v.size(1) != n * n ? null : p -> diagonalFill(p, t, v.ref(1), n, n, k, v.ref(0), 1);
        });
        // diagonal(x[r, c], k): x read along stride c + 1.
        ROUTES.put("mlx_diagonal", v -> {
            if (v.args() < 5) {
                return null;
            }
            int r = v.intArg(2);
            int c = v.intArg(3);
            int k = v.intArg(4);
            int length = Math.max(0, k >= 0 ? Math.min(r, c - k) : Math.min(r + k, c));
            long start = k >= 0 ? k : (long) -k * c;
            return length == 0 || !v.isArray(0) || v.size(0) != r * c ? null : view(v, 0, 1, new int[] { length }, new long[] { c + 1L }, start);
        });
        // meshgrid(x[nx], y[ny]): "xy" gives [ny, nx] grids, "ij" gives [nx, ny].
        ROUTES.put("mlx_meshgrid", v -> {
            if (!v.isArray(0) || !v.isArray(1) || v.args() < 5) {
                return null;
            }
            int nx = v.size(0);
            int ny = v.size(1);
            boolean ij = v.boolArg(4);
            int[] shape = ij ? new int[] { nx, ny } : new int[] { ny, nx };
            Encoder first = view(v, 0, 2, shape, ij ? new long[] { 1, 0 } : new long[] { 0, 1 }, 0);
            Encoder second = view(v, 1, 3, shape, ij ? new long[] { 0, 1 } : new long[] { 1, 0 }, 0);
            return first == null || second == null ? null : p -> {
                first.encode(p);
                second.encode(p);
            };
        });
        // Windows over n = arange(0, M) in float32, step by step as MLX's ops.cpp builds them.
        ROUTES.put("mlx_hanning", v -> window(v, (p, m, n, out) -> {
            p.binaryScalarLeft("Multiply", MLX_FLOAT32, m, (float) (Math.PI / (m - 1)), n, out);
            p.unary("Sin", MLX_FLOAT32, MLX_FLOAT32, m, out, out);
            p.unary("Square", MLX_FLOAT32, MLX_FLOAT32, m, out, out);
        }));
        ROUTES.put("mlx_hamming", v -> window(v, (p, m, n, out) -> {
            p.binaryScalarLeft("Multiply", MLX_FLOAT32, m, (float) ((2.0 * Math.PI) / (m - 1)), n, out);
            p.unary("Cos", MLX_FLOAT32, MLX_FLOAT32, m, out, out);
            p.binaryScalarLeft("Multiply", MLX_FLOAT32, m, 0.46f, out, out);
            p.binaryScalarLeft("Subtract", MLX_FLOAT32, m, 0.54f, out, out);
        }));
        ROUTES.put("mlx_blackman", v -> window(v, (p, m, n, out) -> {
            Ref term2 = p.scratch(4L * m);
            p.binaryScalarLeft("Multiply", MLX_FLOAT32, m, (float) ((2.0 * Math.PI) / (m - 1)), n, out);
            p.unary("Cos", MLX_FLOAT32, MLX_FLOAT32, m, out, out);
            p.unary("Square", MLX_FLOAT32, MLX_FLOAT32, m, out, term2);
            p.binaryScalarLeft("Multiply", MLX_FLOAT32, m, 0.16f, term2, term2);
            p.binaryScalarLeft("Multiply", MLX_FLOAT32, m, 0.5f, out, out);
            p.binaryScalarLeft("Subtract", MLX_FLOAT32, m, 0.34f, out, out);
            p.binary("Add", MLX_FLOAT32, m, out, term2, out);
        }));
        ROUTES.put("mlx_bartlett", v -> window(v, (p, m, n, out) -> {
            p.binaryScalarLeft("Multiply", MLX_FLOAT32, m, 2.0f / (m - 1), n, out);
            p.binaryScalarRight("Subtract", MLX_FLOAT32, m, out, 1.0f, out);
            p.unary("Abs", MLX_FLOAT32, MLX_FLOAT32, m, out, out);
            p.binaryScalarLeft("Subtract", MLX_FLOAT32, m, 1.0f, out, out);
        }));
    }

    private static Encoder fillRoute(View v, int index, double value) {
        int t = outType(v, index, NUMBERS);
        return t < 0 ? null : p -> p.fill(t, v.size(index), value, v.ref(index));
    }

    private static Encoder triangle(View v, boolean lower) {
        int t = pairType(v, 0, 1);
        if (t < 0 || v.args() < 5 || !NUMBERS.contains(t)) {
            return null;
        }
        int r = v.intArg(2);
        int c = v.intArg(3);
        int k = v.intArg(4);
        if (v.size(0) != r * c || v.size(1) != r * c) {
            return null;
        }
        return p -> {
            Ref mask = p.scratch((long) r * c);
            triMask(p, r, c, lower ? k : k - 1, mask);
            if (lower) {
                p.selectScalarRight(t, r * c, mask, v.ref(0), 0, v.ref(1));
            } else {
                p.selectScalar(t, r * c, mask, 0, v.ref(0), v.ref(1));
            }
        };
    }

    // ---------------------------------------------------------------- row kernels (mlx.fast, softmax)

    private static final int SIMD = 32;

    /** MLX's threadgroup size for its one-threadgroup-per-row kernels: enough simdgroups for {@code axis / nReads}, or all threads when looped. */
    private static long rowGroup(int axis, int nReads, int loopedLimit, long maxThreads) {
        if (axis > loopedLimit) {
            return maxThreads;
        }
        long needed = (axis + nReads - 1) / nReads;
        return SIMD * ((needed + SIMD - 1) / SIMD);
    }

    /** softmax over the last axis of {@code rows x axis}, with {@code precise} accumulation for half types (as the provider asks). */
    private static Encoder softmax(View v, int rows, int axis) {
        int t = v.sameType(FLOATS, 0, 1);
        if (t < 0 || rows <= 0 || axis <= 0 || (long) rows * axis != v.size(0)) {
            return null;
        }
        String name = (axis > 4096 ? "looped_" : "block_") + "softmax_" + (t != MLX_FLOAT32 ? "precise_" : "") + MlxMetalKernels.typeName(t);
        return p -> {
            Program.Launch k = p.launch(name).buffer(0, v.ref(0)).buffer(1, v.ref(1)).i32(2, axis);
            long group = rowGroup(axis, 4, 4096, k.maxThreads);
            k.threads(rows * group, group, 1, 1, 1, 1);
        };
    }

    /** RoPE over x[B, H, T, D] (contiguous) with a static offset (as bytes) or an offset array. */
    private static Encoder rope(View v, int xIndex, int outIndex, int offsetIndex, int first) {
        int t = pairType(v, xIndex, outIndex);
        if (t < 0 || !FLOATS.contains(t) || v.args() < first + 8) {
            return null;
        }
        int b = v.intArg(first);
        int h = v.intArg(first + 1);
        int seq = v.intArg(first + 2);
        int d = v.intArg(first + 3);
        int dims = v.intArg(first + 4);
        boolean traditional = v.boolArg(first + 5);
        float base = (float) (Math.log(v.floatArg(first + 6)) / Math.log(2.0));
        float scale = v.floatArg(first + 7);
        boolean staticOffset = offsetIndex < 0;
        if ((long) b * h * seq * d != v.size(xIndex) || v.size(outIndex) != v.size(xIndex) || dims <= 0 || dims > d || dims % 2 != 0
                || (!staticOffset && !v.is(offsetIndex, MLX_INT32, 1))) {
            return null;
        }
        int offset = staticOffset ? v.intArg(first + 8) : 0;
        long mat = (long) seq * d;
        boolean single = seq == 1;
        String name = "rope_" + (single ? "single_" : "") + MlxMetalKernels.typeName(t);
        return p -> {
            Ref in = v.ref(xIndex);
            if (dims < d) {
                // MLX copies x to the output and rotates the first dims of each row in place.
                p.copy(t, t, v.size(xIndex), in, v.ref(outIndex));
                in = v.ref(outIndex);
            }
            Program.Launch k = p.launch(name, true, traditional, false).buffer(0, in).buffer(1, v.ref(outIndex));
            if (staticOffset) {
                k.i32(2, offset);
            } else {
                k.buffer(2, v.ref(offsetIndex));
            }
            k.f32(3, scale).f32(10, base);
            if (single) {
                k.i64(4, mat).blocks(dims / 2, (long) b * h, 1);
            } else {
                k.i64(4, mat, d, 1).i64(5, mat, d, 1).i64(6, 0).i32(7, h).blocks(dims / 2, seq, (long) b * ((h + 3) / 4));
            }
        };
    }

    private static void registerRowRoutes() {
        ROUTES.put("mlx_softmax", v -> v.isArray(0) ? softmax(v, 1, v.size(0)) : null);
        ROUTES.put("mlx_softmax_axis", v -> v.args() < 4 ? null : softmax(v, v.intArg(2), v.intArg(3)));
        // rms_norm(x[rows, dim], w[dim], eps).
        ROUTES.put("mlx_fast_rms_norm", v -> {
            int t = v.sameType(FLOATS, 0, 2);
            if (t < 0 || v.args() < 6 || !v.isArray(1) || v.dtype(1) != t) {
                return null;
            }
            int rows = v.intArg(3);
            int dim = v.intArg(4);
            float eps = v.floatArg(5);
            if ((long) rows * dim != v.size(0) || v.size(1) != dim) {
                return null;
            }
            String name = "rms" + (dim > 4096 ? "_looped" : "") + MlxMetalKernels.typeName(t);
            return p -> {
                Program.Launch k = p.launch(name).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2)).f32(3, eps).i32(4, dim).i32(5, 1);
                long group = rowGroup(dim, 4, 4096, k.maxThreads);
                k.threads(rows * group, group, 1, 1, 1, 1);
            };
        });
        // layer_norm(x[rows, dim], w[dim], b[dim], eps).
        ROUTES.put("mlx_fast_layer_norm", v -> {
            int t = v.sameType(FLOATS, 0, 3);
            if (t < 0 || v.args() < 7 || !v.is(1, t, v.intArg(5)) || !v.is(2, t, v.intArg(5))) {
                return null;
            }
            int rows = v.intArg(4);
            int dim = v.intArg(5);
            float eps = v.floatArg(6);
            if ((long) rows * dim != v.size(0)) {
                return null;
            }
            boolean looped = dim > 6656;
            String name = "layer_norm" + (looped ? "_looped" : "") + MlxMetalKernels.typeName(t);
            return p -> {
                Program.Launch k = p.launch(name).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2)).buffer(3, v.ref(3)).f32(4, eps).i32(5, dim).i32(6, 1).i32(7, 1);
                long group = rowGroup(dim, looped ? 4 : 8, 6656, k.maxThreads);
                k.threads(rows * group, group, 1, 1, 1, 1);
            };
        });
        // rope(x, out, B, H, T, D, dims, traditional, base, scale, offset); rope_dynamic(x, offset[1], out, B, H, T, D, dims, traditional, base, scale).
        ROUTES.put("mlx_fast_rope", v -> rope(v, 0, 1, -1, 2));
        ROUTES.put("mlx_fast_rope_dynamic", v -> rope(v, 0, 2, 1, 3));
    }

    // ---------------------------------------------------------------- reductions

    private static final int REDUCE_N_READS = 4;
    private static final int REDUCE_N_WRITES = 4;

    /** MLX's remap_reduce_types: the type the kernel reads and the type it accumulates into. */
    private static int[] reduceTypes(int in, String op) {
        boolean integer = INTEGERS.contains(in);
        if (op.equals("sum") || op.equals("prod")) {
            if (in == MLX_BOOL) {
                return new int[] { MlxMetalKernels.MLX_INT8, MLX_INT32 };
            }
            if (integer) {
                return switch (in) {
                    case MLX_UINT8, MlxMetalKernels.MLX_UINT16, MlxMetalKernels.MLX_UINT32 -> new int[] { in, MlxMetalKernels.MLX_UINT32 };
                    case MlxMetalKernels.MLX_INT64 -> new int[] { in, in };
                    default -> new int[] { in, MLX_INT32 };
                };
            }
            return new int[] { in, in };
        }
        if (op.equals("and") || op.equals("or")) {
            if (FLOATS.contains(in)) {
                return new int[] { in, MLX_BOOL };
            }
            return switch (MlxMetalKernels.itemSize(in)) {
                case 1 -> new int[] { MLX_BOOL, MLX_BOOL };
                case 2 -> new int[] { MlxMetalKernels.MLX_INT16, MLX_BOOL };
                case 4 -> new int[] { MLX_INT32, MLX_BOOL };
                default -> new int[] { MlxMetalKernels.MLX_INT64, MLX_BOOL };
            };
        }
        return new int[] { in, in };
    }

    private static long roundUp32(long n) {
        return (n + 31) / 32 * 32;
    }

    /** MLX's all_reduce over {@code n} contiguous elements into {@code out[0]}. */
    private static void allReduce(Program p, String op, int in, long n, Ref x, Ref out) {
        int[] types = reduceTypes(in, op);
        String name = "all_reduce_" + op + MlxMetalKernels.typeName(types[0]);
        if (n <= REDUCE_N_READS * 1024L) {
            long group = roundUp32((n + REDUCE_N_READS - 1) / REDUCE_N_READS);
            p.launch(name).buffer(0, x).buffer(1, out).i64(2, n).i64(3, n).threads(group, group, 1, 1, 1, 1);
            return;
        }
        boolean huge = n * MlxMetalKernels.itemSize(in) > (1L << 26);
        int rows = huge ? 1024 * REDUCE_N_READS : 32 * REDUCE_N_READS;
        long secondGroup = huge ? 1024 : 32;
        Ref partial = p.scratch((long) rows * MlxMetalKernels.itemSize(types[1]));
        long rowSize = (n + rows - 1) / rows;
        long group = roundUp32(Math.min((rowSize + REDUCE_N_READS - 1) / REDUCE_N_READS, 1024));
        p.launch(name).buffer(0, x).buffer(1, partial).i64(2, n).i64(3, rowSize).threads(group, group, rows, 1, 1, 1);
        p.launch("all_reduce_" + op + MlxMetalKernels.typeName(types[1])).buffer(0, partial).buffer(1, out).i64(2, rows).i64(3, rows).threads(secondGroup, secondGroup, 1, 1, 1, 1);
    }

    /** MLX's threadgroup_size_from_row_size. */
    private static long rowReduceGroup(long rowSize) {
        if (rowSize <= 512) {
            return 32;
        }
        if (rowSize <= 1024) {
            return 128;
        }
        return Math.min(1024, roundUp32((rowSize + REDUCE_N_READS - 1) / REDUCE_N_READS));
    }

    /** MLX's row reduce over {@code rows} contiguous rows of {@code rowSize}, with no other reduced axes, into {@code out[rows]}. */
    private static void rowReduce(Program p, String op, int in, long rows, long rowSize, Ref x, Ref out) {
        int[] types = reduceTypes(in, op);
        String type = MlxMetalKernels.typeName(types[0]);
        if (rowSize <= 64) {
            // row_reduce_small with no non-row reductions: one thread per output.
            Program.Launch k = p.launch("row_reduce_small_1_reduce_" + op + type).buffer(0, x).buffer(1, out);
            rowArgs(k, rows, rowSize);
            k.threads(rows, Math.min(rows, 1024), 1, 1, 1, 1);
        } else if (rows >= 32) {
            long group = rowReduceGroup(rowSize);
            if (MlxMetalKernels.itemSize(types[0]) == 8) {
                group = Math.min(group, 512);
            }
            long width = (rows + REDUCE_N_WRITES - 1) / REDUCE_N_WRITES;
            p.launch("row_reduce_simple_" + op + type).buffer(0, x).buffer(1, out).i64(2, rowSize).i64(3, rows).threads(group, group, width, 1, 1, 1);
        } else {
            long group = rowReduceGroup(rowSize);
            Program.Launch k = p.launch("row_reduce_looped_1_reduce_" + op + type).buffer(0, x).buffer(1, out);
            rowArgs(k, rows, rowSize);
            k.threads(group, group, rows, 1, 1, 1);
        }
    }

    /** RowReduceArgs.encode for contiguous rows: one non-reduced dimension, no extra reduced axes. */
    private static void rowArgs(Program.Launch k, long rows, long rowSize) {
        k.i64(2, rowSize).i64(3, 1).i32(4, (int) rows).i64(5, rowSize).i32(6, 1).i32(7, 0).i64(8, 0).i32(9, 0);
    }

    /** The reduced extent of a reduce call: whole array (null), or rows x len when the reduced axes are trailing and contiguous. */
    private record Extent(long rows, long len, boolean whole) {
    }

    /** reduce(x, out): whole; reduce_axis(x, out, outer, len, inner); reduce_axes(x, out, outer, l1, l2, inner). Only inner == 1 routes. */
    private static Extent extent(View v, String form) {
        if (!v.isArray(0)) {
            return null;
        }
        long n = v.size(0);
        switch (form) {
            case "whole":
                return new Extent(1, n, true);
            case "axis":
                if (v.args() < 5 || v.intArg(4) != 1 || (long) v.intArg(2) * v.intArg(3) != n) {
                    return null;
                }
                return new Extent(v.intArg(2), v.intArg(3), false);
            default:
                if (v.args() < 6 || v.intArg(5) != 1 || (long) v.intArg(2) * v.intArg(3) * v.intArg(4) != n) {
                    return null;
                }
                return new Extent(v.intArg(2), (long) v.intArg(3) * v.intArg(4), false);
        }
    }

    /** The reduction itself: all_reduce for a whole array, row reduce otherwise. */
    private static void reduceInto(Program p, String op, int in, Extent e, Ref x, Ref out) {
        if (e.whole()) {
            allReduce(p, op, in, e.len(), x, out);
        } else {
            rowReduce(p, op, in, e.rows(), e.len(), x, out);
        }
    }

    /** sum, prod, max, min, all (and), any (or): output type as MLX gives it. */
    private static Encoder plainReduce(View v, String op, String form) {
        Extent e = extent(v, form);
        if (e == null || !v.isArray(1) || v.size(1) != e.rows() || !ANY.contains(v.dtype(0)) || v.dtype(0) == MLX_COMPLEX64) {
            return null;
        }
        int in = v.dtype(0);
        boolean logical = op.equals("and") || op.equals("or");
        String kernelOp = !logical && in == MLX_BOOL ? (op.equals("min") ? "and" : op.equals("max") ? "or" : op) : op;
        int accumulated = reduceTypes(in, kernelOp)[1];
        int outType = v.dtype(1);
        boolean boolOut = accumulated == MLX_BOOL;
        if (boolOut ? outType != MLX_UINT8 : outType != accumulated) {
            return null;
        }
        return p -> {
            if (boolOut) {
                // MLX's reduce kernels need at least 4 bytes of output; reduce into scratch and copy the bools.
                Ref bools = p.scratch(Math.max(4, e.rows()));
                reduceInto(p, kernelOp, in, e, v.ref(0), bools);
                p.copy(MLX_BOOL, MLX_BOOL, (int) e.rows(), bools, v.ref(1));
            } else {
                reduceInto(p, kernelOp, in, e, v.ref(0), v.ref(1));
            }
        };
    }

    /** static_cast of a double into MLX dtype {@code t}, as NumberOfElements stores it, read back as a double. */
    private static double castTo(int t, double value) {
        return switch (t) {
            case MLX_FLOAT32 -> (float) value;
            case MlxMetalKernels.MLX_FLOAT16 -> Float.float16ToFloat(Float.floatToFloat16((float) value));
            default -> Float.intBitsToFloat((Float.floatToRawIntBits((float) value) + 0x7fff + ((Float.floatToRawIntBits((float) value) >>> 16) & 1)) & 0xffff0000);
        };
    }

    /** mean: sum, then multiply by number_of_elements(inverted) as a scalar of the output type (floating inputs). */
    private static Encoder mean(View v, String form) {
        Extent e = extent(v, form);
        int t = v.isArray(0) ? v.dtype(0) : -1;
        if (e == null || !FLOATS.contains(t) || !v.is(1, t, (int) e.rows())) {
            return null;
        }
        double inverse = 1.0 / e.len();
        return p -> {
            reduceInto(p, "sum", t, e, v.ref(0), v.ref(1));
            p.binaryScalarRight("Multiply", t, (int) e.rows(), v.ref(1), inverse, v.ref(1));
        };
    }

    /** var(ddof) and std: mean with keepdims, broadcast subtract, square, sum, and the normaliser MLX uses. */
    private static Encoder variance(View v, String form, int ddofIndex, boolean std) {
        Extent e = extent(v, form);
        int t = v.isArray(0) ? v.dtype(0) : -1;
        if (e == null || !FLOATS.contains(t) || !v.is(1, t, (int) e.rows()) || v.args() <= ddofIndex) {
            return null;
        }
        int ddof = v.intArg(ddofIndex);
        long rows = e.rows();
        long len = e.len();
        int n = v.size(0);
        return p -> {
            int item = MlxMetalKernels.itemSize(t);
            Ref mu = p.scratch(Math.max(4, rows * item));
            Ref d = p.scratch((long) n * item);
            reduceInto(p, "sum", t, e, v.ref(0), mu);
            p.binaryScalarRight("Multiply", t, (int) rows, mu, 1.0 / len, mu);
            if (e.whole()) {
                p.binaryScalarRight("Subtract", t, n, v.ref(0), mu, d);
            } else {
                p.generalBinary("Subtract", t, new int[] { (int) rows, (int) len }, new long[] { len, 1 }, v.ref(0), new long[] { 1, 0 }, mu, d);
            }
            p.unary("Square", t, t, n, d, d);
            reduceInto(p, "sum", t, e, d, v.ref(1));
            if (ddof == 0) {
                p.binaryScalarRight("Multiply", t, (int) rows, v.ref(1), 1.0 / len, v.ref(1));
            } else {
                // maximum(number_of_elements - ddof, 0), each step rounded to the output type.
                double normaliser = Math.max(castTo(t, castTo(t, len) - castTo(t, ddof)), 0.0);
                p.binaryScalarRight("Divide", t, (int) rows, v.ref(1), normaliser, v.ref(1));
            }
            if (std) {
                p.unary("Sqrt", t, t, (int) rows, v.ref(1), v.ref(1));
            }
        };
    }

    /**
     * logsumexp of a whole array: MLX's fused LogSumExp kernel. (The axis forms reduce an inner axis
     * of [outer, len, inner], for which MLX builds a composite instead, so they are not routed.)
     */
    private static Encoder logsumexp(View v, String form) {
        Extent e = extent(v, form);
        int t = v.isArray(0) ? v.dtype(0) : -1;
        if (e == null || !FLOATS.contains(t) || !v.is(1, t, (int) e.rows())) {
            return null;
        }
        int axis = (int) e.len();
        String name = (axis > 4096 ? "looped_" : "block_") + "logsumexp_" + MlxMetalKernels.typeName(t);
        return p -> {
            Program.Launch k = p.launch(name).buffer(0, v.ref(0)).buffer(1, v.ref(1)).i32(2, axis);
            long group = rowGroup(axis, 4, 4096, k.maxThreads);
            k.threads(e.rows() * group, group, 1, 1, 1, 1);
        };
    }

    /** argmin/argmax over an axis of x viewed as [outer, len, inner] (uint32 indices into an int array). */
    private static Encoder argReduce(View v, String op, long outer, long len, long inner) {
        if (!v.isArray(0) || !v.is(1, MLX_INT32, (int) (outer * inner)) || outer * len * inner != v.size(0) || !NUMBERS.contains(v.dtype(0))) {
            return null;
        }
        int t = v.dtype(0);
        return p -> argReduceInto(p, op, t, outer, len, inner, v.ref(0), v.ref(1));
    }

    /** MLX's ArgReduce over axis 1 of x viewed as [outer, len, inner], writing uint32 indices. */
    private static void argReduceInto(Program p, String op, int t, long outer, long len, long inner, Ref x, Ref out) {
        Program.Launch k = p.launch(op + "_" + MlxMetalKernels.typeName(t)).buffer(0, x).buffer(1, out);
        long group = roundUp32(Math.min((len + 3) / 4, k.maxThreads));
        boolean scalar = outer * inner == 1;
        if (scalar) {
            k.i32(2, 0).i64(3, 0).i64(4, 0).i64(5, 0);
        } else {
            k.bytes(2, MlxMetalKernels.intBytes(new int[] { (int) outer, (int) inner })).i64(3, len * inner, 1).i64(4, inner, 1).i64(5, 2);
        }
        k.i64(6, inner).i64(7, len).threads(group, group, outer * inner, 1, 1, 1);
    }

    private static void registerReductionRoutes() {
        String[][] plain = { { "sum", "sum" }, { "prod", "prod" }, { "max", "max" }, { "min", "min" }, { "all", "and" }, { "any", "or" } };
        for (String[] r : plain) {
            ROUTES.put("mlx_" + r[0], v -> plainReduce(v, r[1], "whole"));
            ROUTES.put("mlx_" + r[0] + "_axis", v -> plainReduce(v, r[1], "axis"));
            ROUTES.put("mlx_" + r[0] + "_axes", v -> plainReduce(v, r[1], "axes"));
        }
        ROUTES.put("mlx_mean", v -> mean(v, "whole"));
        ROUTES.put("mlx_mean_axis", v -> mean(v, "axis"));
        ROUTES.put("mlx_mean_axes", v -> mean(v, "axes"));
        ROUTES.put("mlx_var", v -> variance(v, "whole", 2, false));
        ROUTES.put("mlx_var_axis", v -> variance(v, "axis", 5, false));
        ROUTES.put("mlx_var_axes", v -> variance(v, "axes", 6, false));
        ROUTES.put("mlx_std", v -> variance(v, "whole", 2, true));
        ROUTES.put("mlx_std_axis", v -> variance(v, "axis", 5, true));
        ROUTES.put("mlx_std_axes", v -> variance(v, "axes", 6, true));
        ROUTES.put("mlx_logsumexp", v -> logsumexp(v, "whole"));
        ROUTES.put("mlx_argmin", v -> v.isArray(0) ? argReduce(v, "argmin", 1, v.size(0), 1) : null);
        ROUTES.put("mlx_argmin_axis", v -> v.args() < 5 ? null : argReduce(v, "argmin", v.intArg(2), v.intArg(3), v.intArg(4)));
        ROUTES.put("mlx_argmax", v -> v.isArray(0) ? argReduce(v, "argmax", 1, v.size(0), 1) : null);
        ROUTES.put("mlx_argmax_axis", v -> v.args() < 4 ? null : argReduce(v, "argmax", v.intArg(2), v.intArg(3), 1));
    }

    // ---------------------------------------------------------------- slices and reduction composites

    /** slice(x[rows, cols], [r0:r1:rs, c0:c1:cs]) with positive steps. */
    private static Encoder slice(View v) {
        if (v.args() < 10 || !v.isArray(0)) {
            return null;
        }
        int rows = v.intArg(2);
        int cols = v.intArg(3);
        int r0 = v.intArg(4);
        int r1 = v.intArg(5);
        int rs = v.intArg(6);
        int c0 = v.intArg(7);
        int c1 = v.intArg(8);
        int cs = v.intArg(9);
        if (rs <= 0 || cs <= 0 || r0 < 0 || c0 < 0 || r1 > rows || c1 > cols || r0 >= r1 || c0 >= c1 || v.size(0) != rows * cols) {
            return null;
        }
        int outRows = (r1 - r0 + rs - 1) / rs;
        int outCols = (c1 - c0 + cs - 1) / cs;
        return view(v, 0, 1, new int[] { outRows, outCols }, new long[] { (long) rs * cols, cs }, (long) r0 * cols + c0);
    }

    /** slice_update(x[rows, cols], update[ur, uc] at (r0, c0)): copy x, then write (or add / multiply) the update into its region. */
    private static Encoder sliceUpdate(View v, String op) {
        int t = pairType(v, 0, 2);
        if (t < 0 || v.args() < 9 || !v.isArray(1) || v.dtype(1) != t) {
            return null;
        }
        int rows = v.intArg(3);
        int cols = v.intArg(4);
        int r0 = v.intArg(5);
        int c0 = v.intArg(6);
        int ur = v.intArg(7);
        int uc = v.intArg(8);
        if (r0 < 0 || c0 < 0 || r0 + ur > rows || c0 + uc > cols || ur <= 0 || uc <= 0 || v.size(0) != rows * cols || v.size(2) != rows * cols || v.size(1) != ur * uc) {
            return null;
        }
        long offset = (long) r0 * cols + c0;
        return p -> {
            p.copy(t, t, rows * cols, v.ref(0), v.ref(2));
            if (op == null) {
                block(p, t, v.ref(1), uc, 0, v.ref(2), cols, offset, ur, uc);
            } else {
                // out[region] = op(x[region], update), computed contiguously and written back.
                Ref combined = p.scratch((long) ur * uc * MlxMetalKernels.itemSize(t));
                p.generalBinary(op, t, new int[] { ur, uc }, new long[] { cols, 1 }, v.ref(0).plus(offset, t), new long[] { uc, 1 }, v.ref(1), combined);
                block(p, t, combined, uc, 0, v.ref(2), cols, offset, ur, uc);
            }
        };
    }

    /** softmax over the last two axes of x[d0, d1, d2] (precise): MLX's composite with max, exp, sum and divide over float32. */
    private static Encoder softmaxLastTwo(View v) {
        int t = v.sameType(FLOATS, 0, 1);
        if (t < 0 || v.args() < 5) {
            return null;
        }
        int d0 = v.intArg(2);
        int d1 = v.intArg(3);
        int d2 = v.intArg(4);
        if ((long) d0 * d1 * d2 != v.size(0)) {
            return null;
        }
        if (d1 == 1) {
            return softmax(v, d0, d2);
        }
        int len = d1 * d2;
        int n = d0 * len;
        int f = MLX_FLOAT32;
        return p -> {
            Ref in = v.ref(0);
            if (t != f) {
                Ref cast = p.scratch(4L * n);
                p.copy(t, f, n, in, cast);
                in = cast;
            }
            Ref max = p.scratch(4L * Math.max(1, d0));
            Ref ex = p.scratch(4L * n);
            Ref sum = p.scratch(4L * Math.max(1, d0));
            rowReduce(p, "max", f, d0, len, in, max);
            p.generalBinary("Subtract", f, new int[] { d0, len }, new long[] { len, 1 }, in, new long[] { 1, 0 }, max, ex);
            p.unary("Exp", f, f, n, ex, ex);
            rowReduce(p, "sum", f, d0, len, ex, sum);
            Ref target = t == f ? v.ref(1) : ex;
            p.generalBinary("Divide", f, new int[] { d0, len }, new long[] { len, 1 }, ex, new long[] { 1, 0 }, sum, target);
            if (t != f) {
                p.copy(f, t, n, ex, v.ref(1));
            }
        };
    }

    /** logsumexp over an inner axis (or two): max, exp(x - max), sum, log, add max, and max where it is infinite. */
    private static Encoder logsumexpComposite(View v, String form) {
        Extent e = extent(v, form);
        int t = v.isArray(0) ? v.dtype(0) : -1;
        if (e == null || !FLOATS.contains(t) || !v.is(1, t, (int) e.rows())) {
            return null;
        }
        long rows = e.rows();
        long len = e.len();
        int n = v.size(0);
        return p -> {
            int item = MlxMetalKernels.itemSize(t);
            Ref max = p.scratch(Math.max(4, rows * item));
            Ref ex = p.scratch((long) n * item);
            Ref mask = p.scratch(Math.max(4, rows));
            Ref magnitude = p.scratch(Math.max(4, rows * item));
            rowReduce(p, "max", t, rows, len, v.ref(0), max);
            p.generalBinary("Subtract", t, new int[] { (int) rows, (int) len }, new long[] { len, 1 }, v.ref(0), new long[] { 1, 0 }, max, ex);
            p.unary("Exp", t, t, n, ex, ex);
            rowReduce(p, "sum", t, rows, len, ex, v.ref(1));
            p.unary("Log", t, t, (int) rows, v.ref(1), v.ref(1));
            p.binary("Add", t, (int) rows, v.ref(1), max, v.ref(1));
            p.unary("Abs", t, t, (int) rows, max, magnitude);
            p.binaryScalarRight("Equal", t, (int) rows, magnitude, INF, mask);
            p.select(t, (int) rows, mask, max, v.ref(1), v.ref(1));
        };
    }

    private static void registerCompositeRoutes() {
        ROUTES.put("mlx_slice", MlxKernelRoutes::slice);
        ROUTES.put("mlx_slice_update", v -> sliceUpdate(v, null));
        ROUTES.put("mlx_slice_update_add", v -> sliceUpdate(v, "Add"));
        ROUTES.put("mlx_slice_update_prod", v -> sliceUpdate(v, "Multiply"));
        // trace(x[r, c], k): sum(astype(diagonal(x, k), dtype)) as an all-reduce of the contiguous diagonal.
        ROUTES.put("mlx_trace", v -> {
            if (v.args() < 5 || !v.isArray(0) || !v.isArray(1) || v.size(1) != 1 || !NUMBERS.contains(v.dtype(0)) || !NUMBERS.contains(v.dtype(1))) {
                return null;
            }
            int r = v.intArg(2);
            int c = v.intArg(3);
            int k = v.intArg(4);
            int length = Math.max(0, k >= 0 ? Math.min(r, c - k) : Math.min(r + k, c));
            int in = v.dtype(0);
            int out = v.dtype(1);
            if (length == 0 || v.size(0) != r * c || reduceTypes(out, "sum")[1] != out) {
                return null;
            }
            long start = k >= 0 ? k : (long) -k * c;
            return p -> {
                Ref diagonal = p.scratch((long) length * MlxMetalKernels.itemSize(out));
                p.generalCopy(in, out, new int[] { length }, new long[] { c + 1L }, v.ref(0).plus(start, in), null, diagonal, null);
                allReduce(p, "sum", out, length, diagonal, v.ref(1));
            };
        });
        // allclose: all(isclose(...)) (float32, as isclose).
        ROUTES.put("mlx_allclose", v -> {
            if (v.args() < 6 || v.sameType(FLOAT32, 0, 1) < 0 || !v.is(2, MLX_UINT8, 1)) {
                return null;
            }
            int n = v.size(0);
            double rtol = v.floatArg(3);
            double atol = v.floatArg(4);
            boolean equalNan = v.boolArg(5);
            return p -> {
                Ref flags = p.scratch(n);
                Ref result = p.scratch(4);
                iscloseKernels(p, n, v.ref(0), v.ref(1), flags, rtol, atol, equalNan);
                allReduce(p, "and", MLX_BOOL, n, flags, result);
                p.copy(MLX_BOOL, MLX_BOOL, 1, result, v.ref(2));
            };
        });
        // array_equal: all(equal(a, b)), with NaNEqual when equal_nan is set on floating types.
        ROUTES.put("mlx_array_equal", v -> {
            int t = v.sameType(NUMBERS, 0, 1);
            if (t < 0 || v.args() < 4 || !v.is(2, MLX_UINT8, 1)) {
                return null;
            }
            int n = v.size(0);
            String op = v.boolArg(3) && FLOATS.contains(t) ? "NaNEqual" : "Equal";
            return p -> {
                Ref flags = p.scratch(n);
                Ref result = p.scratch(4);
                p.binary(op, t, n, v.ref(0), v.ref(1), flags);
                allReduce(p, "and", MLX_BOOL, n, flags, result);
                p.copy(MLX_BOOL, MLX_BOOL, 1, result, v.ref(2));
            };
        });
        ROUTES.put("mlx_softmax_axes", MlxKernelRoutes::softmaxLastTwo);
        ROUTES.put("mlx_logsumexp_axis", v -> logsumexpComposite(v, "axis"));
        ROUTES.put("mlx_logsumexp_axes", v -> logsumexpComposite(v, "axes"));
    }

    // ---------------------------------------------------------------- matmul (GEMV and steel GEMM)

    /** GEMMParams: M, N, K, lda, ldb, ldd, tiles_n, tiles_m, three int64 batch strides, swizzle_log, k iterations, batch_ndim. */
    private static byte[] gemmParams(int m, int n, int k, int lda, int ldb, int ldd, int tilesN, int tilesM, long strideA, long strideB, long strideD, int kIterations) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(72).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(m).putInt(n).putInt(k).putInt(lda).putInt(ldb).putInt(ldd).putInt(tilesN).putInt(tilesM);
        b.putLong(strideA).putLong(strideB).putLong(strideD);
        b.putInt(0).putInt(kIterations).putInt(1);
        return b.array();
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    /** One (batched) matmul as MLX's Matmul / AddMM sees it: out[b] = alpha * a[b] @ op(b[b]) + beta * c. */
    private record Mm(int type, int batch, int m, int k, int n, boolean bTransposed, Ref a, Ref b, Ref out, Ref c, float alpha, float beta) {
    }

    /**
     * a[batch, m, k] @ b for contiguous a, with b [batch, k, n] contiguous ({@code bTransposed} false,
     * ldb = n) or the transpose of w[n, k] ({@code bTransposed} true, ldb = k), optionally with an
     * addmm source c [m, n]: MLX's GEMV, split-K or regular steel GEMM exactly as Matmul::eval_gpu and
     * AddMM::eval_gpu pick them on GPUs before generation 15. Returns null where MLX would do otherwise.
     */
    private static Encoder matmulKernels(Mm mm, String architecture) {
        int t = mm.type();
        int batch = mm.batch();
        int m = mm.m();
        int k = mm.k();
        int n = mm.n();
        boolean addmm = mm.c() != null;
        if (architecture == null || architecture.isEmpty() || MlxMetalKernels.architectureGeneration(architecture) >= 15 || (m == 1 && n == 1) || m <= 0 || k <= 0 || n <= 0
                || (addmm && batch != 1) || (mm.bTransposed() && batch != 1)) {
            return null;
        }
        char devc = architecture.charAt(architecture.length() - 1);
        String type = MlxMetalKernels.typeName(t);
        int lda = k;
        int ldb = mm.bTransposed() ? k : n;
        long strideA = batch > 1 ? (long) m * k : 0;
        long strideB = batch > 1 ? (long) k * n : 0;
        Ref a = mm.a();
        Ref b = mm.b();
        Ref out = mm.out();
        Ref c = mm.c();
        float alpha = mm.alpha();
        float beta = mm.beta();
        if (Math.min(m, n) == 1) {
            // gemv_axbpy: the matrix is b when n != 1.
            boolean bMatrix = n != 1;
            boolean transposeMat = bMatrix && !mm.bTransposed();
            int outLen = bMatrix ? n : m;
            int matLd = bMatrix ? ldb : lda;
            int tm = 4;
            int tn = 4;
            int sm = 1;
            int sn = 32;
            int bm = 1;
            int bn = 1;
            int perGroup;
            String name;
            if (transposeMat) {
                if (k >= 8192 && outLen >= 2048) {
                    sm = 4;
                    sn = 8;
                } else {
                    sm = 8;
                    sn = 4;
                }
                bn = outLen >= 2048 ? 16 : outLen >= 512 ? 4 : 2;
                tn = outLen < tn ? 1 : tn;
                perGroup = bn * sn * tn;
                name = "gemv_t_" + type;
            } else {
                bm = outLen >= 4096 ? 8 : 4;
                sn = 32;
                if (k <= 64) {
                    bm = 1;
                    sm = 8;
                    sn = 4;
                } else if (k >= 16 * outLen) {
                    bm = 1;
                    bn = 8;
                }
                tm = outLen < tm ? 1 : tm;
                perGroup = bm * sm * tm;
                name = "gemv_" + type;
            }
            boolean axpby = addmm && (alpha != 1.0f || beta != 0.0f);
            String kernel = name + "_bm" + bm + "_bn" + bn + "_sm" + sm + "_sn" + sn + "_tm" + tm + "_tn" + tn + "_nc0_axpby" + (axpby ? 1 : 0);
            Ref mat = bMatrix ? b : a;
            Ref vec = bMatrix ? a : b;
            long matStride = bMatrix ? strideB : strideA;
            long vecStride = bMatrix ? strideA : strideB;
            int groups = (outLen + perGroup - 1) / perGroup;
            int gy = bn;
            int gz = bm;
            return p -> {
                Program.Launch l = p.launch(kernel).buffer(0, mat).buffer(1, vec).buffer(3, out).i32(4, k).i32(5, outLen).i32(6, matLd).i32(9, 1)
                        .bytes(10, MlxMetalKernels.intBytes(new int[] { batch })).i64(11, vecStride).i64(12, matStride);
                if (axpby) {
                    l.buffer(2, c).f32(7, alpha).f32(8, beta).i64(13, 0).i32(14, 1);
                }
                l.threadgroups(groups, 1, batch, 32, gy, gz);
            };
        }
        int tk = k / 16;
        int threshold = (devc == 's' || devc == 'd') ? 2048 : 1024;
        char ta = 'n';
        char tb = mm.bTransposed() ? 't' : 'n';
        if (batch == 1 && ((m + 15) / 16) * ((n + 15) / 16) <= threshold && tk >= 8 && k >= Math.max(m, n)) {
            // steel_gemm_splitk: float32 partial products, then an accumulate pass.
            int bm = m < 40 ? 16 : 32;
            int bn = n < 40 ? 16 : 32;
            int bk = 16;
            int partitions = Math.min(Math.max(2, nextPowerOfTwo(tk / (((m + 31) / 32) * ((n + 31) / 32)))), 32);
            int kIterations = (k / bk) / partitions;
            boolean mnAligned = m % bm == 0 && n % bn == 0;
            boolean kAligned = k % bk == 0;
            String kernel = "steel_gemm_splitk_" + ta + tb + "_" + type + "_float32_bm" + bm + "_bn" + bn + "_bk" + bk + "_wm2_wn2_MN_" + (mnAligned ? "t" : "n") + "aligned_K_"
                    + (kAligned ? "t" : "n") + "aligned";
            int tilesN = (n + bn - 1) / bn;
            int tilesM = (m + bm - 1) / bm;
            java.nio.ByteBuffer params = java.nio.ByteBuffer.allocate(52).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            params.putInt(m).putInt(n).putInt(k).putInt(lda).putInt(ldb).putInt(n).putInt(tilesN).putInt(tilesM).putInt(partitions).putInt(m * n).putInt(kIterations * bk).putInt(0)
                    .putInt(kIterations);
            byte[] paramBytes = params.array();
            boolean axpby = addmm && (alpha != 1.0f || beta != 0.0f);
            String accumulate = "steel_gemm_splitk_accum_" + type + "_float32" + (axpby ? "_axbpy" : "");
            return p -> {
                Ref split = p.scratch(4L * partitions * m * n);
                p.launch(kernel).buffer(0, a).buffer(1, b).buffer(2, split).bytes(3, paramBytes).threadgroups(tilesN, tilesM, partitions, 32, 2, 2);
                Program.Launch l = p.launch(accumulate).buffer(0, split).buffer(1, out).i32(2, partitions).i32(3, m * n).i32(4, n);
                if (axpby) {
                    l.buffer(5, c).i32(6, n).i32(7, 1).f32(8, alpha).f32(9, beta);
                }
                l.blocks(n, m, 1);
            };
        }
        // steel_matmul_regular with the device's tile parameters.
        int[] tiles = gemmTiles(devc, t, m, n, k, mm.bTransposed(), batch);
        int bm = tiles[0];
        int bn = tiles[1];
        int bk = tiles[2];
        int wm = tiles[3];
        int wn = tiles[4];
        String kernel = "steel_gemm_fused_" + ta + tb + "_" + type + "_" + type + "_bm" + bm + "_bn" + bn + "_bk" + bk + "_wm" + wm + "_wn" + wn;
        boolean useOutSource = addmm && (alpha != 0.0f || beta != 1.0f);
        boolean doAxpby = useOutSource && (alpha != 1.0f || beta != 1.0f);
        boolean alignM = m % bm == 0;
        boolean alignN = n % bn == 0;
        boolean alignK = k % bk == 0;
        int tilesN = (n + bn - 1) / bn;
        int tilesM = (m + bm - 1) / bm;
        byte[] params = gemmParams(m, n, k, lda, ldb, n, tilesN, tilesM, strideA, strideB, (long) m * n, k / bk);
        java.nio.ByteBuffer addParams = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        addParams.putInt(n).putInt(1).putLong(0).putFloat(alpha).putFloat(beta);
        byte[] addBytes = addParams.array();
        int gy = wn;
        int gz = wm;
        return p -> {
            Program.Launch l = p.launchIndexed(kernel, new int[] { 10, 100, 110, 200, 201, 202 }, new boolean[] { false, useOutSource, doAxpby, alignM, alignN, alignK })
                    .buffer(0, a).buffer(1, b).buffer(3, out).bytes(4, params);
            if (useOutSource) {
                l.buffer(2, c).bytes(5, addBytes);
            }
            l.threadgroups(tilesN, tilesM, batch, 32, gy, gz);
        };
    }

    /** GEMM_TPARAM_MACRO: {bm, bn, bk, wm, wn} for the device class. */
    private static int[] gemmTiles(char devc, int t, int m, int n, int k, boolean nt, int batch) {
        if (devc == 'g' || devc == 'p') {
            if (nt) {
                return new int[] { 64, 32, 32, 2, 2 };
            }
            return t != MLX_FLOAT32 ? new int[] { 64, 64, 16, 1, 2 } : new int[] { 64, 64, 16, 2, 2 };
        }
        if (devc == 'd') {
            if ((long) batch * m * n >= 1L << 20) {
                if (t != MLX_FLOAT32) {
                    if (2 * Math.max(m, n) > k) {
                        return new int[] { 64, 64, 16, 1, 2 };
                    }
                    return nt ? new int[] { 64, 32, 32, 2, 2 } : new int[] { 32, 64, 16, 1, 2 };
                }
                return new int[] { 64, 64, 16, 2, 2 };
            }
            if (t != MLX_FLOAT32) {
                return nt ? new int[] { 64, 32, 32, 2, 2 } : new int[] { 64, 64, 16, 1, 2 };
            }
            return nt ? new int[] { 32, 64, 16, 1, 2 } : new int[] { 64, 32, 32, 2, 2 };
        }
        return new int[] { 64, 64, 16, 2, 2 };
    }

    /** Arrays {@code indices} exist and share floating type {@code t}; returns t or -1. */
    private static int floatType(View v, int... indices) {
        int t = v.isArray(indices[0]) ? v.dtype(indices[0]) : -1;
        for (int i : indices) {
            if (!v.isArray(i) || v.dtype(i) != t) {
                return -1;
            }
        }
        return FLOATS.contains(t) ? t : -1;
    }

    /** a[m, k] @ b, with b [k, n] or the transpose of w[n, k]. */
    private static Encoder matmul(View v, int ai, int bi, int oi, int m, int k, int n, boolean bTransposed, String architecture) {
        int t = floatType(v, ai, bi, oi);
        if (t < 0 || v.size(ai) != m * k || v.size(bi) != k * n || v.size(oi) != m * n) {
            return null;
        }
        return matmulKernels(new Mm(t, 1, m, k, n, bTransposed, v.ref(ai), v.ref(bi), v.ref(oi), null, 1.0f, 0.0f), architecture);
    }

    private static void registerMatmulRoutes() {
        // matmul(a, b, out, m, k, n); matmul_transposed(a, w[n, k], out, m, k, n) = a @ w^T.
        ROUTES.put("mlx_matmul", v -> v.args() < 6 ? null : matmulRoute(v, false));
        ROUTES.put("mlx_matmul_transposed", v -> v.args() < 6 ? null : matmulRoute(v, true));
        ROUTES.put("mlx_quantized_matmul", MlxKernelRoutes::quantizedMatmul);
        ROUTES.put("mlx_gather_mm", MlxKernelRoutes::gatherMm);
        ROUTES.put("mlx_gather_qmm", MlxKernelRoutes::gatherQmm);
        ROUTES.put("mlx_quantize_mx", MlxKernelRoutes::quantizeMx);
        ROUTES.put("mlx_qqmm", MlxKernelRoutes::qqmm);
        ROUTES.put("mlx_segmented_mm", MlxKernelRoutes::segmentedMm);
        // addmm(c, a, b, out, m, k, n, alpha, beta) = alpha * a @ b + beta * c.
        ROUTES.put("mlx_addmm", v -> {
            if (v.args() < 9) {
                return null;
            }
            int m = v.intArg(4);
            int k = v.intArg(5);
            int n = v.intArg(6);
            int t = floatType(v, 0, 1, 2, 3);
            if (t < 0 || v.size(0) != m * n || v.size(1) != m * k || v.size(2) != k * n || v.size(3) != m * n) {
                return null;
            }
            return matmulKernels(new Mm(t, 1, m, k, n, false, v.ref(1), v.ref(2), v.ref(3), v.ref(0), v.floatArg(7), v.floatArg(8)), v.architecture());
        });
        // einsum("bij,bjk->bik"): a batched matmul of contiguous [B, i, j] and [B, j, k].
        ROUTES.put("mlx_einsum_bmm", v -> {
            if (v.args() < 7) {
                return null;
            }
            int batch = v.intArg(3);
            int i = v.intArg(4);
            int j = v.intArg(5);
            int k = v.intArg(6);
            int t = floatType(v, 0, 1, 2);
            if (t < 0 || batch <= 1 || v.size(0) != batch * i * j || v.size(1) != batch * j * k || v.size(2) != batch * i * k) {
                return null;
            }
            return matmulKernels(new Mm(t, batch, i, j, k, false, v.ref(0), v.ref(1), v.ref(2), null, 1.0f, 0.0f), v.architecture());
        });
        // tensordot(a[d0, d1, d2], b[d1, d2, d3], axes (1, 2) / (0, 1)) and tensordot_axis(a[m, k], b[k, n], 1): reshapes and a matmul.
        ROUTES.put("mlx_tensordot", v -> v.args() < 7 ? null : matmul(v, 0, 1, 2, v.intArg(3), v.intArg(4) * v.intArg(5), v.intArg(6), false, v.architecture()));
        ROUTES.put("mlx_tensordot_axis", v -> v.args() < 6 ? null : matmul(v, 0, 1, 2, v.intArg(3), v.intArg(4), v.intArg(5), false, v.architecture()));
        // outer(a[n], b[m]): a[:, None] * b[None, :].
        ROUTES.put("mlx_outer", v -> {
            int t = floatType(v, 0, 1, 2);
            if (t < 0 || v.size(2) != v.size(0) * v.size(1)) {
                return null;
            }
            int n = v.size(0);
            int m = v.size(1);
            return p -> p.generalBinary("Multiply", t, new int[] { n, m }, new long[] { 1, 0 }, v.ref(0), new long[] { 0, 1 }, v.ref(1), v.ref(2));
        });
        // kron(a[r1, c1], b[r2, c2]): a[r1, 1, c1, 1] * b[1, r2, 1, c2], as [r1, r2, c1, c2].
        ROUTES.put("mlx_kron", v -> {
            if (v.args() < 7) {
                return null;
            }
            int t = floatType(v, 0, 1, 2);
            int r1 = v.intArg(3);
            int c1 = v.intArg(4);
            int r2 = v.intArg(5);
            int c2 = v.intArg(6);
            if (t < 0 || v.size(0) != r1 * c1 || v.size(1) != r2 * c2 || v.size(2) != r1 * c1 * r2 * c2) {
                return null;
            }
            return p -> p.generalBinary("Multiply", t, new int[] { r1, r2, c1, c2 }, new long[] { c1, 0, 1, 0 }, v.ref(0), new long[] { 0, c2, 0, 1 }, v.ref(1), v.ref(2));
        });
        // inner(a[n], b[n]): tensordot over the last axis, a 1 x n by n x 1 matmul, i.e. MLX's dot_product.
        ROUTES.put("mlx_inner", v -> {
            int t = floatType(v, 0, 1, 2);
            if (t < 0 || v.size(0) != v.size(1) || v.size(2) != 1 || v.architecture() == null) {
                return null;
            }
            int n = v.size(0);
            String name = "dot_product_" + MlxMetalKernels.typeName(t) + "_it32_tg512_sg16";
            return p -> {
                long threads = (n + 31) / 32;
                long blocks = (threads + 511) / 512;
                Ref partials = p.scratch(4 * blocks);
                Ref total = p.scratch(4);
                p.launch(name).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, partials).i32(3, n).threads(blocks * 512, 512, 1, 1, 1, 1);
                allReduce(p, "sum", MLX_FLOAT32, blocks, partials, total);
                p.copy(MLX_FLOAT32, t, 1, total, v.ref(2));
            };
        });
        ROUTES.put("mlx_fast_scaled_dot_product_attention", MlxKernelRoutes::attention);
        ROUTES.put("mlx_quantize", v -> quantize(v, false));
        ROUTES.put("mlx_dequantize", v -> quantize(v, true));
    }

    /**
     * scaled_dot_product_attention(q[B, Hq, Lq, D], k[B, Hkv, Lk, D], v[B, Hkv, Lk, D], out, B, Hq, Hkv, Lq, Lk, D, scale, causal):
     * MLX's sdpa_vector or two-pass sdpa_vector kernels, where MLX itself uses them (Lq <= 8).
     */
    private static Encoder attention(View v) {
        if (v.args() < 12 || !v.isArray(0) || !v.isArray(1) || !v.isArray(2) || !v.isArray(3)) {
            return null;
        }
        int t = v.dtype(0);
        int b = v.intArg(4);
        int hq = v.intArg(5);
        int hkv = v.intArg(6);
        int lq = v.intArg(7);
        int lk = v.intArg(8);
        int d = v.intArg(9);
        float scale = v.floatArg(10);
        boolean causal = v.boolArg(11) && lq > 1;
        String type = cType(t);
        String architecture = v.architecture();
        boolean headDim = d == 64 || d == 96 || d == 128 || d == 256;
        if (type == null || architecture == null || architecture.isEmpty() || v.dtype(1) != t || v.dtype(2) != t || v.dtype(3) != t || hkv <= 0 || hq % hkv != 0) {
            return null;
        }
        int gqa = hq / hkv;
        if (v.size(0) != b * hq * lq * d || v.size(1) != b * hkv * lk * d || v.size(2) != b * hkv * lk * d || v.size(3) != b * hq * lq * d) {
            return null;
        }
        if (lq > 8) {
            return fullAttention(v, t, b, hq, hkv, lq, lk, d, scale, v.boolArg(11), architecture);
        }
        if (!headDim || lq > lk || lq * gqa > 32) {
            return null;
        }
        char devc = architecture.charAt(architecture.length() - 1);
        long headStride = hkv == 1 ? (long) hkv * lk * d : (long) lk * d;
        long seqStride = d;
        Ref q = v.ref(0);
        Ref k = v.ref(1);
        Ref vv = v.ref(2);
        Ref out = v.ref(3);
        int[] indices = { 20, 21, 22, 23, 24, 25 };
        Object[] constants = { false, false, causal, false, false, false };
        if (((devc == 'd' || devc == 's') && lk >= 1024) || (hkv < hq && lk >= 4096)) {
            int simds = gqa * lq;
            int blocks;
            if (devc == 's') {
                blocks = 64;
                if (lk > 1024 && simds > 4) {
                    blocks = lk <= 8192 ? 128 : lk <= 32768 ? 256 : lk <= 65536 ? 512 : 1024;
                }
            } else if (devc == 'd') {
                blocks = 128;
                if (simds <= 2 && lk > 8192) {
                    blocks = 256;
                } else if (simds >= 6) {
                    blocks = lk >= 65536 ? 1024 : lk >= 16384 ? 512 : blocks;
                }
            } else {
                blocks = simds >= 4 ? 64 : 32;
            }
            String override = System.getenv("MLX_SDPA_BLOCKS");
            if (override != null && !override.isEmpty() && Integer.parseInt(override) > 0) {
                blocks = (Integer.parseInt(override) + 31) / 32 * 32;
            }
            int finalBlocks = blocks;
            String first = "sdpa_vector_2pass_1_" + type + "_" + d + "_" + d;
            String second = "sdpa_vector_2pass_2_" + type + "_" + d;
            Object[] withBlocks = { false, false, causal, false, false, false, finalBlocks };
            int[] withBlocksIndices = { 20, 21, 22, 23, 24, 25, 26 };
            long rows = (long) b * hq * lq;
            return p -> {
                Ref intermediate = p.scratch(rows * finalBlocks * d * MlxMetalKernels.itemSize(t));
                Ref sums = p.scratch(4L * rows * finalBlocks);
                Ref maxs = p.scratch(4L * rows * finalBlocks);
                p.launchConstants(first, withBlocksIndices, withBlocks).buffer(0, q).buffer(1, k).buffer(2, vv).buffer(3, intermediate).buffer(4, sums).buffer(5, maxs).i32(7, lk)
                        .i64(8, headStride).i64(9, seqStride).i64(10, headStride).i64(11, seqStride).f32(12, scale).threadgroups(hkv, b, finalBlocks, 32, gqa, lq);
                p.launch(second).buffer(0, intermediate).buffer(1, sums).buffer(2, maxs).buffer(3, out).i32(4, finalBlocks).threadgroups((long) b * hq, lq, 1, 1024, 1, 1);
            };
        }
        String kernel = "sdpa_vector_" + type + "_" + d + "_" + d;
        return p -> p.launchConstants(kernel, indices, constants).buffer(0, q).buffer(1, k).buffer(2, vv).buffer(3, out).i32(4, gqa).i32(5, lk).i64(6, headStride).i64(7, seqStride)
                .i64(8, headStride).i64(9, seqStride).f32(10, scale).threadgroups((long) b * hq, lq, 1, 1024, 1, 1);
    }

    /**
     * Prefill attention (Lq > 8): MLX's steel_attention kernel, where MLX uses it (head dim 64/72/80/96/128,
     * causal only with Lq <= Lk, no NAX). The output strides are those of the contiguous [B, H, Lq, D] buffer.
     */
    private static Encoder fullAttention(View v, int t, int b, int hq, int hkv, int lq, int lk, int d, float scale, boolean causal, String architecture) {
        boolean headDim = d == 64 || d == 72 || d == 80 || d == 96 || d == 128;
        if (!headDim || (causal && lq > lk) || MlxMetalKernels.architectureGeneration(architecture) >= 17) {
            return null;
        }
        int bq = 32;
        int bk = d < 128 ? 32 : 16;
        String type = MlxMetalKernels.typeName(t);
        String kernel = "steel_attention_" + type + "_bq" + bq + "_bk" + bk + "_bd" + d + "_wm4_wn1_mask" + type;
        boolean alignQ = lq % bq == 0;
        boolean alignK = lk % bk == 0;
        int nq = (lq + bq - 1) / bq;
        int nk = (lk + bk - 1) / bk;
        int nqAligned = lq / bq;
        int nkAligned = lk / bk;
        java.nio.ByteBuffer params = java.nio.ByteBuffer.allocate(152).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        params.putInt(b).putInt(hq).putInt(d).putInt(lq).putInt(lk).putInt(hq / hkv).putFloat(scale).putInt(nq).putInt(nk).putInt(nqAligned).putInt(nkAligned)
                .putInt(lq - nqAligned * bq).putInt(lk - nkAligned * bk).putInt(lk - lq);
        long[][] strides = { { (long) hq * lq * d, (long) lq * d, d }, { (long) hkv * lk * d, (long) lk * d, d }, { (long) hkv * lk * d, (long) lk * d, d },
                { (long) hq * lq * d, (long) lq * d, d } };
        for (long[] st : strides) {
            for (long x : st) {
                params.putLong(x);
            }
        }
        byte[] paramBytes = params.array();
        return p -> p.launchIndexed(kernel, new int[] { 200, 201, 300, 301, 302 }, new boolean[] { alignQ, alignK, false, causal, false }).buffer(0, v.ref(0)).buffer(1, v.ref(1))
                .buffer(2, v.ref(2)).buffer(3, v.ref(3)).bytes(4, paramBytes).threadgroups(nq, hq, b, 32, 4, 1);
    }

    /** quantize_mx(w[rows, cols], wq, scales, rows, cols, mode) in mxfp8 (group 32, 8 bits): MLX's quantize kernel. */
    private static Encoder quantizeMx(View v) {
        if (v.args() < 6 || v.intArg(5) != 0 || !v.isArray(0) || !v.isArray(1) || !v.isArray(2)) {
            return null;
        }
        int t = v.dtype(0);
        String type = cType(t);
        long elements = (long) v.intArg(3) * v.intArg(4);
        if (type == null || v.intArg(4) % 32 != 0 || v.size(0) != elements || (long) v.size(1) * 4 != elements || v.dtype(2) != MLX_UINT8 || v.size(2) != elements / 32) {
            return null;
        }
        String kernel = "mxfp8_quantize_" + type + "_gs_32_b_8_hgs_false";
        if (!v.hasKernel(kernel)) {
            return null;
        }
        return p -> {
            Program.Launch k = p.launch(kernel).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2));
            k.threads(elements, Math.min(elements, k.maxThreads), 1, 1, 1, 1);
        };
    }

    /**
     * qqmm(x[m, k], wq[n, k / 4], scales[n, k / 32], out[m, n], m, k, n, mode) in mxfp8: quantize-dequantize x, then
     * MLX's mxfp8 qmv (or qmv_quad for k = 64 / 128); m >= 2 takes MLX's qmv_wide, which is not reproduced.
     */
    private static Encoder qqmm(View v) {
        if (v.args() < 8 || v.intArg(7) != 0 || !v.isArray(0) || !v.isArray(1) || !v.isArray(2) || !v.isArray(3)) {
            return null;
        }
        int t = v.dtype(0);
        String type = cType(t);
        int m = v.intArg(4);
        int k = v.intArg(5);
        int n = v.intArg(6);
        boolean quad = k == 64 || k == 128;
        if (type == null || v.dtype(3) != t || v.dtype(2) != MLX_UINT8 || k % 32 != 0 || v.size(0) != m * k || (long) v.size(1) * 4 != (long) n * k || v.size(2) != n * (k / 32)
                || v.size(3) != m * n || (!quad && m >= 2)) {
            return null;
        }
        String roundTrip = "mxfp8_quantize_dequantize_" + type + "_gs_32_b_8_hgs_false";
        boolean fast = n % 8 == 0 && k % (4 * 2 * 32) == 0;
        String kernel = quad ? "mxfp8_qmv_quad_" + type + "_gs_32_b_8_d_" + k + "_batch_0" : "mxfp8_qmv" + (fast ? "_fast_" : "_") + type + "_gs_32_b_8_batch_0";
        if (!v.hasKernel(roundTrip) || !v.hasKernel(kernel)) {
            return null;
        }
        long elements = (long) m * k;
        return p -> {
            Ref xhat = p.scratch(elements * MlxMetalKernels.itemSize(t));
            Program.Launch q = p.launch(roundTrip).buffer(0, v.ref(0)).buffer(2, xhat);
            q.threads(elements, Math.min(elements, q.maxThreads), 1, 1, 1, 1);
            if (quad) {
                // qmv_quad numbers its arguments in sequence, so without biases x follows the scales directly.
                p.launch(kernel).buffer(0, v.ref(1)).buffer(1, v.ref(2)).buffer(2, xhat).buffer(3, v.ref(3)).i32(4, k).i32(5, n).threadgroups(m, (n + 63) / 64, 1, 32, 1, 1);
            } else {
                p.launch(kernel).buffer(0, v.ref(1)).buffer(1, v.ref(2)).buffer(3, xhat).buffer(4, v.ref(3)).i32(5, k).i32(6, n).threadgroups(m, (n + 7) / 8, 1, 32, 2, 1);
            }
        };
    }

    /**
     * gather_qmm(x[B, m, k], wq[E, n, k * bits / 32], scales[E, n, k / gs], biases, lhs[B], rhs[B], out[B, m, n],
     * B, E, m, k, n, gs, bits), affine with transposed weights: MLX's gather_qmv below the batch limit, gather_qmm above.
     */
    private static Encoder gatherQmm(View v) {
        if (v.args() < 14) {
            return null;
        }
        for (int i = 0; i < 7; i++) {
            if (!v.isArray(i)) {
                return null;
            }
        }
        int t = v.dtype(0);
        int batches = v.intArg(7);
        int experts = v.intArg(8);
        int m = v.intArg(9);
        int k = v.intArg(10);
        int n = v.intArg(11);
        int gs = v.intArg(12);
        int bits = v.intArg(13);
        String type = cType(t);
        String architecture = v.architecture();
        if (type == null || architecture == null || architecture.isEmpty() || !powerOfTwoBits(bits) || gs <= 0 || k % gs != 0 || v.dtype(2) != t || v.dtype(3) != t || v.dtype(6) != t
                || v.dtype(4) != MLX_INT32 || v.dtype(5) != MLX_INT32 || v.size(4) != batches || v.size(5) != batches || v.size(0) != batches * m * k
                || (long) v.size(1) * 32 != (long) experts * n * k * bits || v.size(2) != experts * n * (k / gs) || v.size(6) != batches * m * n) {
            return null;
        }
        int gen = MlxMetalKernels.architectureGeneration(architecture);
        boolean matrix = m >= qmvBatchLimit(k, n, architecture);
        // (MLX's gather_qmm_rhs path needs sorted right indices, which the provider does not ask for.)
        if (matrix && gen >= 17) {
            return null;
        }
        int words = k * bits / 32;
        int groups = k / gs;
        byte[] xShape = MlxMetalKernels.intBytes(new int[] { batches, m, k });
        byte[] wShape = MlxMetalKernels.intBytes(new int[] { experts, n, words });
        long[] xStrides = { (long) m * k, k, 1 };
        long[] wStrides = { (long) n * words, words, 1 };
        long[] sStrides = { (long) n * groups, groups, 1 };
        byte[] indexShape = MlxMetalKernels.intBytes(new int[] { batches });
        if (matrix) {
            String kernel = "affine_gather_qmm_t_" + type + "_gs_" + gs + "_b_" + bits + (n % 32 == 0 ? "_alN_true" : "_alN_false");
            return p -> p.launch(kernel).buffer(0, v.ref(1)).buffer(1, v.ref(2)).buffer(2, v.ref(3)).buffer(3, v.ref(0)).buffer(4, v.ref(4)).buffer(5, v.ref(5)).buffer(6, v.ref(6))
                    .i32(7, k).i32(8, n).i32(9, m).i32(10, 1).bytes(11, xShape).i64(12, xStrides).i32(13, 1).bytes(14, wShape).i64(15, wStrides).i64(16, sStrides).i64(17, sStrides)
                    .i32(18, 1).bytes(19, indexShape).i64(20, 1).i64(21, 1).threadgroups((n + 31) / 32, (m + 31) / 32, batches, 32, 2, 2);
        }
        int alignment = (32 / bits) * (bits == 2 ? 1 : 2) * 32;
        boolean fast = n % 8 == 0 && k % alignment == 0;
        String kernel = "affine_gather_qmv" + (fast ? "_fast_" : "_") + type + "_gs_" + gs + "_b_" + bits;
        return p -> p.launch(kernel).buffer(0, v.ref(1)).buffer(1, v.ref(2)).buffer(2, v.ref(3)).buffer(3, v.ref(0)).buffer(4, v.ref(4)).buffer(5, v.ref(5)).buffer(6, v.ref(6))
                .i32(7, k).i32(8, n).i32(9, 1).bytes(10, xShape).i64(11, xStrides).i32(12, 1).bytes(13, wShape).i64(14, wStrides).i64(15, sStrides).i64(16, sStrides).i32(17, 1)
                .bytes(18, indexShape).i64(19, 1).i64(20, 1).threadgroups(m, (n + 7) / 8, batches, 32, 2, 1);
    }

    /**
     * gather_mm(a[Ba, m, k], b[Bb, k, n], lhs[L], rhs[L], out[L, m, n], Ba, Bb, m, k, n): MLX's steel gather
     * GEMM (the path for contiguous b with m and n above one); null for the gather_mv cases.
     */
    private static Encoder gatherMm(View v) {
        if (v.args() < 10 || !v.isArray(2) || !v.isArray(3) || v.dtype(2) != MLX_INT32 || v.dtype(3) != MLX_INT32) {
            return null;
        }
        int t = floatType(v, 0, 1, 4);
        int ba = v.intArg(5);
        int bb = v.intArg(6);
        int m = v.intArg(7);
        int k = v.intArg(8);
        int n = v.intArg(9);
        int batches = v.size(2);
        String architecture = v.architecture();
        if (t < 0 || architecture == null || architecture.isEmpty() || m == 1 || n == 1 || v.size(3) != batches || v.size(0) != ba * m * k || v.size(1) != bb * k * n
                || v.size(4) != batches * m * n || MlxMetalKernels.architectureGeneration(architecture) >= 17) {
            return null;
        }
        int[] tiles = gemmTiles(architecture.charAt(architecture.length() - 1), t, m, n, k, false, batches);
        int bm = tiles[0];
        int bn = tiles[1];
        int bk = tiles[2];
        int wm = tiles[3];
        int wn = tiles[4];
        String type = MlxMetalKernels.typeName(t);
        String kernel = "steel_gather_mm_nn_" + type + "_" + type + "_bm" + bm + "_bn" + bn + "_bk" + bk + "_wm" + wm + "_wn" + wn;
        if (!v.hasKernel(kernel)) {
            return null;
        }
        int tilesN = (n + bn - 1) / bn;
        int tilesM = (m + bm - 1) / bm;
        java.nio.ByteBuffer params = java.nio.ByteBuffer.allocate(72).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        params.putInt(m).putInt(n).putInt(k).putInt(k).putInt(n).putInt(n).putInt(tilesN).putInt(tilesM).putLong(1).putLong(1).putLong((long) m * n).putInt(0).putInt(k / bk)
                .putInt(1);
        byte[] paramBytes = params.array();
        boolean[] constants = { false, m % bm == 0, n % bn == 0, k % bk == 0 };
        int gy = wn;
        int gz = wm;
        return p -> p.launchIndexed(kernel, new int[] { 10, 200, 201, 202 }, constants).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2)).buffer(3, v.ref(3))
                .buffer(4, v.ref(4)).bytes(5, paramBytes).bytes(6, MlxMetalKernels.intBytes(new int[] { batches })).i64(7, 1).i64(8, 1).i32(9, 1)
                .bytes(10, MlxMetalKernels.intBytes(new int[] { ba, m, k })).i64(11, (long) m * k, k, 1).i32(12, 1).bytes(13, MlxMetalKernels.intBytes(new int[] { bb, k, n }))
                .i64(14, (long) k * n, n, 1).threadgroups(tilesN, tilesM, batches, 32, gy, gz);
    }

    /** segmented_mm(a[m, k], b[k, n], segments[S, 2], out[S, m, n], m, k, n): MLX's steel segmented GEMM. */
    private static Encoder segmentedMm(View v) {
        if (v.args() < 7 || !v.isArray(2) || v.dtype(2) != MLX_INT32 || v.size(2) % 2 != 0) {
            return null;
        }
        int t = floatType(v, 0, 1, 3);
        int m = v.intArg(4);
        int k = v.intArg(5);
        int n = v.intArg(6);
        int segments = v.size(2) / 2;
        String architecture = v.architecture();
        if (t < 0 || architecture == null || architecture.isEmpty() || v.size(0) != m * k || v.size(1) != k * n || v.size(3) != segments * m * n
                || MlxMetalKernels.architectureGeneration(architecture) >= 17) {
            return null;
        }
        int[] tiles = gemmTiles(architecture.charAt(architecture.length() - 1), t, m, n, k, false, segments);
        int bm = tiles[0];
        int bn = tiles[1];
        String type = MlxMetalKernels.typeName(t);
        String kernel = "steel_segmented_mm_nn_" + type + "_" + type + "_bm" + bm + "_bn" + bn + "_bk" + tiles[2] + "_wm" + tiles[3] + "_wn" + tiles[4];
        if (!v.hasKernel(kernel)) {
            return null;
        }
        int tilesN = (n + bn - 1) / bn;
        int tilesM = (m + bm - 1) / bm;
        java.nio.ByteBuffer params = java.nio.ByteBuffer.allocate(72).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        params.putInt(m).putInt(n).putInt(k).putInt(k).putInt(n).putInt(n).putInt(tilesN).putInt(tilesM).putLong(0).putLong(0).putLong((long) m * n).putInt(0).putInt(0).putInt(0);
        byte[] paramBytes = params.array();
        boolean[] constants = { true, m % bm == 0, n % bn == 0 };
        int gy = tiles[4];
        int gz = tiles[3];
        return p -> p.launchIndexed(kernel, new int[] { 199, 200, 201 }, constants).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2)).buffer(3, v.ref(3))
                .bytes(4, paramBytes).threadgroups(tilesN, tilesM, segments, 32, gy, gz);
    }

    /** MLX's C type names, as its quantized kernel names use them. */
    private static String cType(int dtype) {
        return switch (dtype) {
            case MLX_FLOAT32 -> "float";
            case MlxMetalKernels.MLX_FLOAT16 -> "float16_t";
            case MlxMetalKernels.MLX_BFLOAT16 -> "bfloat16_t";
            default -> null;
        };
    }

    private static boolean powerOfTwoBits(int bits) {
        return bits == 2 || bits == 4 || bits == 8;
    }

    /** MLX's get_qmv_batch_limit: below it, a transposed quantized matmul runs as matrix-vector products. */
    private static int qmvBatchLimit(int d, int o, String architecture) {
        char size = architecture.charAt(architecture.length() - 1);
        int gen = MlxMetalKernels.architectureGeneration(architecture);
        boolean small = d <= 2048 && o <= 2048;
        boolean medium = d <= 4096 && o <= 4096;
        if (gen >= 17 && size != 'd') {
            return small ? 33 : medium ? 25 : 13;
        }
        if (gen >= 15 && size != 'd') {
            return small ? 13 : medium ? 15 : 13;
        }
        if (size == 'd') {
            return small ? 32 : medium ? 18 : 12;
        }
        if (gen >= 13) {
            return small ? 14 : medium ? 10 : 6;
        }
        return small ? 18 : medium ? 12 : 10;
    }

    /**
     * quantized_matmul(x[m, k], wq[n, k * bits / 32], scales, biases, out, m, k, n, group_size, bits), affine,
     * transposed weights: MLX's qmv_quad / qmv_fast / qmv kernels when m is below the batch limit.
     */
    private static Encoder quantizedMatmul(View v) {
        if (v.args() < 10 || !v.isArray(0) || !v.isArray(1) || !v.isArray(2) || !v.isArray(3) || !v.isArray(4)) {
            return null;
        }
        int t = v.dtype(0);
        int m = v.intArg(5);
        int k = v.intArg(6);
        int n = v.intArg(7);
        int gs = v.intArg(8);
        int bits = v.intArg(9);
        String architecture = v.architecture();
        String type = cType(t);
        if (type == null || architecture == null || architecture.isEmpty() || !powerOfTwoBits(bits) || v.dtype(2) != t || v.dtype(3) != t || v.dtype(4) != t
                || v.size(0) != m * k || v.size(4) != m * n || (long) v.size(1) * 32 != (long) n * k * bits || gs <= 0 || v.size(2) != n * (k / gs)) {
            return null;
        }
        int gen = MlxMetalKernels.architectureGeneration(architecture);
        Ref w = v.ref(1);
        Ref scales = v.ref(2);
        Ref biases = v.ref(3);
        Ref x = v.ref(0);
        Ref out = v.ref(4);
        if (m >= qmvBatchLimit(k, n, architecture)) {
            return gen >= 17 ? null : quantizedPrefill(t, type, m, k, n, gs, bits, w, scales, biases, x, out);
        }
        if (gen >= 15 && m >= 2) {
            return null;
        }
        if (k == 64 || k == 128) {
            String kernel = "affine_qmv_quad_" + type + "_gs_" + gs + "_b_" + bits + "_d_" + k + "_batch_0";
            return p -> p.launch(kernel).buffer(0, w).buffer(1, scales).buffer(2, biases).buffer(3, x).buffer(4, out).i32(5, k).i32(6, n).threadgroups(m, (n + 63) / 64, 1, 32, 1, 1);
        }
        int alignment = (32 / bits) * (bits == 2 ? 1 : 2) * 32;
        boolean fast = n % 8 == 0 && k % alignment == 0;
        String kernel = "affine_qmv" + (fast ? "_fast_" : "_") + type + "_gs_" + gs + "_b_" + bits + "_batch_0";
        return p -> p.launch(kernel).buffer(0, w).buffer(1, scales).buffer(2, biases).buffer(3, x).buffer(4, out).i32(5, k).i32(6, n).threadgroups(m, (n + 7) / 8, 1, 32, 2, 1);
    }

    /**
     * The prefill quantized matmul MLX runs for transposed weights at batch 1: qmm_t_splitk and a column
     * reduction of the partial products over the split axis, or qmm_t when the split comes to one.
     */
    private static Encoder quantizedPrefill(int t, String type, int m, int k, int n, int gs, int bits, Ref w, Ref scales, Ref biases, Ref x, Ref out) {
        int nTiles = (n + 31) / 32;
        int mTiles = (m + 31) / 32;
        int split = Math.max(1, 512 / (nTiles * mTiles));
        int kAlign = Math.max(gs, 32);
        split = Math.min(split, k / kAlign);
        while (split > 1 && k % (split * kAlign) != 0) {
            split--;
        }
        String aligned = n % 32 == 0 ? "_alN_true" : "_alN_false";
        if (split <= 1) {
            String kernel = "affine_qmm_t_" + type + "_gs_" + gs + "_b_" + bits + aligned + "_batch_0";
            return p -> p.launch(kernel).buffer(0, w).buffer(1, scales).buffer(2, biases).buffer(3, x).buffer(4, out).i32(5, k).i32(6, n).i32(7, m)
                    .threadgroups(nTiles, mTiles, 1, 32, 2, 2);
        }
        long stride = (long) m * n;
        if (split > 256 || (stride < 32 && split >= 1024)) {
            // MLX would use its 2-pass or long-column reduction here, which are not reproduced.
            return null;
        }
        int finalSplit = split;
        String kernel = "affine_qmm_t_splitk_" + type + "_gs_" + gs + "_b_" + bits + aligned;
        return p -> {
            Ref partial = p.scratch(finalSplit * stride * MlxMetalKernels.itemSize(t));
            p.launch(kernel).buffer(0, w).buffer(1, scales).buffer(2, biases).buffer(3, x).buffer(4, partial).i32(5, k).i32(6, n).i32(7, m).i32(8, k / finalSplit)
                    .i32(9, (int) stride).threadgroups(nTiles, mTiles, finalSplit, 32, 2, 2);
            columnReduce(p, "sum", t, finalSplit, stride, partial, out);
        };
    }

    /**
     * MLX's strided (column) reduce of a contiguous [size, stride] array over axis 0 into [stride]:
     * col_reduce_small below 32 rows, col_reduce_looped otherwise (the shapes split-K produces).
     */
    private static void columnReduce(Program p, String op, int t, long size, long stride, Ref in, Ref out) {
        String type = MlxMetalKernels.typeName(reduceTypes(t, op)[0]);
        Program.Launch k;
        if (size < 32) {
            k = p.launch("col_reduce_small_1_reduce_" + op + type);
        } else {
            k = p.launch("col_reduce_looped_1_32_32_reduce_" + op + type);
        }
        k.buffer(0, in).buffer(1, out).i64(2, size).i64(3, stride).i32(4, 0).i64(5, 0).i32(6, 0).i32(7, (int) size).i64(8, stride).i32(9, 1).i64(10, 1);
        if (size < 32) {
            long blocks = (stride + 3) / 4;
            long gx = Math.min(blocks, 32);
            long gy = Math.min(8, Math.min(k.maxThreads / gx, size));
            k.threadgroups((blocks + gx - 1) / gx, 1, 1, gx, gy, 1);
        } else {
            k.threads(256 * ((stride + 31) / 32), 256, 1, 1, 1, 1);
        }
    }

    /**
     * quantize(w[rows, cols], wq, scales, biases, rows, cols, group_size, bits) and
     * dequantize(wq, scales, biases, out, rows, cols, group_size, bits), affine.
     */
    private static Encoder quantize(View v, boolean dequantize) {
        if (v.args() < 8 || !v.isArray(0) || !v.isArray(1) || !v.isArray(2) || !v.isArray(3)) {
            return null;
        }
        int rows = v.intArg(4);
        int cols = v.intArg(5);
        int gs = v.intArg(6);
        int bits = v.intArg(7);
        int t = dequantize ? v.dtype(3) : v.dtype(0);
        String type = cType(t);
        if (type == null || !powerOfTwoBits(bits) || gs <= 0 || cols % gs != 0) {
            return null;
        }
        long elements = (long) rows * cols;
        long packedBytes = elements * bits / 8;
        int groups = rows * (cols / gs);
        int wqIndex = dequantize ? 0 : 1;
        int valuesIndex = dequantize ? 3 : 0;
        int scalesIndex = dequantize ? 1 : 2;
        int biasesIndex = dequantize ? 2 : 3;
        if ((long) v.size(wqIndex) * MlxMetalKernels.itemSize(v.dtype(wqIndex)) != packedBytes || v.size(valuesIndex) != elements || !v.is(scalesIndex, t, groups)
                || !v.is(biasesIndex, t, groups)) {
            return null;
        }
        String kernel = "affine_" + (dequantize ? "dequantize" : "quantize") + "_" + type + "_gs_" + gs + "_b_" + bits;
        int packsPerInt = 8 / bits;
        long threads = dequantize ? elements / packsPerInt : elements / Math.max(gs / 32, 1);
        return p -> {
            // Both kernels take their four arrays in argument order: (w, wq, scales, biases) or (wq, scales, biases, w).
            Program.Launch k = p.launch(kernel).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2)).buffer(3, v.ref(3));
            k.threads(threads, Math.min(threads, k.maxThreads), 1, 1, 1, 1);
        };
    }

    private static Encoder matmulRoute(View v, boolean transposed) {
        String architecture = v.architecture();
        return architecture == null ? null : matmul(v, 0, 1, 2, v.intArg(3), v.intArg(4), v.intArg(5), transposed, architecture);
    }

    // ---------------------------------------------------------------- scans

    /** cumsum, cumprod, cummax, cummin, logcumsumexp over axis 1 of x viewed as [outer, len, inner]. */
    private static Encoder scan(View v, String op) {
        int t = pairType(v, 0, 1);
        if (t < 0 || v.args() < 7 || !NUMBERS.contains(t) || (op.equals("logaddexp") && !FLOATS.contains(t))) {
            return null;
        }
        long outer = v.intArg(2);
        long len = v.intArg(3);
        long inner = v.intArg(4);
        boolean reverse = v.boolArg(5);
        boolean inclusive = v.boolArg(6);
        if (outer * len * inner != v.size(0) || v.size(1) != v.size(0) || len <= 0) {
            return null;
        }
        boolean contiguous = inner == 1;
        String type = MlxMetalKernels.typeName(t);
        String name = (contiguous ? "contig_" : "strided_") + "scan_" + (reverse ? "reverse_" : "") + (inclusive ? "inclusive_" : "exclusive_") + op + "_" + type + "_" + type;
        int nReads = MlxMetalKernels.itemSize(t) <= 4 ? 4 : 2;
        return p -> {
            Program.Launch k = p.launch(name).buffer(0, v.ref(0)).buffer(1, v.ref(1)).i64(2, len);
            if (contiguous) {
                long perSimd = nReads * 32L;
                long group = k.maxThreads;
                if (len <= nReads * 1024L) {
                    group = (len + perSimd - 1) / perSimd * 32;
                } else if (len <= nReads * 2048L) {
                    group = (len / 2 + perSimd - 1) / perSimd * 32;
                }
                group = Math.min(group, k.maxThreads);
                k.threads(group, group, outer, 1, 1, 1);
            } else {
                long blocks = (inner + 31) / 32;
                long group = (32 / nReads) * 32L;
                k.i64(3, inner).i64(4, blocks).threads(group, group, outer * blocks, 1, 1, 1);
            }
        };
    }

    private static void registerScanRoutes() {
        String[][] scans = { { "cumsum", "sum" }, { "cumprod", "prod" }, { "cummax", "max" }, { "cummin", "min" }, { "logcumsumexp", "logaddexp" } };
        for (String[] sc : scans) {
            ROUTES.put("mlx_" + sc[0], v -> scan(v, sc[1]));
        }
    }

    // ---------------------------------------------------------------- sorting

    /**
     * MLX's gpu_merge_sort of x viewed as [outer, len, inner] along axis 1 into {@code out} (values, or
     * uint32 indices when {@code argsort}): the single-block kernel, or block sort plus merge passes.
     */
    private static void mergeSort(Program p, int t, long outer, long len, long inner, Ref in, Ref out, boolean argsort) {
        int tn = 4;
        long potential = (len + tn - 1) / tn;
        int bn = potential > 256 ? 512 : potential > 128 ? 256 : potential > 64 ? 128 : potential > 32 ? 64 : 32;
        if (bn == 512 && MlxMetalKernels.itemSize(t) > 4) {
            bn = 256;
        }
        long perBlock = (long) bn * tn;
        long blocks = (len + perBlock - 1) / perBlock;
        long rows = outer * inner;
        String type = MlxMetalKernels.typeName(t);
        String outType = argsort ? "uint32" : type;
        if (blocks == 1) {
            boolean contiguous = inner == 1;
            String name = (contiguous ? "c" : "nc") + (argsort ? "arg" : "") + "_block_sort_" + type + "_" + outType + "_bn" + bn + "_tn" + tn;
            Program.Launch k = p.launch(name).buffer(0, in).buffer(1, out).i32(2, (int) len).i32(3, (int) inner).i32(4, (int) inner);
            if (contiguous) {
                int segment = outer > 1 ? (int) len : Integer.MAX_VALUE;
                k.i32(5, segment).i32(6, segment);
            } else {
                k.i32(5, 2).bytes(6, MlxMetalKernels.intBytes(new int[] { (int) outer, (int) inner })).i64(7, len * inner, 1).i64(8, len * inner, 1);
            }
            k.threadgroups(1, rows, 1, bn, 1, 1);
            return;
        }
        int valueSize = MlxMetalKernels.itemSize(t);
        Ref vals0 = p.scratch(rows * len * valueSize);
        Ref vals1 = p.scratch(rows * len * valueSize);
        Ref idxs0 = p.scratch(rows * len * 4);
        Ref idxs1 = p.scratch(rows * len * 4);
        Ref partitions = p.scratch(rows * (blocks + 1) * 4);
        String suffix = "_" + type + "_uint32_bn" + bn + "_tn" + tn;
        Program.Launch k = p.launch("sort_mbsort" + suffix).buffer(0, in).buffer(1, vals0).buffer(2, idxs0).i32(3, (int) len).i32(4, (int) inner);
        if (inner == 1 && outer == 1) {
            k.i32(5, 0).bytes(6, MlxMetalKernels.intBytes(new int[] { 0 })).i64(7, 1);
        } else {
            k.i32(5, 2).bytes(6, MlxMetalKernels.intBytes(new int[] { (int) outer, (int) inner })).i64(7, len * inner, 1);
        }
        k.threadgroups(blocks, rows, 1, bn, 1, 1);
        boolean ping = false;
        Ref valsOut = vals1;
        Ref idxsOut = idxs1;
        long partitionThreads = Math.min(blocks + 1, 1024);
        for (long tiles = 2; tiles / 2 < blocks; tiles *= 2) {
            Ref valsIn = ping ? vals1 : vals0;
            Ref idxsIn = ping ? idxs1 : idxs0;
            valsOut = ping ? vals0 : vals1;
            idxsOut = ping ? idxs0 : idxs1;
            ping = !ping;
            p.launch("partition_mbsort" + suffix).buffer(0, partitions).buffer(1, valsIn).buffer(2, idxsIn).i32(3, (int) len).i32(4, (int) tiles).i32(5, (int) blocks)
                    .threadgroups(1, rows, 1, partitionThreads, 1, 1);
            p.launch("merge_mbsort" + suffix).buffer(0, partitions).buffer(1, valsIn).buffer(2, idxsIn).buffer(3, valsOut).buffer(4, idxsOut).i32(5, (int) len).i32(6, (int) tiles)
                    .i32(7, (int) blocks).threadgroups(blocks, rows, 1, bn, 1, 1);
        }
        Ref sorted = argsort ? idxsOut : valsOut;
        int sortedType = argsort ? MlxMetalKernels.MLX_UINT32 : t;
        int[] shape = { (int) outer, (int) len, (int) inner };
        p.generalCopy(sortedType, sortedType, shape, new long[] { len * inner, 1, len }, sorted, null, out, null);
    }

    /** sort-like calls: whole (x, out) or along axis 1 of [outer, len, inner] (x, out, outer, len, inner). */
    private static Encoder sortRoute(View v, boolean argsort, boolean alongAxis) {
        if (!v.isArray(0) || !v.isArray(1) || !NUMBERS.contains(v.dtype(0))) {
            return null;
        }
        long outer = alongAxis ? v.intArg(2) : 1;
        long len = alongAxis ? v.intArg(3) : v.size(0);
        long inner = alongAxis ? v.intArg(4) : 1;
        int t = v.dtype(0);
        if (outer * len * inner != v.size(0) || v.size(1) != v.size(0) || (argsort ? v.dtype(1) != MLX_INT32 : v.dtype(1) != t)) {
            return null;
        }
        return p -> mergeSort(p, t, outer, len, inner, v.ref(0), v.ref(1), argsort);
    }

    /** topk(x, out, k) over a whole array, or topk_axis(x, out, rows, cols, k) over rows: partition, then the last k along the axis. */
    private static Encoder topk(View v, boolean rowsForm) {
        if (!v.isArray(0) || !v.isArray(1) || !NUMBERS.contains(v.dtype(0)) || v.dtype(1) != v.dtype(0)) {
            return null;
        }
        int t = v.dtype(0);
        long rows = rowsForm ? v.intArg(2) : 1;
        long cols = rowsForm ? v.intArg(3) : v.size(0);
        int k = rowsForm ? v.intArg(4) : v.intArg(2);
        if (rows * cols != v.size(0) || k <= 0 || k > cols || v.size(1) != rows * k) {
            return null;
        }
        return p -> {
            if (k == cols) {
                p.copy(t, t, (int) (rows * cols), v.ref(0), v.ref(1));
                return;
            }
            Ref sorted = p.scratch(rows * cols * MlxMetalKernels.itemSize(t));
            mergeSort(p, t, rows, cols, 1, v.ref(0), sorted, false);
            block(p, t, sorted, (int) cols, cols - k, v.ref(1), k, 0, (int) rows, k);
        };
    }

    private static void registerSortRoutes() {
        ROUTES.put("mlx_sort", v -> sortRoute(v, false, false));
        ROUTES.put("mlx_argsort", v -> sortRoute(v, true, false));
        ROUTES.put("mlx_partition", v -> sortRoute(v, false, false));
        ROUTES.put("mlx_argpartition", v -> sortRoute(v, true, false));
        ROUTES.put("mlx_sort_axis", v -> v.args() < 5 ? null : sortRoute(v, false, true));
        ROUTES.put("mlx_argsort_axis", v -> v.args() < 5 ? null : sortRoute(v, true, true));
        ROUTES.put("mlx_partition_axis", v -> v.args() < 5 ? null : sortRoute(v, false, true));
        ROUTES.put("mlx_argpartition_axis", v -> v.args() < 5 ? null : sortRoute(v, true, true));
        ROUTES.put("mlx_topk", v -> v.args() < 3 ? null : topk(v, false));
        ROUTES.put("mlx_topk_axis", v -> v.args() < 5 ? null : topk(v, true));
    }

    // ---------------------------------------------------------------- random sampling

    private static final int F32 = MLX_FLOAT32;

    /** MLX's random key for a seed: {seed >> 32, seed & 0xffffffff} as uint32, the seed widened to 64 bits. */
    private static byte[] randomKey(int seed) {
        long wide = seed;
        return MlxMetalKernels.intBytes(new int[] { (int) (wide >>> 32), (int) wide });
    }

    /** RandomBits with one row-contiguous key: {@code n} uint32 words into {@code out}. */
    private static void randomBits(Program p, int seed, long n, Ref out) {
        long bytes = 4 * n;
        long words = (bytes + 3) / 4;
        long half = words / 2;
        boolean odd = words % 2 == 1;
        p.launch("rbitsc").bytes(0, randomKey(seed)).buffer(1, out).bytes(2, new byte[] { (byte) (odd ? 1 : 0) }).i64(3, bytes).blocks(1, half + (odd ? 1 : 0), 1);
    }

    /**
     * uniform(low, high, float32): bits / UINT32_MAX, capped just below one, then range * u + low. {@code low}
     * and {@code range} are scalars already in float32 buffers (as MLX computes them) or constants.
     */
    private static void uniform(Program p, int seed, int n, Ref low, Ref range, double lowValue, double rangeValue, Ref out, Ref scratch) {
        randomBits(p, seed, n, scratch);
        p.copy(MlxMetalKernels.MLX_UINT32, F32, n, scratch, out);
        p.binaryScalarRight("Divide", F32, n, out, 4294967295.0, out);
        p.binaryScalarRight("Minimum", F32, n, out, Math.nextDown(1.0f), out);
        if (range != null) {
            p.binaryScalarLeft("Multiply", F32, n, range, out, out);
        } else {
            p.binaryScalarLeft("Multiply", F32, n, rangeValue, out, out);
        }
        if (low != null) {
            p.binaryScalarRight("Add", F32, n, out, low, out);
        } else {
            p.binaryScalarRight("Add", F32, n, out, lowValue, out);
        }
    }

    /** uniform over (nextafter(-1, 0), 1): the base of normal and laplace. */
    private static void symmetricUniform(Program p, int seed, int n, Ref out, Ref scratch) {
        float low = Math.nextUp(-1.0f);
        uniform(p, seed, n, null, null, low, 1.0f - low, out, scratch);
    }

    private static int seedArg(View v, int index) {
        return v.intArg(index);
    }

    private static void registerRandomRoutes() {
        ROUTES.put("mlx_random_bits", v -> v.args() < 2 || !v.isArray(0) || v.dtype(0) != MLX_INT32 ? null : p -> randomBits(p, seedArg(v, 1), v.size(0), v.ref(0)));
        ROUTES.put("mlx_random_uniform", v -> {
            if (v.args() < 4 || !v.isArray(0) || v.dtype(0) != F32) {
                return null;
            }
            float low = v.floatArg(1);
            float high = v.floatArg(2);
            int n = v.size(0);
            return p -> uniform(p, seedArg(v, 3), n, null, null, low, high - low, v.ref(0), p.scratch(4L * n));
        });
        // normal(loc, scale): sqrt(2) * erfinv(u) (times scale when it is not one), plus loc when it is not zero.
        ROUTES.put("mlx_random_normal", v -> {
            if (v.args() < 4 || !v.isArray(0) || v.dtype(0) != F32) {
                return null;
            }
            float loc = v.floatArg(1);
            float scale = v.floatArg(2);
            int n = v.size(0);
            float applied = scale == 1.0f ? (float) Math.sqrt(2.0) : (float) Math.sqrt(2.0) * scale;
            return p -> {
                symmetricUniform(p, seedArg(v, 3), n, v.ref(0), p.scratch(4L * n));
                p.unary("ErfInv", F32, F32, n, v.ref(0), v.ref(0));
                p.binaryScalarLeft("Multiply", F32, n, applied, v.ref(0), v.ref(0));
                if (loc != 0.0f) {
                    p.binaryScalarLeft("Add", F32, n, loc, v.ref(0), v.ref(0));
                }
            };
        });
        // normal with per-element loc and scale arrays.
        ROUTES.put("mlx_random_normal_broadcast", v -> {
            if (v.args() < 4 || floatType(v, 0, 1, 2) != F32 || v.size(0) != v.size(2) || v.size(1) != v.size(2)) {
                return null;
            }
            int n = v.size(2);
            return p -> {
                Ref scale = p.scratch(4L * n);
                symmetricUniform(p, seedArg(v, 3), n, v.ref(2), p.scratch(4L * n));
                p.binaryScalarLeft("Multiply", F32, n, (float) Math.sqrt(2.0), v.ref(1), scale);
                p.unary("ErfInv", F32, F32, n, v.ref(2), v.ref(2));
                p.binary("Multiply", F32, n, scale, v.ref(2), v.ref(2));
                p.binary("Add", F32, n, v.ref(0), v.ref(2), v.ref(2));
            };
        });
        // bernoulli(p): bits < p * nextafter(UINT32_MAX as float, max).
        ROUTES.put("mlx_random_bernoulli", v -> {
            if (v.args() < 3 || !v.isArray(0) || v.dtype(0) != F32 || !v.is(1, MLX_UINT8, v.size(0))) {
                return null;
            }
            int n = v.size(0);
            float upper = Math.nextUp((float) 4294967295.0);
            return p -> {
                Ref bits = p.scratch(4L * n);
                Ref threshold = p.scratch(4L * n);
                randomBits(p, seedArg(v, 2), n, bits);
                p.binaryScalarRight("Multiply", F32, n, v.ref(0), upper, threshold);
                p.copy(MlxMetalKernels.MLX_UINT32, F32, n, bits, bits);
                p.binary("Less", F32, n, bits, threshold, v.ref(1));
            };
        });
        // randint(low, high): floor of a float32 uniform, clipped to [low, high - 1].
        ROUTES.put("mlx_random_randint", v -> {
            if (v.args() < 4 || !v.isArray(0) || v.dtype(0) != MLX_INT32) {
                return null;
            }
            int low = v.intArg(1);
            int high = v.intArg(2);
            int n = v.size(0);
            return p -> {
                Ref u = p.scratch(4L * n);
                uniform(p, seedArg(v, 3), n, null, null, (float) low, (float) high - (float) low, u, p.scratch(4L * n));
                p.unary("Floor", F32, F32, n, u, u);
                p.copy(F32, MLX_INT32, n, u, v.ref(0));
                p.binaryScalarRight("Minimum", MLX_INT32, n, v.ref(0), high - 1, v.ref(0));
                p.binaryScalarRight("Maximum", MLX_INT32, n, v.ref(0), low, v.ref(0));
            };
        });
        // truncated_normal(lower, upper): a uniform between erf(lower / sqrt2) and erf(upper / sqrt2), mapped back and clipped.
        ROUTES.put("mlx_random_truncated_normal", v -> {
            if (v.args() < 4 || !v.isArray(0) || v.dtype(0) != F32) {
                return null;
            }
            float lower = v.floatArg(1);
            float upper = v.floatArg(2);
            float sqrt2 = (float) Math.sqrt(2.0);
            int n = v.size(0);
            return p -> {
                Ref bounds = p.scratch(16);
                Ref a = bounds;
                Ref b = bounds.plus(1, F32);
                Ref range = bounds.plus(2, F32);
                p.fill(F32, 1, lower, a);
                p.fill(F32, 1, upper, b);
                p.binaryScalarRight("Divide", F32, 1, a, sqrt2, a);
                p.binaryScalarRight("Divide", F32, 1, b, sqrt2, b);
                p.unary("Erf", F32, F32, 1, a, a);
                p.unary("Erf", F32, F32, 1, b, b);
                p.binary("Subtract", F32, 1, b, a, range);
                uniform(p, seedArg(v, 3), n, a, range, 0, 0, v.ref(0), p.scratch(4L * n));
                p.unary("ErfInv", F32, F32, n, v.ref(0), v.ref(0));
                p.binaryScalarLeft("Multiply", F32, n, sqrt2, v.ref(0), v.ref(0));
                p.binaryScalarLeft("Minimum", F32, n, upper, v.ref(0), v.ref(0));
                p.binaryScalarRight("Maximum", F32, n, v.ref(0), lower, v.ref(0));
            };
        });
        // gumbel: -log(-log(uniform(0, 1))).
        ROUTES.put("mlx_random_gumbel", v -> {
            if (v.args() < 2 || !v.isArray(0) || v.dtype(0) != F32) {
                return null;
            }
            int n = v.size(0);
            return p -> gumbel(p, seedArg(v, 1), n, v.ref(0), p.scratch(4L * n));
        });
        // laplace(loc, scale): sign(u) * log1p(-|u|), scaled and shifted.
        ROUTES.put("mlx_random_laplace", v -> {
            if (v.args() < 4 || !v.isArray(0) || v.dtype(0) != F32) {
                return null;
            }
            float loc = v.floatArg(1);
            float scale = v.floatArg(2);
            int n = v.size(0);
            return p -> {
                Ref sign = p.scratch(4L * n);
                symmetricUniform(p, seedArg(v, 3), n, v.ref(0), p.scratch(4L * n));
                p.unary("Sign", F32, F32, n, v.ref(0), sign);
                p.unary("Abs", F32, F32, n, v.ref(0), v.ref(0));
                p.binaryScalarLeft("Multiply", F32, n, -1.0f, v.ref(0), v.ref(0));
                p.unary("Log1p", F32, F32, n, v.ref(0), v.ref(0));
                p.binary("Multiply", F32, n, sign, v.ref(0), v.ref(0));
                if (scale != 1.0f) {
                    p.binaryScalarLeft("Multiply", F32, n, scale, v.ref(0), v.ref(0));
                }
                if (loc != 0.0f) {
                    p.binaryScalarLeft("Add", F32, n, loc, v.ref(0), v.ref(0));
                }
            };
        });
        // permutation of arange(n): argsort of n random words.
        ROUTES.put("mlx_random_permutation_arange", v -> {
            if (v.args() < 2 || !v.isArray(0) || v.dtype(0) != MLX_INT32) {
                return null;
            }
            int n = v.size(0);
            return p -> {
                Ref bits = p.scratch(4L * n);
                randomBits(p, seedArg(v, 1), n, bits);
                mergeSort(p, MlxMetalKernels.MLX_UINT32, 1, n, 1, bits, v.ref(0), true);
            };
        });
        // categorical: argmax of logits plus Gumbel noise (MLX's path when the logits have more than one row).
        ROUTES.put("mlx_random_categorical", v -> {
            if (v.args() < 5) {
                return null;
            }
            int rows = v.intArg(2);
            int classes = v.intArg(3);
            return categorical(v, rows, classes, 1, v.intArg(4), false);
        });
        ROUTES.put("mlx_random_categorical_num_samples", v -> {
            if (v.args() < 6) {
                return null;
            }
            return categorical(v, v.intArg(2), v.intArg(3), v.intArg(4), v.intArg(5), false);
        });
        ROUTES.put("mlx_random_categorical_shape", v -> {
            if (v.args() < 6) {
                return null;
            }
            return categorical(v, v.intArg(2), v.intArg(3), v.intArg(4), v.intArg(5), true);
        });
    }

    private static void gumbel(Program p, int seed, int n, Ref out, Ref scratch) {
        uniform(p, seed, n, null, null, 0.0, 1.0, out, scratch);
        p.unary("Log", F32, F32, n, out, out);
        p.unary("Negative", F32, F32, n, out, out);
        p.unary("Log", F32, F32, n, out, out);
        p.unary("Negative", F32, F32, n, out, out);
    }

    /**
     * categorical over logits[rows, classes] with {@code samples} draws per row: Gumbel noise shaped as MLX
     * inserts the class axis, added to the broadcast logits, then argmax over the class axis. {@code shapeForm}
     * orders the output [samples, rows] (categorical_shape); otherwise [rows, samples].
     */
    private static Encoder categorical(View v, int rows, int classes, int samples, int seed, boolean shapeForm) {
        if (!v.isArray(0) || v.dtype(0) != F32 || !v.isArray(1) || v.dtype(1) != MLX_INT32 || rows <= 1 || classes <= 0 || samples <= 0 || v.size(0) != rows * classes
                || v.size(1) != rows * samples) {
            return null;
        }
        long total = (long) rows * classes * samples;
        return p -> {
            Ref noisy = p.scratch(4L * total);
            gumbel(p, seed, (int) total, noisy, p.scratch(4L * total));
            if (shapeForm) {
                // noise [samples, rows, classes] + logits[None, rows, classes], argmax over the last axis.
                p.generalBinary("Add", F32, new int[] { samples, rows, classes }, new long[] { (long) rows * classes, classes, 1 }, noisy, new long[] { 0, classes, 1 }, v.ref(0),
                        noisy);
                argReduceInto(p, "argmax", F32, (long) samples * rows, classes, 1, noisy, v.ref(1));
            } else {
                // noise [rows, classes, samples] + logits[rows, classes, None], argmax over the middle axis.
                p.generalBinary("Add", F32, new int[] { rows, classes, samples }, new long[] { (long) classes * samples, samples, 1 }, noisy, new long[] { classes, 1, 0 },
                        v.ref(0), noisy);
                argReduceInto(p, "argmax", F32, rows, classes, samples, noisy, v.ref(1));
            }
        };
    }

    // ---------------------------------------------------------------- fft helpers, norms, cross

    /** sqrt(sum(square(x))) over contiguous rows. */
    private static void l2Rows(Program p, int t, long rows, long len, Ref x, Ref out, Ref scratch) {
        p.unary("Square", t, t, (int) (rows * len), x, scratch);
        rowReduce(p, "sum", t, rows, len, scratch, out);
        p.unary("Sqrt", t, t, (int) rows, out, out);
    }

    private static void registerSmallCompositeRoutes() {
        // fftshift / ifftshift along axis 1 of x[r, c]: roll by c / 2 or -(c / 2).
        ROUTES.put("mlx_fft_fftshift", v -> v.args() < 4 ? null : roll2(v, v.intArg(2), v.intArg(3), 0, v.intArg(3) / 2));
        ROUTES.put("mlx_fft_ifftshift", v -> v.args() < 4 ? null : roll2(v, v.intArg(2), v.intArg(3), 0, -(v.intArg(3) / 2)));
        // fftfreq(n, d): [arange(0, (n + 1) / 2), arange(-(n / 2), 0)] * float(1 / (n * d)).
        ROUTES.put("mlx_fft_fftfreq", v -> {
            if (v.args() < 3 || !v.isArray(0) || v.dtype(0) != F32 || v.size(0) != v.intArg(1) || v.intArg(1) <= 0) {
                return null;
            }
            int n = v.intArg(1);
            float scale = (float) (1.0 / ((double) n * v.floatArg(2)));
            int positive = (n + 1) / 2;
            return p -> {
                p.arange(F32, positive, 0, 1, v.ref(0));
                if (n / 2 > 0) {
                    p.arange(F32, n / 2, -(n / 2), 1, v.ref(0).plus(positive, F32));
                }
                p.binaryScalarRight("Multiply", F32, n, v.ref(0), scale, v.ref(0));
            };
        });
        ROUTES.put("mlx_fft_rfftfreq", v -> {
            if (v.args() < 3 || !v.isArray(0) || v.dtype(0) != F32 || v.intArg(1) <= 0 || v.size(0) != v.intArg(1) / 2 + 1) {
                return null;
            }
            int count = v.size(0);
            float scale = (float) (1.0 / ((double) v.intArg(1) * v.floatArg(2)));
            return p -> {
                p.arange(F32, count, 0, 1, v.ref(0));
                p.binaryScalarRight("Multiply", F32, count, v.ref(0), scale, v.ref(0));
            };
        });
        // norm(x[r, c], ord) over the last axis.
        ROUTES.put("mlx_linalg_norm", v -> {
            if (v.args() < 5 || !v.isArray(0) || v.dtype(0) != F32 || !v.is(1, F32, v.intArg(2))) {
                return null;
            }
            long r = v.intArg(2);
            long c = v.intArg(3);
            double ord = v.floatArg(4);
            if (r * c != v.size(0)) {
                return null;
            }
            int n = v.size(0);
            return p -> {
                Ref tmp = p.scratch(4L * n);
                if (ord == 0.0) {
                    Ref counts = p.scratch(4 * r);
                    p.binaryScalarRight("NotEqual", F32, n, v.ref(0), 0.0, tmp);
                    rowReduce(p, "sum", MLX_BOOL, r, c, tmp, counts);
                    p.copy(MLX_INT32, F32, (int) r, counts, v.ref(1));
                } else if (ord == 2.0) {
                    l2Rows(p, F32, r, c, v.ref(0), v.ref(1), tmp);
                } else {
                    p.unary("Abs", F32, F32, n, v.ref(0), tmp);
                    if (ord == 1.0) {
                        rowReduce(p, "sum", F32, r, c, tmp, v.ref(1));
                    } else if (ord == Double.POSITIVE_INFINITY) {
                        rowReduce(p, "max", F32, r, c, tmp, v.ref(1));
                    } else if (ord == Double.NEGATIVE_INFINITY) {
                        rowReduce(p, "min", F32, r, c, tmp, v.ref(1));
                    } else {
                        p.binaryScalarRight("Power", F32, n, tmp, ord, tmp);
                        rowReduce(p, "sum", F32, r, c, tmp, v.ref(1));
                        p.binaryScalarRight("Power", F32, (int) r, v.ref(1), 1.0 / ord, v.ref(1));
                    }
                }
            };
        });
        // l2 norm over the last axis of x[r, c]; Frobenius norm of each matrix of x[b, r, c].
        ROUTES.put("mlx_linalg_norm_l2", v -> {
            if (v.args() < 4 || !v.isArray(0) || !FLOATS.contains(v.dtype(0)) || !v.is(1, v.dtype(0), v.intArg(2)) || (long) v.intArg(2) * v.intArg(3) != v.size(0)) {
                return null;
            }
            int t = v.dtype(0);
            return p -> l2Rows(p, t, v.intArg(2), v.intArg(3), v.ref(0), v.ref(1), p.scratch((long) v.size(0) * MlxMetalKernels.itemSize(t)));
        });
        ROUTES.put("mlx_linalg_norm_matrix", v -> {
            if (v.args() < 5 || !v.isArray(0) || !FLOATS.contains(v.dtype(0)) || !v.is(1, v.dtype(0), v.intArg(2))
                    || (long) v.intArg(2) * v.intArg(3) * v.intArg(4) != v.size(0)) {
                return null;
            }
            int t = v.dtype(0);
            return p -> l2Rows(p, t, v.intArg(2), (long) v.intArg(3) * v.intArg(4), v.ref(0), v.ref(1), p.scratch((long) v.size(0) * MlxMetalKernels.itemSize(t)));
        });
        // cross(a[count, 3], b[count, 3]) along the last axis, component by component as MLX splits and concatenates.
        ROUTES.put("mlx_linalg_cross", v -> {
            int t = floatType(v, 0, 1, 2);
            if (t < 0 || v.args() < 4 || v.size(0) != 3 * v.intArg(3) || v.size(1) != v.size(0) || v.size(2) != v.size(0)) {
                return null;
            }
            int count = v.intArg(3);
            return p -> {
                int item = MlxMetalKernels.itemSize(t);
                Ref left = p.scratch((long) count * item);
                Ref right = p.scratch((long) count * item);
                int[][] terms = { { 1, 2, 2, 1 }, { 2, 0, 0, 2 }, { 0, 1, 1, 0 } };
                for (int component = 0; component < 3; component++) {
                    int[] q = terms[component];
                    p.generalBinary("Multiply", t, new int[] { count }, new long[] { 3 }, v.ref(0).plus(q[0], t), new long[] { 3 }, v.ref(1).plus(q[1], t), left);
                    p.generalBinary("Multiply", t, new int[] { count }, new long[] { 3 }, v.ref(0).plus(q[2], t), new long[] { 3 }, v.ref(1).plus(q[3], t), right);
                    p.binary("Subtract", t, count, left, right, left);
                    p.generalCopy(t, t, new int[] { count }, new long[] { 1 }, left, new long[] { 3 }, v.ref(2).plus(component, t), null);
                }
            };
        });
    }

    // ---------------------------------------------------------------- FFT (Stockham plans)

    private static final int[] RADICES = { 13, 11, 8, 7, 6, 5, 4, 3, 2 };

    private static boolean powerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /** MLX's plan_stockham_fft: steps per radix, or null if n does not factor over the radices. */
    private static int[] stockhamPlan(int n) {
        int[] plan = new int[RADICES.length];
        int remaining = n;
        if (n == 1) {
            return plan;
        }
        for (int i = 0; i < RADICES.length; i++) {
            int radix = RADICES[i];
            if (powerOfTwo(n) && n < 512 && radix > 4) {
                continue;
            }
            while (remaining % radix == 0) {
                plan[i]++;
                remaining /= radix;
                if (remaining == 1) {
                    return plan;
                }
            }
        }
        return null;
    }

    /** MLX's compute_elems_per_thread for a Stockham-only plan. */
    private static int elementsPerThread(int n, int[] plan) {
        java.util.TreeSet<Integer> used = new java.util.TreeSet<>();
        for (int i = 0; i < plan.length; i++) {
            if (plan[i] > 0) {
                used.add(RADICES[i]);
            }
        }
        if (used.contains(7) && (used.contains(11) || used.contains(13))) {
            return 7;
        }
        if (used.contains(11) && used.contains(13)) {
            return 11;
        }
        switch (n) {
            case 3159:
                return 13;
            case 3645:
                return 5;
            case 3969:
                return 7;
            case 1982:
                return 5;
            default:
                break;
        }
        if (used.size() == 1) {
            return used.first();
        }
        Integer[] sorted = used.toArray(new Integer[0]);
        if (used.size() == 2 && (used.contains(11) || used.contains(13))) {
            return (sorted[0] + sorted[1]) / 2;
        }
        return sorted[1];
    }

    /**
     * One MLX fft_op pass over contiguous rows of length n (total batch rows): the Stockham fft_mem kernel,
     * or false when MLX would plan Rader, Bluestein or four-step for n (sizes above 4096 or with other prime factors).
     */
    private static boolean fftPass(Program p, int n, int batch, boolean inverse, boolean real, Ref in, Ref out, boolean dryRun) {
        if (n > 4096) {
            return false;
        }
        int[] plan = stockhamPlan(n);
        if (plan == null) {
            return false;
        }
        if (dryRun) {
            return true;
        }
        int ept = elementsPerThread(n, plan);
        int threadsPerFft = (n + ept - 1) / ept;
        int groupBatch = Math.max(256 / n, 1);
        int memory = nextPowerOfTwo(groupBatch * n);
        int batchSize = (batch + groupBatch - 1) / groupBatch;
        if (real) {
            batchSize = (batchSize + 1) / 2;
        }
        String inType = real && !inverse ? "float" : "float2";
        String outType = real && inverse ? "float" : "float2";
        int[] indices = new int[4 + 2 * RADICES.length];
        Object[] values = new Object[indices.length];
        indices[0] = 0;
        values[0] = inverse;
        indices[1] = 1;
        values[1] = powerOfTwo(n);
        for (int i = 0; i < RADICES.length; i++) {
            indices[2 + i] = 4 + i;
            values[2 + i] = plan[i];
            indices[2 + RADICES.length + i] = 4 + RADICES.length + i;
            values[2 + RADICES.length + i] = 0;
        }
        indices[2 + 2 * RADICES.length] = 2;
        values[2 + 2 * RADICES.length] = ept;
        indices[3 + 2 * RADICES.length] = 3;
        values[3 + 2 * RADICES.length] = n;
        p.launchConstants("fft_mem_" + memory + "_" + inType + "_" + outType, indices, values).buffer(0, in).buffer(1, out).i64(2, n).i32(3, batch)
                .threads(batchSize, 1, groupBatch, groupBatch, threadsPerFft, threadsPerFft);
        return true;
    }

    /** MLX's fft_scale_factor for norm (0 backward, 1 ortho, 2 forward) over n elements. */
    private static double fftScale(int norm, double n, boolean inverse) {
        return switch (norm) {
            case 1 -> inverse ? Math.sqrt(n) : 1.0 / Math.sqrt(n);
            case 2 -> inverse ? n : 1.0 / n;
            default -> 1.0;
        };
    }

    /** fft / ifft / rfft / irfft (x, out, rows, len, n, norm) along the last axis of [rows, len]. */
    private static Encoder fft1d(View v, boolean inverse, boolean real) {
        if (v.args() < 6 || !v.isArray(0) || !v.isArray(1) || v.dtype(0) != F32 || v.dtype(1) != F32) {
            return null;
        }
        int rows = v.intArg(2);
        int len = v.intArg(3);
        int n = v.intArg(4);
        int norm = v.intArg(5);
        int inLen = real && inverse ? n / 2 + 1 : n;
        int outLen = real && !inverse ? n / 2 + 1 : n;
        boolean complexIn = !(real && !inverse);
        boolean complexOut = !(real && inverse);
        if (len != inLen || n <= 1 || v.size(0) != rows * inLen * (complexIn ? 2 : 1) || v.size(1) != rows * outLen * (complexOut ? 2 : 1) || !fftPass(null, n, rows, inverse, real,
                null, null, true)) {
            return null;
        }
        double scale = fftScale(norm, n, inverse);
        int outType = complexOut ? MLX_COMPLEX64 : F32;
        return p -> {
            fftPass(p, n, rows, inverse, real, v.ref(0), v.ref(1), false);
            if (scale != 1.0) {
                p.binaryScalarRight("Multiply", outType, rows * outLen, v.ref(1), scale, v.ref(1));
            }
        };
    }

    /** An array in an FFT chain: its buffer, logical shape and element strides (complex elements for complex data). */
    private record FftArray(Ref ref, int[] shape, long[] strides) {
        long size() {
            long n = 1;
            for (int d : shape) {
                n *= d;
            }
            return n;
        }
    }

    /** MLX's check_contiguity: {row contiguous, column contiguous}. */
    private static boolean[] contiguity(int[] shape, long[] strides) {
        long f = 1;
        long b = 1;
        boolean row = true;
        boolean col = true;
        for (int i = 0, ri = shape.length - 1; ri >= 0; i++, ri--) {
            col &= strides[i] == f || shape[i] == 1;
            row &= strides[ri] == b || shape[ri] == 1;
            f *= shape[i];
            b *= shape[ri];
        }
        return new boolean[] { row, col };
    }

    /**
     * One pass of MLX's nd_fft_op / fft_op along {@code axis}: relayout the input so the axis has stride 1 when
     * MLX would (a copy, which moves data only), run the Stockham kernel over its rows, and return the output
     * with the strides MLX gives it.
     */
    private static FftArray fftAxisPass(Program p, FftArray in, int axis, boolean inverse, boolean real, int n, int inType, Ref outBuffer) {
        boolean[] c = contiguity(in.shape(), in.strides());
        FftArray source = in;
        if (!(in.strides()[axis] == 1 && (c[0] || c[1]))) {
            long[] strides = new long[in.shape().length];
            long current = in.shape()[axis];
            for (int d = 0; d < strides.length; d++) {
                if (d == axis) {
                    strides[d] = 1;
                } else {
                    strides[d] = current;
                    current *= in.shape()[d];
                }
            }
            Ref copy = p.scratch(in.size() * MlxMetalKernels.itemSize(inType));
            p.generalCopy(inType, inType, in.shape(), in.strides(), in.ref(), strides, copy, null);
            source = new FftArray(copy, in.shape(), strides);
        }
        int inLen = in.shape()[axis];
        int outLen = real ? (inverse ? n : n / 2 + 1) : inLen;
        int[] outShape = in.shape().clone();
        outShape[axis] = outLen;
        long[] outStrides = source.strides().clone();
        if (inLen != outLen) {
            for (int d = 0; d < outStrides.length; d++) {
                if (outStrides[d] != 1) {
                    outStrides[d] = outStrides[d] / inLen * outLen;
                }
            }
        }
        long total = real && inverse ? new FftArray(null, outShape, outStrides).size() : source.size();
        fftPass(p, n, (int) (total / n), inverse, real, source.ref(), outBuffer, false);
        return new FftArray(outBuffer, outShape, outStrides);
    }

    /**
     * fft2 / fftn and their inverse and real forms over axes 1.. of x[batch, d1, .., dk] (x, out, batch, d1, .., dk, norm):
     * the 1D passes in MLX's order (last axis first forward, first axis first inverse), the real transform on the last axis.
     */
    private static Encoder fftNd(View v, int dims, boolean inverse, boolean real) {
        if (v.args() < 4 + dims || !v.isArray(0) || !v.isArray(1) || v.dtype(0) != F32 || v.dtype(1) != F32) {
            return null;
        }
        int[] sizes = new int[dims + 1];
        for (int i = 0; i <= dims; i++) {
            sizes[i] = v.intArg(2 + i);
        }
        int norm = v.intArg(3 + dims);
        int[] inShape = sizes.clone();
        int[] outShape = sizes.clone();
        if (real && inverse) {
            inShape[dims] = sizes[dims] / 2 + 1;
        }
        if (real && !inverse) {
            outShape[dims] = sizes[dims] / 2 + 1;
        }
        boolean complexIn = !(real && !inverse);
        boolean complexOut = !(real && inverse);
        long inCount = 1;
        long outCount = 1;
        double elements = 1;
        for (int i = 0; i <= dims; i++) {
            inCount *= inShape[i];
            outCount *= outShape[i];
            if (i > 0) {
                elements *= sizes[i];
                if (sizes[i] <= 1 || !fftPass(null, sizes[i], 1, false, false, null, null, true)) {
                    return null;
                }
            }
        }
        if (v.size(0) != inCount * (complexIn ? 2 : 1) || v.size(1) != outCount * (complexOut ? 2 : 1)) {
            return null;
        }
        double scale = fftScale(norm, elements, inverse);
        int c64 = MLX_COMPLEX64;
        int outElements = (int) outCount;
        return p -> {
            FftArray current = new FftArray(v.ref(0), inShape, MlxMetalKernels.contiguousStrides(inShape));
            int currentType = complexIn ? c64 : F32;
            for (int i = dims - 1; i >= 0; i--) {
                int reverse = dims - i - 1;
                int index = inverse ? reverse : i;
                int axis = 1 + index;
                boolean stepReal = real && index == dims - 1;
                int n = sizes[axis];
                int passOutType = stepReal && inverse ? F32 : c64;
                long passOut = 1;
                for (int d = 0; d <= dims; d++) {
                    passOut *= d == axis ? (stepReal ? (inverse ? n : n / 2 + 1) : current.shape()[d]) : current.shape()[d];
                }
                Ref buffer = p.scratch(passOut * MlxMetalKernels.itemSize(passOutType));
                current = fftAxisPass(p, current, axis, inverse, stepReal, n, currentType, buffer);
                currentType = passOutType;
            }
            // The result has MLX's strides; the output buffer is row-major.
            int outType = complexOut ? c64 : F32;
            p.generalCopy(outType, outType, current.shape(), current.strides(), current.ref(), null, v.ref(1), null);
            if (scale != 1.0) {
                p.binaryScalarRight("Multiply", outType, outElements, v.ref(1), scale, v.ref(1));
            }
        };
    }

    private static void registerFftRoutes() {
        ROUTES.put("mlx_fft_fft", v -> fft1d(v, false, false));
        ROUTES.put("mlx_fft_ifft", v -> fft1d(v, true, false));
        ROUTES.put("mlx_fft_rfft", v -> fft1d(v, false, true));
        ROUTES.put("mlx_fft_irfft", v -> fft1d(v, true, true));
        ROUTES.put("mlx_fft_fft2", v -> fftNd(v, 2, false, false));
        ROUTES.put("mlx_fft_ifft2", v -> fftNd(v, 2, true, false));
        ROUTES.put("mlx_fft_rfft2", v -> fftNd(v, 2, false, true));
        ROUTES.put("mlx_fft_irfft2", v -> fftNd(v, 2, true, true));
        ROUTES.put("mlx_fft_fftn", v -> fftNd(v, 3, false, false));
        ROUTES.put("mlx_fft_ifftn", v -> fftNd(v, 3, true, false));
        ROUTES.put("mlx_fft_rfftn", v -> fftNd(v, 3, false, true));
        ROUTES.put("mlx_fft_irfftn", v -> fftNd(v, 3, true, true));
    }

    // ---------------------------------------------------------------- convolutions

    /** MLXConvParams for two spatial dimensions (1D convolutions use a unit second dimension). */
    private record Conv2(int n, int c, int o, int[] is, int[] ws, int[] os, int[] str, int[] pad, int[] kdil, int[] idil, long[] inStrides, long[] wtStrides, long[] outStrides,
            int groups, boolean flip) {
        byte[] bytes() {
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(176).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            b.putInt(n).putInt(c).putInt(o);
            for (int[] a : new int[][] { is, ws, os, str, pad, kdil, idil }) {
                b.putInt(a[0]).putInt(a[1]);
            }
            b.putInt(0);
            for (long[] a : new long[][] { inStrides, wtStrides, outStrides }) {
                for (long x : a) {
                    b.putLong(x);
                }
            }
            b.putInt(groups).put((byte) (flip ? 1 : 0));
            return b.array();
        }
    }

    /** MLX's implicit_gemm_conv_2D_gpu, or null when the kernel it would pick is not in the metallib. */
    private static Encoder implicitGemmConv2(View v, int t, Conv2 cp) {
        int cpg = cp.c() / cp.groups();
        int opg = cp.o() / cp.groups();
        long implicitM = (long) cp.n() * cp.os()[0] * cp.os()[1];
        int implicitN = opg;
        int implicitK = cp.ws()[0] * cp.ws()[1] * cpg;
        int wm = 2;
        int wn = 2;
        int bm = implicitM >= 8192 && cpg >= 64 ? 64 : 32;
        int bn = (bm == 64 || implicitN >= 64) ? 64 : 32;
        int bk = 16;
        if (implicitN <= 16) {
            bn = 8;
            wm = 4;
            wn = 1;
        }
        int tn = (implicitN + bn - 1) / bn;
        int tm = (int) ((implicitM + bm - 1) / bm);
        int channelSpecialization = 0;
        int kIters = cp.ws()[0] * cp.ws()[1] * ((cpg + bk - 1) / bk);
        if (cpg <= 2) {
            kIters = (implicitK + bk - 1) / bk;
            channelSpecialization = cpg;
        } else if (cpg <= 4) {
            kIters = ((cp.ws()[0] * cp.ws()[1] * 4) + bk - 1) / bk;
            channelSpecialization = cpg;
        }
        boolean smallFilter = channelSpecialization == 0 && cp.ws()[0] <= 16 && cp.ws()[1] <= 16;
        int sign = cp.flip() ? -1 : 1;
        int ijw = (int) (cp.inStrides()[2] * cp.kdil()[1]);
        int ijh = (int) (cp.inStrides()[1] * cp.kdil()[0]);
        int jumpW = sign * ijw;
        int jumpH = sign * (ijh - (cp.ws()[1] - 1) * ijw);
        int jumpC = bk - sign * (cp.ws()[0] - 1) * ijh - sign * (cp.ws()[1] - 1) * ijw;
        String kernel = "implicit_gemm_conv_2d_" + MlxMetalKernels.typeName(t) + "_bm" + bm + "_bn" + bn + "_bk" + bk + "_wm" + wm + "_wn" + wn + "_channel_"
                + (channelSpecialization > 0 ? Integer.toString(channelSpecialization) : "l") + "_filter_" + (smallFilter ? "s" : "l");
        if (implicitM > Integer.MAX_VALUE || !v.hasKernel(kernel)) {
            return null;
        }
        java.nio.ByteBuffer gemm = java.nio.ByteBuffer.allocate(40).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        gemm.putInt((int) implicitM).putInt(implicitN).putInt(implicitK).putInt(kIters).putInt(jumpW).putInt(jumpH).putInt(jumpC).putInt(tn).putInt(tm).putInt(0);
        byte[] gemmBytes = gemm.array();
        byte[] convBytes = cp.bytes();
        int gy = wn;
        int gz = wm;
        return p -> p.launch(kernel).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2)).bytes(3, convBytes).bytes(4, gemmBytes).threadgroups(tn, tm, cp.groups(), 32, gy, gz);
    }

    /** MLX's dispatch_conv_2D_gpu, for the strategies reproduced here (implicit GEMM); null otherwise. */
    private static Encoder dispatchConv2(View v, int t, Conv2 cp) {
        boolean strideOne = cp.str()[0] == 1 && cp.str()[1] == 1;
        boolean kdilOne = cp.kdil()[0] == 1 && cp.kdil()[1] == 1;
        boolean idilOne = cp.idil()[0] == 1 && cp.idil()[1] == 1;
        int cpg = cp.c() / cp.groups();
        int opg = cp.o() / cp.groups();
        if (cp.groups() > 1) {
            if (!idilOne) {
                return null;
            }
            boolean depthwise = cpg == 1 && opg == 1 && kdilOne && cp.ws()[0] <= 7 && cp.ws()[1] <= 7 && cp.str()[0] <= 2 && cp.str()[1] <= 2 && cp.wtStrides()[1] == cp.ws()[1]
                    && cp.c() % 16 == 0 && cp.c() == cp.o();
            if (depthwise) {
                return null;
            }
            return (cpg <= 4 || cpg % 16 == 0) && (opg <= 16 || opg % 16 == 0) ? implicitGemmConv2(v, t, cp) : null;
        }
        boolean inputLarge = (long) cp.n() * cp.is()[0] * cp.is()[1] >= 4096;
        boolean channelsLarge = cp.c() + cp.o() >= 256;
        boolean outLarge = (long) cp.n() * cp.os()[0] * cp.os()[1] >= 256;
        if (!cp.flip() && strideOne && kdilOne && idilOne && cp.ws()[0] == 3 && cp.ws()[1] == 3 && cp.c() % 32 == 0 && cp.o() % 32 == 0 && inputLarge && channelsLarge) {
            return null;
        }
        boolean specialized = (cp.c() <= 4 || cp.c() % 16 == 0) && (cp.o() <= 16 || cp.o() % 16 == 0);
        boolean outAligned = cp.o() <= 16 || cp.o() % 16 == 0;
        if (idilOne && strideOne && outLarge && !specialized && outAligned && cp.ws()[0] * cp.ws()[1] >= 9) {
            return null;
        }
        if (idilOne && specialized) {
            return implicitGemmConv2(v, t, cp);
        }
        return (cp.c() % 16 == 0 && cp.o() % 16 == 0) || outLarge ? implicitGemmConv2General(v, t, cp) : null;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static int lcm(int a, int b) {
        return a / gcd(a, b) * b;
    }

    /** MLX's implicit_gemm_conv_2D_general_gpu (input dilation, as transposed convolutions produce), groups of one. */
    private static Encoder implicitGemmConv2General(View v, int t, Conv2 cp) {
        long implicitM = (long) cp.n() * cp.os()[0] * cp.os()[1];
        int implicitN = cp.o();
        int implicitK = cp.ws()[0] * cp.ws()[1] * cp.c();
        int wgtJumpH = lcm(cp.idil()[0], cp.kdil()[0]) / cp.kdil()[0];
        int wgtJumpW = lcm(cp.idil()[1], cp.kdil()[1]) / cp.kdil()[1];
        int outJumpH = lcm(cp.idil()[0], cp.str()[0]) / cp.str()[0];
        int outJumpW = lcm(cp.idil()[1], cp.str()[1]) / cp.str()[1];
        int adjOutH = (cp.os()[0] + outJumpH - 1) / outJumpH;
        int adjOutW = (cp.os()[1] + outJumpW - 1) / outJumpW;
        int adjOutHw = adjOutH * adjOutW;
        int adjM = cp.n() * adjOutHw;
        int jumpH = cp.flip() ? -cp.kdil()[0] : cp.kdil()[0];
        int jumpW = cp.flip() ? -cp.kdil()[1] : cp.kdil()[1];
        int initH = cp.flip() ? (cp.ws()[0] - 1) * cp.kdil()[0] : 0;
        int initW = cp.flip() ? (cp.ws()[1] - 1) * cp.kdil()[1] : 0;
        int[] baseH = new int[2 * outJumpH];
        for (int i = 0; i < outJumpH; i++) {
            int ih = i * cp.str()[0] - cp.pad()[0] + initH;
            int base = 0;
            while (base < cp.ws()[0] && ih % cp.idil()[0] != 0) {
                base++;
                ih += jumpH;
            }
            baseH[2 * i] = base;
            baseH[2 * i + 1] = ((cp.ws()[0] - base) + wgtJumpH - 1) / wgtJumpH;
        }
        int[] baseW = new int[2 * outJumpW];
        for (int j = 0; j < outJumpW; j++) {
            int iw = j * cp.str()[1] - cp.pad()[1] + initW;
            int base = 0;
            while (base < cp.ws()[1] && iw % cp.idil()[1] != 0) {
                base++;
                iw += jumpW;
            }
            baseW[2 * j] = base;
            baseW[2 * j + 1] = ((cp.ws()[1] - base) + wgtJumpW - 1) / wgtJumpW;
        }
        int bm = adjM >= 8192 && cp.c() >= 64 ? 64 : 32;
        int bn = (bm == 64 && implicitN >= 64) ? 64 : 32;
        int bk = 16;
        int tn = (implicitN + bn - 1) / bn;
        int tm = (adjM + bm - 1) / bm;
        boolean alignC = cp.c() % bk == 0;
        int sign = cp.flip() ? -1 : 1;
        int ijw = (int) (cp.inStrides()[2] * cp.kdil()[1]);
        int ijh = (int) (cp.inStrides()[1] * cp.kdil()[0]);
        String kernel = "implicit_gemm_conv_2d_general_" + MlxMetalKernels.typeName(t) + "_bm" + bm + "_bn" + bn + "_bk" + bk + "_wm2_wn2";
        if (implicitM > Integer.MAX_VALUE || !v.hasKernel(kernel)) {
            return null;
        }
        java.nio.ByteBuffer gemm = java.nio.ByteBuffer.allocate(40).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        gemm.putInt((int) implicitM).putInt(implicitN).putInt(implicitK).putInt((cp.c() + bk - 1) / bk).putInt(sign * ijw).putInt(sign * (ijh - (cp.ws()[1] - 1) * ijw))
                .putInt(bk - sign * (cp.ws()[0] - 1) * ijh - sign * (cp.ws()[1] - 1) * ijw).putInt(tn).putInt(tm).putInt(0);
        byte[] gemmBytes = gemm.array();
        byte[] jumpBytes = MlxMetalKernels.intBytes(new int[] { wgtJumpH, wgtJumpW, outJumpH, outJumpW, adjOutH, adjOutW, adjOutHw, adjM });
        byte[] convBytes = cp.bytes();
        int phases = outJumpH * outJumpW;
        return p -> p.launchIndexed(kernel, new int[] { 200 }, new boolean[] { alignC }).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2)).bytes(3, convBytes)
                .bytes(4, gemmBytes).bytes(5, jumpBytes).bytes(6, MlxMetalKernels.intBytes(baseH)).bytes(7, MlxMetalKernels.intBytes(baseW)).threadgroups(tn, tm, phases, 32, 2, 2);
    }

    private static int convOut(int in, int k, int stride, int padLo, int padHi, int kdil, int idil) {
        int dilatedIn = idil * (in - 1) + 1;
        int dilatedK = kdil * (k - 1) + 1;
        return (dilatedIn + padLo + padHi - dilatedK) / stride + 1;
    }

    private static void registerConvRoutes() {
        // conv1d(x[N, L, C], w[O, K, C/g], out, N, L, C, O, K, stride, pad, dil, groups).
        ROUTES.put("mlx_conv1d", v -> {
            int t = floatType(v, 0, 1, 2);
            if (t < 0 || v.args() < 12) {
                return null;
            }
            int n = v.intArg(3);
            int len = v.intArg(4);
            int c = v.intArg(5);
            int o = v.intArg(6);
            int k = v.intArg(7);
            int stride = v.intArg(8);
            int pad = v.intArg(9);
            int dil = v.intArg(10);
            int groups = v.intArg(11);
            int outLen = convOut(len, k, stride, pad, pad, dil, 1);
            if (pad < 0 || groups <= 0 || c % groups != 0 || o % groups != 0 || outLen <= 0 || v.size(0) != n * len * c || v.size(1) != o * k * (c / groups)
                    || v.size(2) != n * outLen * o) {
                return null;
            }
            if (groups == c && groups == o && stride == 1 && dil == 1 && pad == 0) {
                String kernel = "depthwise_conv_1d_" + MlxMetalKernels.typeName(t);
                return p -> p.launch(kernel).buffer(0, v.ref(0)).buffer(1, v.ref(1)).buffer(2, v.ref(2)).bytes(3, MlxMetalKernels.intBytes(new int[] { len * c, c, 1 })).i32(4, k)
                        .blocks(c, outLen, n);
            }
            int cpg = c / groups;
            int opg = o / groups;
            if (!((cpg <= 4 || cpg % 16 == 0) && (opg <= 16 || opg % 16 == 0))) {
                return null;
            }
            Conv2 cp = new Conv2(n, c, o, new int[] { len, 1 }, new int[] { k, 1 }, new int[] { outLen, 1 }, new int[] { stride, 1 }, new int[] { pad, 0 }, new int[] { dil, 1 },
                    new int[] { 1, 1 }, new long[] { (long) len * c, c, 0, 1 }, new long[] { (long) k * cpg, cpg, 0, 1 }, new long[] { (long) outLen * o, o, 0, 1 }, groups, false);
            return dispatchConv2(v, t, cp);
        });
        // conv_transpose1d(x, w, out, N, L, C, O, K, stride, pad, dil, output_pad, groups): a flipped convolution; MLX runs
        // stride 1 through the 1D-as-2D implicit GEMM path (larger strides dilate the input and take explicit GEMM).
        ROUTES.put("mlx_conv_transpose1d", v -> {
            int t = floatType(v, 0, 1, 2);
            if (t < 0 || v.args() < 13 || v.intArg(8) != 1) {
                return null;
            }
            int n = v.intArg(3);
            int len = v.intArg(4);
            int c = v.intArg(5);
            int o = v.intArg(6);
            int k = v.intArg(7);
            int pad = v.intArg(9);
            int dil = v.intArg(10);
            int groups = v.intArg(12);
            int lo = dil * (k - 1) - pad;
            int hi = transposedHighPad(len, k, 1, pad, dil, v.intArg(11));
            int outLen = convOut(len, k, 1, lo, hi, dil, 1);
            int cpg = groups > 0 ? c / groups : 0;
            int opg = groups > 0 ? o / groups : 0;
            if (lo < 0 || hi < 0 || groups <= 0 || c % groups != 0 || o % groups != 0 || outLen <= 0 || v.size(0) != n * len * c || v.size(1) != o * k * cpg
                    || v.size(2) != n * outLen * o || !((cpg <= 4 || cpg % 16 == 0) && (opg <= 16 || opg % 16 == 0))) {
                return null;
            }
            Conv2 cp = new Conv2(n, c, o, new int[] { len, 1 }, new int[] { k, 1 }, new int[] { outLen, 1 }, new int[] { 1, 1 }, new int[] { lo, 0 }, new int[] { dil, 1 },
                    new int[] { 1, 1 }, new long[] { (long) len * c, c, 0, 1 }, new long[] { (long) k * cpg, cpg, 0, 1 }, new long[] { (long) outLen * o, o, 0, 1 }, groups, true);
            return dispatchConv2(v, t, cp);
        });
        // conv2d(x[N, H, W, C], w[O, KH, KW, C/g], out, N, H, W, C, O, KH, KW, stride, pad, dil, groups) and
        // conv_general(..., stride, pad_lo, pad_hi, kernel_dil, input_dil, groups, flip) in two dimensions.
        ROUTES.put("mlx_conv2d", v -> v.args() < 14 ? null : conv2(v, v.intArg(10), v.intArg(11), v.intArg(11), v.intArg(12), 1, v.intArg(13), false));
        ROUTES.put("mlx_conv_general", v -> v.args() < 17 ? null : conv2(v, v.intArg(10), v.intArg(11), v.intArg(12), v.intArg(13), v.intArg(14), v.intArg(15), v.boolArg(16)));
        // conv_transpose2d(x, w, out, N, H, W, C, O, KH, KW, stride, pad, dil, output_pad, groups): MLX's conv_transpose_general,
        // a flipped convolution with unit stride, input dilation = stride and the padding it derives (square kernels).
        ROUTES.put("mlx_conv_transpose2d", v -> {
            if (v.args() < 15 || v.intArg(8) != v.intArg(9)) {
                return null;
            }
            int k = v.intArg(8);
            int stride = v.intArg(10);
            int pad = v.intArg(11);
            int dil = v.intArg(12);
            int outPad = v.intArg(13);
            int lo = 1 + dil * (k - 1) - pad - 1;
            int hiH = transposedHighPad(v.intArg(4), k, stride, pad, dil, outPad);
            int hiW = transposedHighPad(v.intArg(5), k, stride, pad, dil, outPad);
            return hiH != hiW ? null : conv2(v, 1, lo, hiH, dil, stride, v.intArg(14), true);
        });
    }

    /** padding_hi of MLX's conv_transpose_general for one spatial dimension. */
    private static int transposedHighPad(int in, int k, int stride, int pad, int dil, int outPad) {
        long convOut = (long) (in - 1) * stride - 2L * pad + (long) dil * (k - 1) + 1;
        long outSize = 1 + (long) stride * (in - 1);
        return (int) (convOut - outSize + pad + outPad);
    }

    private static Encoder conv2(View v, int stride, int padLo, int padHi, int kdil, int idil, int groups, boolean flip) {
        int t = floatType(v, 0, 1, 2);
        if (t < 0 || padLo < 0 || padHi < 0 || groups <= 0) {
            return null;
        }
        int n = v.intArg(3);
        int h = v.intArg(4);
        int w = v.intArg(5);
        int c = v.intArg(6);
        int o = v.intArg(7);
        int kh = v.intArg(8);
        int kw = v.intArg(9);
        if (c % groups != 0 || o % groups != 0) {
            return null;
        }
        int oh = convOut(h, kh, stride, padLo, padHi, kdil, idil);
        int ow = convOut(w, kw, stride, padLo, padHi, kdil, idil);
        int cpg = c / groups;
        if (oh <= 0 || ow <= 0 || v.size(0) != n * h * w * c || v.size(1) != o * kh * kw * cpg || v.size(2) != n * oh * ow * o) {
            return null;
        }
        Conv2 cp = new Conv2(n, c, o, new int[] { h, w }, new int[] { kh, kw }, new int[] { oh, ow }, new int[] { stride, stride }, new int[] { padLo, padLo },
                new int[] { kdil, kdil }, new int[] { idil, idil }, new long[] { (long) h * w * c, (long) w * c, c, 1 }, new long[] { (long) kh * kw * cpg, (long) kw * cpg, cpg, 1 },
                new long[] { (long) oh * ow * o, (long) ow * o, o, 1 }, groups, flip);
        return dispatchConv2(v, t, cp);
    }

    private static Encoder scaled(View v, double factor) {
        int t = v.sameType(FLOATS, 0, 1);
        return t < 0 ? null : p -> p.binaryScalarRight("Multiply", t, v.size(0), v.ref(0), factor, v.ref(1));
    }

    interface PredicateBody {
        void encode(Program p, int type, int n);
    }

    /** A predicate (a floating array, a byte array of the same length). */
    private static Encoder predicate(View v, PredicateBody body) {
        if (!v.isArray(0) || !MlxMetalKernels.isFloating(v.dtype(0)) || !v.is(1, MLX_UINT8, v.size(0))) {
            return null;
        }
        int t = v.dtype(0);
        int n = v.size(0);
        return p -> body.encode(p, t, n);
    }

    private static Encoder logical(View v, String op) {
        if (!v.isArray(0) || !v.isArray(1) || v.size(1) != v.size(0) || !v.is(2, MLX_UINT8, v.size(0))) {
            return null;
        }
        int n = v.size(0);
        int ta = v.dtype(0);
        int tb = v.dtype(1);
        if (!ANY.contains(ta) || !ANY.contains(tb)) {
            return null;
        }
        return p -> {
            Ref a = p.scratch(n);
            Ref b = p.scratch(n);
            p.copy(ta, MLX_BOOL, n, v.ref(0), a);
            p.copy(tb, MLX_BOOL, n, v.ref(1), b);
            p.binary(op, MLX_BOOL, n, a, b, v.ref(2));
        };
    }

    private static Encoder complexPart(View v, String op, boolean complexOut) {
        if (!v.isArray(0) || !v.isArray(1) || v.dtype(0) != MLX_FLOAT32 || v.dtype(1) != MLX_FLOAT32 || v.size(0) % 2 != 0) {
            return null;
        }
        int n = v.size(0) / 2;
        if (v.size(1) != (complexOut ? 2 * n : n)) {
            return null;
        }
        return p -> p.unary(op, MLX_COMPLEX64, complexOut ? MLX_COMPLEX64 : MLX_FLOAT32, n, v.ref(0), v.ref(1));
    }

    /**
     * isclose(a, b, rtol, atol, equal_nan), float32: |a - b| <= atol + rtol * |b|, false where either
     * value is infinite unless both are infinite with the same sign, and optionally true where both
     * are NaN. Other types promote through float32 in MLX and are not routed.
     */
    private static Encoder isclose(View v) {
        int t = v.sameType(FLOAT32, 0, 1);
        if (t < 0 || v.args() != 6 || !v.is(2, MLX_UINT8, v.size(0))) {
            return null;
        }
        int n = v.size(0);
        double rtol = v.floatArg(3);
        double atol = v.floatArg(4);
        boolean equalNan = v.boolArg(5);
        return p -> iscloseKernels(p, n, v.ref(0), v.ref(1), v.ref(2), rtol, atol, equalNan);
    }

    /** The kernels of isclose (float32) writing MLX bools to {@code out}. */
    private static void iscloseKernels(Program p, int n, Ref a, Ref b, Ref out, double rtol, double atol, boolean equalNan) {
        int t = MLX_FLOAT32;
        Ref rhs = p.scratch(4L * n);
        Ref lhs = p.scratch(4L * n);
        Ref m1 = p.scratch(n);
        Ref m2 = p.scratch(n);
        Ref m3 = p.scratch(n);
        p.unary("Abs", t, t, n, b, rhs);
        p.binaryScalarLeft("Multiply", t, n, rtol, rhs, rhs);
        p.binaryScalarLeft("Add", t, n, atol, rhs, rhs);
        p.binary("Subtract", t, n, a, b, lhs);
        p.unary("Abs", t, t, n, lhs, lhs);
        p.binary("LessEqual", t, n, lhs, rhs, out);
        // any_inf = |a| == inf or |b| == inf; out = out and not any_inf.
        p.unary("Abs", t, t, n, a, lhs);
        p.binaryScalarRight("Equal", t, n, lhs, INF, m1);
        p.unary("Abs", t, t, n, b, lhs);
        p.binaryScalarRight("Equal", t, n, lhs, INF, m2);
        p.binary("LogicalOr", MLX_BOOL, n, m1, m2, m1);
        p.unary("LogicalNot", MLX_BOOL, MLX_BOOL, n, m1, m1);
        p.binary("LogicalAnd", MLX_BOOL, n, out, m1, out);
        // both_inf with the same sign = a == b and |a| == inf.
        p.binary("Equal", t, n, a, b, m2);
        p.unary("Abs", t, t, n, a, lhs);
        p.binaryScalarRight("Equal", t, n, lhs, INF, m3);
        p.binary("LogicalAnd", MLX_BOOL, n, m2, m3, m2);
        p.binary("LogicalOr", MLX_BOOL, n, out, m2, out);
        if (equalNan) {
            p.binary("NotEqual", t, n, a, a, m2);
            p.binary("NotEqual", t, n, b, b, m3);
            p.binary("LogicalAnd", MLX_BOOL, n, m2, m3, m2);
            p.binary("LogicalOr", MLX_BOOL, n, out, m2, out);
        }
    }

    /** How many calls ran as in-place MLX kernels. */
    static long dispatches() {
        return DISPATCHES.get();
    }

    /** Whether {@code functionName} has an in-place route. */
    static boolean hasRoute(String functionName) {
        return ROUTES.containsKey(functionName);
    }

    /** Runs {@code functionName} in place if it has a route that takes these arguments; false otherwise. */
    static boolean dispatch(String functionName, LibraryInvocation invocation) {
        Route route = ROUTES.get(functionName);
        if (route == null || invocation.isCapturing() || !(invocation.getDevice() instanceof TornadoNativeStreamSupport streams)) {
            return false;
        }
        Encoder encoder = route.plan(new View(invocation));
        if (encoder == null) {
            return false;
        }
        long queue = streams.getNativeStream(invocation.getExecutionPlanId());
        if (queue == 0) {
            return false;
        }
        try (Program program = new Program(queue)) {
            encoder.encode(program);
        }
        DISPATCHES.incrementAndGet();
        return true;
    }
}
