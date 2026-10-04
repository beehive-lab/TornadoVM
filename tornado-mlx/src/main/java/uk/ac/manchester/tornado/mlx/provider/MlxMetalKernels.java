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

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * Runs MLX's own Metal kernels, from its {@code mlx.metallib}, on TornadoVM's command queue with
 * TornadoVM's buffers bound in place, the way JIT-compiled kernels run. The kernel writes the
 * TornadoVM buffer directly, so there is no MLX array and no copy.
 *
 * <p>
 * Only kernels whose launch is simple enough to reproduce exactly are used: the contiguous
 * element-wise ones ({@code v_}/{@code vn_} unary, {@code vv_}/{@code vvn_} binary). Their names,
 * arguments, work per thread and grid follow MLX's {@code unary.cpp} and {@code binary.cpp}.
 */
final class MlxMetalKernels {

    /** Element-wise kernels process this many elements per thread from this size up (MLX's {@code get_work_per_thread}). */
    private static final int WORK_PER_THREAD_THRESHOLD = 1 << 16;

    private static final long SPIN_BUDGET_NS = 1_000_000;
    private static final int STATUS_POLL_INTERVAL = 64;
    private static final long MTL_COMMAND_BUFFER_STATUS_COMPLETED = 4;

    private static final MemoryLayout MTL_SIZE = MemoryLayout.structLayout(C_LONG.withName("width"), C_LONG.withName("height"), C_LONG.withName("depth"));

    private static final SymbolLookup LIBOBJC = FFMSupport.loadLibrary("/usr/lib/libobjc.A.dylib");
    private static final SymbolLookup FOUNDATION = FFMSupport.loadLibrary("/System/Library/Frameworks/Foundation.framework/Foundation");
    private static final MethodHandle SEL_REGISTER_NAME = downcall(FunctionDescriptor.of(C_LONG, C_POINTER), "sel_registerName");
    private static final MethodHandle OBJC_GET_CLASS = downcall(FunctionDescriptor.of(C_LONG, C_POINTER), "objc_getClass");
    private static final MethodHandle POOL_PUSH = downcall(FunctionDescriptor.of(C_LONG), "objc_autoreleasePoolPush");
    private static final MethodHandle POOL_POP = downcall(FunctionDescriptor.ofVoid(C_LONG), "objc_autoreleasePoolPop");

    private static final Map<FunctionDescriptor, MethodHandle> SENDS = new ConcurrentHashMap<>();
    private static final Map<String, Long> SELECTORS = new ConcurrentHashMap<>();

    /** Pipelines by device and kernel name; the library is loaded once per device. */
    private static final Map<Long, Long> LIBRARIES = new ConcurrentHashMap<>();
    private static final Map<String, long[]> PIPELINES = new ConcurrentHashMap<>();

    /** One shared event per command queue, signalled at the end of each command buffer. */
    private static final Map<Long, long[]> QUEUE_EVENTS = new ConcurrentHashMap<>();

    private static final String METALLIB = System.getProperty("tornado.mlx.metallib", installedMetallib());

    private MlxMetalKernels() {
    }

    /** Whether MLX's kernel library can be found. */
    /** Where MLX installs its compiled kernel library, next to {@code libmlx.dylib}; null if not found. */
    private static String installedMetallib() {
        for (String candidate : new String[] { "/opt/homebrew/opt/mlx/lib/mlx.metallib", "/usr/local/opt/mlx/lib/mlx.metallib" }) {
            if (Files.isReadable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    static boolean isAvailable() {
        return LIBOBJC != null && FOUNDATION != null && METALLIB != null && Files.isReadable(Path.of(METALLIB));
    }

    /** MLX's type suffix for a kernel name, or null for a type MLX's kernels do not take. */
    static String typeName(int dtype) {
        return switch (dtype) {
            case MlxTypes.MLX_BOOL -> "bool_";
            case MlxTypes.MLX_UINT8 -> "uint8";
            case MlxTypes.MLX_UINT16 -> "uint16";
            case MlxTypes.MLX_UINT32 -> "uint32";
            case MlxTypes.MLX_INT8 -> "int8";
            case MlxTypes.MLX_INT16 -> "int16";
            case MlxTypes.MLX_INT32 -> "int32";
            case MlxTypes.MLX_INT64 -> "int64";
            case MlxTypes.MLX_FLOAT16 -> "float16";
            case MlxTypes.MLX_FLOAT32 -> "float32";
            case MlxTypes.MLX_BFLOAT16 -> "bfloat16";
            case MlxTypes.MLX_COMPLEX64 -> "complex64";
            default -> null;
        };
    }

    static boolean isFloating(int dtype) {
        return dtype == MlxTypes.MLX_FLOAT32 || dtype == MlxTypes.MLX_FLOAT16 || dtype == MlxTypes.MLX_BFLOAT16;
    }

    static int itemSize(int dtype) {
        return switch (dtype) {
            case MlxTypes.MLX_BOOL, MlxTypes.MLX_UINT8, MlxTypes.MLX_INT8 -> 1;
            case MlxTypes.MLX_UINT16, MlxTypes.MLX_INT16, MlxTypes.MLX_FLOAT16, MlxTypes.MLX_BFLOAT16 -> 2;
            case MlxTypes.MLX_INT64, MlxTypes.MLX_COMPLEX64 -> 8;
            default -> 4;
        };
    }

    /** MLX's work per thread for contiguous element-wise kernels ({@code get_work_per_thread}). */
    private static int workPerThread(int dtype, long size) {
        return size < WORK_PER_THREAD_THRESHOLD ? 1 : Math.max(1, 8 / itemSize(dtype));
    }

    /** A kernel argument: a buffer with the byte offset of its first element. */
    record Ref(long buffer, long offset) {
        /** The same buffer, {@code elements} elements of {@code dtype} further on. */
        Ref plus(long elements, int dtype) {
            return new Ref(buffer, offset + elements * itemSize(dtype));
        }
    }

    /**
     * A shape with one or two stride vectors, with size-1 dimensions dropped and neighbouring
     * dimensions merged where every stride vector is contiguous across them, as MLX's
     * {@code collapse_contiguous_dims} does before choosing a kernel.
     */
    record Layout(int[] shape, long[] a, long[] b) {
        static Layout collapse(int[] shape, long[] a, long[] b) {
            int[] sh = new int[shape.length];
            long[] sa = new long[shape.length];
            long[] sb = new long[shape.length];
            int nd = 0;
            for (int i = 0; i < shape.length; i++) {
                if (shape[i] == 1) {
                    continue;
                }
                if (nd > 0 && sa[nd - 1] == a[i] * shape[i] && (b == null || sb[nd - 1] == b[i] * shape[i])) {
                    sh[nd - 1] *= shape[i];
                    sa[nd - 1] = a[i];
                    if (b != null) {
                        sb[nd - 1] = b[i];
                    }
                    continue;
                }
                sh[nd] = shape[i];
                sa[nd] = a[i];
                if (b != null) {
                    sb[nd] = b[i];
                }
                nd++;
            }
            if (nd == 0) {
                return new Layout(new int[] { 1 }, new long[] { 0 }, b == null ? null : new long[] { 0 });
            }
            return new Layout(java.util.Arrays.copyOf(sh, nd), java.util.Arrays.copyOf(sa, nd), b == null ? null : java.util.Arrays.copyOf(sb, nd));
        }

        int ndim() {
            return shape.length;
        }

        long size() {
            long n = 1;
            for (int d : shape) {
                n *= d;
            }
            return n;
        }
    }

    /** Row-major strides of {@code shape}. */
    static long[] contiguousStrides(int... shape) {
        long[] strides = new long[shape.length];
        long stride = 1;
        for (int i = shape.length - 1; i >= 0; i--) {
            strides[i] = stride;
            stride *= shape[i];
        }
        return strides;
    }

    /** MLX's {@code get_block_dims}: a power-of-two threadgroup of up to 1024 threads over a 3D grid. */
    private static long[] blockDims(long dim0, long dim1, long dim2) {
        int[] pows = new int[3];
        int sum = 0;
        while (true) {
            int presum = sum;
            if (dim0 >= (1L << (pows[0] + 1))) {
                pows[0]++;
                sum++;
            }
            if (sum == 10) {
                break;
            }
            if (dim1 >= (1L << (pows[1] + 1))) {
                pows[1]++;
                sum++;
            }
            if (sum == 10) {
                break;
            }
            if (dim2 >= (1L << (pows[2] + 1))) {
                pows[2]++;
                sum++;
            }
            if (sum == presum || sum == 10) {
                break;
            }
        }
        return new long[] { 1L << pows[0], 1L << pows[1], 1L << pows[2] };
    }

    /**
     * A sequence of MLX kernels encoded into one command buffer on TornadoVM's queue and waited on
     * once. The compute encoder dispatches serially, so each kernel sees the previous one's writes,
     * as in MLX's own command buffers. Scratch buffers come from a per-device pool and are reused.
     */
    static final class Program implements AutoCloseable {
        private final long queue;
        private final long device;
        private final long pool;
        private final Arena arena = Arena.ofConfined();
        private final long commandBuffer;
        private final long encoder;
        private int scratchUsed;

        Program(long queue) {
            this.queue = queue;
            this.pool = poolPush();
            this.device = send(queue, "device");
            this.commandBuffer = send(queue, "commandBuffer");
            this.encoder = send(commandBuffer, "computeCommandEncoder");
        }

        /** A scratch buffer of at least {@code bytes}, valid until this program is closed. */
        Ref scratch(long bytes) {
            return new Ref(scratchBuffer(device, scratchUsed++, bytes), 0);
        }

        /** {@code out = op(in)}: MLX's contiguous unary kernel. */
        void unary(String op, int inType, int outType, int size, Ref in, Ref out) {
            int wpt = workPerThread(inType, size);
            dispatch((wpt > 1 ? "vn_" : "v_") + op + typeName(inType) + typeName(outType), size, wpt, new Ref[] { in, out }, null);
        }

        /** {@code out = static_cast<outType>(in)}: MLX's contiguous copy kernel. */
        void copy(int inType, int outType, int size, Ref in, Ref out) {
            int wpt = workPerThread(outType, size);
            dispatch((wpt > 1 ? "vn_copy" : "v_copy") + typeName(inType) + typeName(outType), size, wpt, new Ref[] { in, out }, null);
        }

        /** {@code out[:] = value}: MLX's scalar-fill copy kernel. */
        void fill(int type, int size, double value, Ref out) {
            int wpt = workPerThread(type, size);
            dispatch((wpt > 1 ? "sn_copy" : "s_copy") + typeName(type) + typeName(type), size, wpt, new Ref[] { null, out }, scalarBytes(type, value));
        }

        /** {@code out = op(a, b)}, both vectors: MLX's {@code vv} binary kernel. */
        void binary(String op, int inType, int size, Ref a, Ref b, Ref out) {
            int wpt = workPerThread(inType, size);
            dispatch((wpt > 1 ? "vvn_" : "vv_") + op + typeName(inType), size, wpt, new Ref[] { a, b, out }, null);
        }

        /** {@code out = op(a, scalar)}: MLX's {@code vs} binary kernel. */
        void binaryScalarRight(String op, int inType, int size, Ref a, double scalar, Ref out) {
            int wpt = workPerThread(inType, size);
            dispatch((wpt > 1 ? "vsn_" : "vs_") + op + typeName(inType), size, wpt, new Ref[] { a, null, out }, scalarBytes(inType, scalar));
        }

        /** {@code out = op(scalar, b)}: MLX's {@code sv} binary kernel. */
        void binaryScalarLeft(String op, int inType, int size, double scalar, Ref b, Ref out) {
            int wpt = workPerThread(inType, size);
            dispatch((wpt > 1 ? "svn_" : "sv_") + op + typeName(inType), size, wpt, new Ref[] { null, b, out }, scalarBytes(inType, scalar));
        }

        /** {@code out = op(a, b[0])} with the scalar {@code b} read from a buffer: MLX's {@code vs} binary kernel. */
        void binaryScalarRight(String op, int inType, int size, Ref a, Ref scalar, Ref out) {
            int wpt = workPerThread(inType, size);
            dispatch((wpt > 1 ? "vsn_" : "vs_") + op + typeName(inType), size, wpt, new Ref[] { a, scalar, out }, null);
        }

        /** {@code out = op(scalar[0], b)} with the scalar read from a buffer: MLX's {@code sv} binary kernel. */
        void binaryScalarLeft(String op, int inType, int size, Ref scalar, Ref b, Ref out) {
            int wpt = workPerThread(inType, size);
            dispatch((wpt > 1 ? "svn_" : "sv_") + op + typeName(inType), size, wpt, new Ref[] { scalar, b, out }, null);
        }

        /** {@code out1, out2 = op(a, b)}: MLX's two-output binary kernel (divmod). */
        void binaryTwo(String op, int inType, int size, Ref a, Ref b, Ref out1, Ref out2) {
            int wpt = workPerThread(inType, size);
            dispatch((wpt > 1 ? "vvn_" : "vv_") + op + typeName(inType), size, wpt, new Ref[] { a, b, out1, out2 }, null);
        }

        /** {@code out = condition ? x : y}, with MLX bools as the condition: MLX's {@code v} Select kernel. */
        void select(int type, int size, Ref condition, Ref x, Ref y, Ref out) {
            int wpt = workPerThread(type, size);
            dispatch((wpt > 1 ? "vn_Select" : "v_Select") + typeName(type), size, wpt, new Ref[] { condition, x, y, out }, null);
        }

        /** {@code out = condition ? scalar : y}: MLX's {@code sv} Select kernel. */
        void selectScalar(int type, int size, Ref condition, double scalar, Ref y, Ref out) {
            int wpt = workPerThread(type, size);
            dispatch((wpt > 1 ? "svn_Select" : "sv_Select") + typeName(type), size, wpt, new Ref[] { condition, null, y, out }, scalarBytes(type, scalar));
        }

        /** {@code out = condition ? x : scalar}: MLX's {@code vs} Select kernel. */
        void selectScalarRight(int type, int size, Ref condition, Ref x, double scalar, Ref out) {
            int wpt = workPerThread(type, size);
            dispatch((wpt > 1 ? "vsn_Select" : "vs_Select") + typeName(type), size, wpt, new Ref[] { condition, x, null, out }, scalarBytes(type, scalar));
        }

        /** {@code out[i] = start + i * step} for {@code size} elements: MLX's arange kernel. */
        void arange(int type, int size, double start, double step, Ref out) {
            long[] pipeline = pipeline(device, "arange" + typeName(type));
            sendVoid(encoder, "setComputePipelineState:", pipeline[0]);
            // MLX sets the step to T(start + step) - T(start), computed in the output type.
            bytes(scalarBytes(type, start), 0);
            bytes(arangeStep(type, start, step), 1);
            sendVoid(encoder, "setBuffer:offset:atIndex:", out.buffer(), out.offset(), 2);
            dispatchThreads(arena, encoder, size, Math.min(size, pipeline[1]), 1, 1, 1, 1);
        }

        /**
         * {@code dst[layout] = src[layout]}: MLX's general copy, reading {@code src} with
         * {@code srcStrides} and writing {@code dst} contiguously ({@code dstStrides} null) or with
         * {@code dstStrides}. A null {@code src} copies {@code scalar}, with zero strides.
         */
        void generalCopy(int inType, int outType, int[] shape, long[] srcStrides, Ref src, long[] dstStrides, Ref dst, byte[] scalar) {
            boolean contiguousDst = dstStrides == null;
            Layout layout = Layout.collapse(shape, srcStrides, contiguousDst ? contiguousStrides(shape) : dstStrides);
            int nd = layout.ndim();
            if (src != null && nd == 1 && layout.a()[0] == 1 && layout.b()[0] == 1) {
                copy(inType, outType, (int) layout.size(), src, dst);
                return;
            }
            if (src == null && nd == 1 && layout.b()[0] == 1) {
                dispatch((workPerThread(outType, layout.size()) > 1 ? "sn_copy" : "s_copy") + typeName(inType) + typeName(outType), layout.size(),
                        workPerThread(outType, layout.size()), new Ref[] { null, dst }, scalar);
                return;
            }
            boolean gg = !contiguousDst || src == null;
            if (!gg) {
                // A contiguous destination only needs the source strides.
                layout = Layout.collapse(shape, srcStrides, null);
                nd = layout.ndim();
            }
            int wpt = nd > 3 ? 2 : 1;
            String name = (gg ? "gg" : "g") + (nd > 3 ? "n2" : Integer.toString(nd)) + "_copy" + typeName(inType) + typeName(outType);
            long[] pipeline = pipeline(device, name);
            sendVoid(encoder, "setComputePipelineState:", pipeline[0]);
            if (src == null) {
                bytes(scalar, 0);
            } else {
                sendVoid(encoder, "setBuffer:offset:atIndex:", src.buffer(), src.offset(), 0);
            }
            sendVoid(encoder, "setBuffer:offset:atIndex:", dst.buffer(), dst.offset(), 1);
            if (nd > 3) {
                bytes(intBytes(layout.shape()), 2);
            }
            bytes(longBytes(src == null ? new long[nd] : layout.a()), 3);
            if (gg) {
                bytes(longBytes(layout.b()), 4);
            }
            if (nd > 3) {
                bytes(intBytes(new int[] { nd }), 5);
            }
            gridDispatch(layout, wpt);
        }

        /**
         * {@code out = op(a, b)} over {@code shape} with broadcasting strides: MLX's general binary
         * kernel, or the {@code vv} kernel when both operands turn out contiguous.
         */
        void generalBinary(String op, int inType, int[] shape, long[] stridesA, Ref a, long[] stridesB, Ref b, Ref out) {
            Layout layout = Layout.collapse(shape, stridesA, stridesB);
            int nd = layout.ndim();
            if (nd == 1 && layout.a()[0] == 1 && layout.b()[0] == 1) {
                binary(op, inType, (int) layout.size(), a, b, out);
                return;
            }
            long[] pipeline = pipeline(device, (nd > 3 ? "gn2" : "g" + nd) + "_" + op + typeName(inType));
            sendVoid(encoder, "setComputePipelineState:", pipeline[0]);
            sendVoid(encoder, "setBuffer:offset:atIndex:", a.buffer(), a.offset(), 0);
            sendVoid(encoder, "setBuffer:offset:atIndex:", b.buffer(), b.offset(), 1);
            sendVoid(encoder, "setBuffer:offset:atIndex:", out.buffer(), out.offset(), 2);
            if (nd > 3) {
                // Beyond three dimensions the shape, both stride vectors and ndim follow the arrays.
                bytes(intBytes(layout.shape()), 3);
                bytes(longBytes(layout.a()), 4);
                bytes(longBytes(layout.b()), 5);
                bytes(intBytes(new int[] { nd }), 6);
                gridDispatch(layout, 2);
            } else {
                bytes(longBytes(layout.a()), 3);
                bytes(longBytes(layout.b()), 4);
                gridDispatch(layout, 1);
            }
        }

        /** Starts a hand-bound launch of kernel {@code name}, optionally specialised with boolean function constants 1... */
        Launch launch(String name, boolean... constants) {
            int[] indices = new int[constants.length];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = i + 1;
            }
            return launchIndexed(name, indices, constants);
        }


        /** A launch of kernel {@code name} specialised with boolean function constants {@code values[i]} at {@code indices[i]}. */
        Launch launchIndexed(String name, int[] indices, boolean[] values) {
            Object[] boxed = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                boxed[i] = values[i];
            }
            return launchConstants(name, indices, boxed);
        }

        /** A launch of kernel {@code name} specialised with function constants (Boolean or Integer) {@code values[i]} at {@code indices[i]}. */
        Launch launchConstants(String name, int[] indices, Object[] values) {
            long[] pipeline = pipeline(device, name, indices.length == 0 ? null : indices, values);
            sendVoid(encoder, "setComputePipelineState:", pipeline[0]);
            return new Launch(pipeline[1]);
        }

        /** A kernel launch whose arguments are bound one by one, for kernels with their own argument layout. */
        final class Launch {
            final long maxThreads;

            private Launch(long maxThreads) {
                this.maxThreads = maxThreads;
            }

            Launch buffer(int index, Ref ref) {
                sendVoid(encoder, "setBuffer:offset:atIndex:", ref.buffer(), ref.offset(), index);
                return this;
            }

            Launch bytes(int index, byte[] value) {
                Program.this.bytes(value, index);
                return this;
            }

            Launch i32(int index, int value) {
                return bytes(index, intBytes(new int[] { value }));
            }

            Launch i64(int index, long... values) {
                return bytes(index, longBytes(values));
            }

            Launch f32(int index, float value) {
                return bytes(index, scalarBytes(MlxTypes.MLX_FLOAT32, value));
            }

            /** {@code dispatchThreads} over (x, y, z) threads in groups of (gx, gy, gz). */
            void threads(long x, long gx, long y, long gy, long z, long gz) {
                dispatchThreads(arena, encoder, x, gx, y, gy, z, gz);
            }

            /** {@code dispatchThreadgroups}: (x, y, z) threadgroups of (gx, gy, gz) threads. */
            void threadgroups(long x, long y, long z, long gx, long gy, long gz) {
                dispatchThreadgroups(arena, encoder, x, y, z, gx, gy, gz);
            }

            /** {@code dispatchThreads} over (x, y, z) threads in MLX's {@code get_block_dims} groups. */
            void blocks(long x, long y, long z) {
                long[] group = blockDims(x, y, z);
                dispatchThreads(arena, encoder, x, group[0], y, group[1], z, group[2]);
            }
        }

        /** The 3D grid MLX uses for general kernels: (last dim / wpt, second-to-last dim, the rest). */
        private void gridDispatch(Layout layout, int wpt) {
            int nd = layout.ndim();
            long dim0 = layout.shape()[nd - 1];
            long dim1 = nd > 1 ? layout.shape()[nd - 2] : 1;
            long rest = layout.size() / (dim0 * dim1);
            if (nd > 3) {
                dim0 = (dim0 + wpt - 1) / wpt;
            }
            long[] group = blockDims(dim0, dim1, rest);
            dispatchThreads(arena, encoder, dim0, group[0], dim1, group[1], rest, group[2]);
        }

        private void bytes(byte[] value, int index) {
            MemorySegment segment = arena.allocate(Math.max(1, value.length));
            MemorySegment.copy(value, 0, segment, FFMSupport.C_CHAR, 0, value.length);
            sendVoid(encoder, "setBytes:length:atIndex:", segment.address(), value.length, index);
        }

        /**
         * Encodes one element-wise dispatch: {@code refs} bound at indices 0.., where a null entry is
         * the scalar operand {@code scalar} (bound with {@code setBytes}), and the element count after
         * them.
         */
        private void dispatch(String name, long size, int wpt, Ref[] refs, byte[] scalar) {
            long[] pipeline = pipeline(device, name);
            sendVoid(encoder, "setComputePipelineState:", pipeline[0]);
            for (int i = 0; i < refs.length; i++) {
                if (refs[i] == null) {
                    MemorySegment bytes = arena.allocate(scalar.length);
                    MemorySegment.copy(scalar, 0, bytes, FFMSupport.C_CHAR, 0, scalar.length);
                    sendVoid(encoder, "setBytes:length:atIndex:", bytes.address(), scalar.length, i);
                } else {
                    sendVoid(encoder, "setBuffer:offset:atIndex:", refs[i].buffer(), refs[i].offset(), i);
                }
            }
            MemorySegment sizeArg = arena.allocate(Integer.BYTES);
            sizeArg.set(FFMSupport.C_INT, 0, (int) size);
            sendVoid(encoder, "setBytes:length:atIndex:", sizeArg.address(), Integer.BYTES, refs.length);
            long threads = (size + wpt - 1) / wpt;
            dispatchThreads(arena, encoder, threads, Math.min(threads, pipeline[1]));
        }

        /** Ends encoding, commits, and waits for the GPU to finish. */
        @Override
        public void close() {
            try {
                sendVoid(encoder, "endEncoding");
                commitAndWait(queue, device, commandBuffer);
            } finally {
                arena.close();
                poolPop(pool);
            }
        }
    }

    /** A scalar in MLX's representation of {@code dtype}, as {@code array(value, dtype)} would hold it. */
    static byte[] scalarBytes(int dtype, double value) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        switch (dtype) {
            case MlxTypes.MLX_FLOAT32 -> b.putFloat((float) value);
            case MlxTypes.MLX_FLOAT16 -> b.putShort(Float.floatToFloat16((float) value));
            case MlxTypes.MLX_BFLOAT16 -> b.putShort(floatToBFloat16((float) value));
            case MlxTypes.MLX_INT32, MlxTypes.MLX_UINT32 -> b.putInt((int) value);
            case MlxTypes.MLX_INT64 -> b.putLong((long) value);
            case MlxTypes.MLX_INT16, MlxTypes.MLX_UINT16 -> b.putShort((short) value);
            case MlxTypes.MLX_INT8, MlxTypes.MLX_UINT8, MlxTypes.MLX_BOOL -> b.put((byte) value);
            case MlxTypes.MLX_COMPLEX64 -> b.putFloat((float) value).putFloat(0.0f);
            default -> throw new TornadoRuntimeException("[ERROR] No scalar form for MLX dtype " + dtype);
        }
        byte[] out = new byte[itemSize(dtype)];
        System.arraycopy(b.array(), 0, out, 0, out.length);
        return out;
    }

    static byte[] intBytes(int[] values) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(4 * values.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int v : values) {
            b.putInt(v);
        }
        return b.array();
    }

    static byte[] longBytes(long[] values) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8 * values.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (long v : values) {
            b.putLong(v);
        }
        return b.array();
    }

    /** MLX's arange step, {@code T(start + step) - T(start)} evaluated in type {@code T}. */
    static byte[] arangeStep(int dtype, double start, double step) {
        return switch (dtype) {
            case MlxTypes.MLX_FLOAT32 -> scalarBytes(dtype, (float) (start + step) - (float) start);
            case MlxTypes.MLX_FLOAT16 -> scalarBytes(dtype, Float.float16ToFloat(Float.floatToFloat16((float) (start + step))) - Float.float16ToFloat(Float.floatToFloat16((float) start)));
            case MlxTypes.MLX_BFLOAT16 -> scalarBytes(dtype, bf16ToFloat(floatToBFloat16((float) (start + step))) - bf16ToFloat(floatToBFloat16((float) start)));
            default -> scalarBytes(dtype, (long) (start + step) - (long) start);
        };
    }

    private static float bf16ToFloat(short bits) {
        return Float.intBitsToFloat((bits & 0xffff) << 16);
    }

    /** Round-to-nearest-even float to bfloat16, as MLX's {@code bfloat16_t} conversion does. */
    private static short floatToBFloat16(float value) {
        int bits = Float.floatToRawIntBits(value);
        if (Float.isNaN(value)) {
            return (short) ((bits >>> 16) | 0x40);
        }
        int rounding = 0x7fff + ((bits >>> 16) & 1);
        return (short) ((bits + rounding) >>> 16);
    }

    /** Scratch buffers by device and slot, grown on demand and kept for reuse. */
    private static final Map<Long, long[][]> SCRATCH = new ConcurrentHashMap<>();
    private static final int SCRATCH_SLOTS = 16;

    private static synchronized long scratchBuffer(long device, int slot, long bytes) {
        if (slot >= SCRATCH_SLOTS) {
            throw new TornadoRuntimeException("[ERROR] Too many MLX scratch buffers in one program");
        }
        long[][] slots = SCRATCH.computeIfAbsent(device, d -> new long[SCRATCH_SLOTS][2]);
        long[] entry = slots[slot];
        if (entry[1] < bytes) {
            if (entry[0] != 0) {
                sendVoid(entry[0], "release");
            }
            long capacity = Math.max(bytes, 4096);
            entry[0] = send(device, "newBufferWithLength:options:", capacity, 0);
            entry[1] = capacity;
        }
        return entry[0];
    }

    private static long[] pipeline(long device, String name) {
        return pipeline(device, name, null, null);
    }

    /** MTLDataTypeBool and MTLDataTypeInt, for function constants. */
    private static final long MTL_DATA_TYPE_BOOL = 53;
    private static final long MTL_DATA_TYPE_INT = 29;

    /**
     * The pipeline for kernel {@code name}, specialised with boolean function constants
     * {@code constants[i]} at index {@code indices[i]} when given.
     */
    private static long[] pipeline(long device, String name, int[] indices, Object[] constants) {
        String key = device + ":" + name + (constants == null ? "" : java.util.Arrays.toString(indices) + java.util.Arrays.toString(constants));
        return PIPELINES.computeIfAbsent(key, k -> {
            long library = LIBRARIES.computeIfAbsent(device, MlxMetalKernels::loadLibrary);
            long function;
            if (constants == null) {
                function = send(library, "newFunctionWithName:", nsString(name));
            } else {
                long values = send(objcClass("MTLFunctionConstantValues"), "new");
                try (Arena arena = Arena.ofConfined()) {
                    for (int i = 0; i < constants.length; i++) {
                        MemorySegment value = arena.allocate(4);
                        if (constants[i] instanceof Integer number) {
                            value.set(FFMSupport.C_INT, 0, number);
                            sendVoid(values, "setConstantValue:type:atIndex:", value.address(), MTL_DATA_TYPE_INT, indices[i]);
                        } else {
                            value.set(FFMSupport.C_CHAR, 0, (byte) (Boolean.TRUE.equals(constants[i]) ? 1 : 0));
                            sendVoid(values, "setConstantValue:type:atIndex:", value.address(), MTL_DATA_TYPE_BOOL, indices[i]);
                        }
                    }
                }
                MemorySegment error = FFMSupport.scratchPointer();
                error.set(C_POINTER, 0, MemorySegment.NULL);
                function = send(library, "newFunctionWithName:constantValues:error:", nsString(name), values, error.address());
                sendVoid(values, "release");
            }
            if (function == 0) {
                throw new TornadoRuntimeException("[ERROR] mlx.metallib has no kernel " + name);
            }
            MemorySegment error = FFMSupport.scratchPointer();
            error.set(C_POINTER, 0, MemorySegment.NULL);
            long pso = send(device, "newComputePipelineStateWithFunction:error:", function, error.address());
            if (pso == 0) {
                throw new TornadoRuntimeException("[ERROR] Cannot build a pipeline for MLX kernel " + name);
            }
            return new long[] { pso, send(pso, "maxTotalThreadsPerThreadgroup") };
        });
    }

    private static long loadLibrary(long device) {
        long url = send(objcClass("NSURL"), "fileURLWithPath:", nsString(METALLIB));
        MemorySegment error = FFMSupport.scratchPointer();
        error.set(C_POINTER, 0, MemorySegment.NULL);
        long library = send(device, "newLibraryWithURL:error:", url, error.address());
        if (library == 0) {
            throw new TornadoRuntimeException("[ERROR] Cannot load " + METALLIB);
        }
        return library;
    }

    /**
     * Commits and waits by polling a shared event the command buffer signals when its work is done,
     * as the Metal backend does, falling back to {@code waitUntilCompleted} after a short spin.
     */
    private static void commitAndWait(long queue, long device, long commandBuffer) {
        long[] queueEvent = QUEUE_EVENTS.computeIfAbsent(queue, q -> new long[] { send(device, "newSharedEvent"), 0 });
        long value;
        synchronized (queueEvent) {
            value = ++queueEvent[1];
        }
        sendVoid(commandBuffer, "encodeSignalEvent:value:", queueEvent[0], value);
        sendVoid(commandBuffer, "commit");
        long start = System.nanoTime();
        int polls = 0;
        while (send(queueEvent[0], "signaledValue") < value) {
            if (++polls % STATUS_POLL_INTERVAL == 0) {
                if (System.nanoTime() - start > SPIN_BUDGET_NS) {
                    sendVoid(commandBuffer, "waitUntilCompleted");
                    break;
                }
                if (send(commandBuffer, "status") >= MTL_COMMAND_BUFFER_STATUS_COMPLETED) {
                    break;
                }
            }
            Thread.yield();
        }
        if (send(commandBuffer, "error") != 0) {
            throw new TornadoRuntimeException("[ERROR] MLX Metal kernel failed");
        }
    }

    // ---------------------------------------------------------------- Objective-C messaging

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) {
            return e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        return new TornadoRuntimeException(t instanceof Exception e ? e : new Exception(t));
    }

    private static MethodHandle downcall(FunctionDescriptor descriptor, String name) {
        return LIBOBJC == null ? null : FFMSupport.downcall(LIBOBJC, descriptor, name);
    }

    private static long sel(String name) {
        return SELECTORS.computeIfAbsent(name, key -> {
            try (Arena arena = Arena.ofConfined()) {
                return (long) SEL_REGISTER_NAME.invokeExact(FFMSupport.allocateCString(arena, key));
            } catch (Throwable t) {
                throw rethrow(t);
            }
        });
    }

    private static long objcClass(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return (long) OBJC_GET_CLASS.invokeExact(FFMSupport.allocateCString(arena, name));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static long nsString(String value) {
        try (Arena arena = Arena.ofConfined()) {
            return send(objcClass("NSString"), "stringWithUTF8String:", FFMSupport.allocateCString(arena, value).address());
        }
    }

    private static MethodHandle msgSend(FunctionDescriptor descriptor) {
        return SENDS.computeIfAbsent(descriptor, key -> FFMSupport.downcall(LIBOBJC, key, "objc_msgSend"));
    }

    private static long send(long receiver, String selector) {
        try {
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static long send(long receiver, String selector, long argument) {
        try {
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector), argument);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static long send(long receiver, String selector, long first, long second) {
        try {
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector), first, second);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static long send(long receiver, String selector, long first, long second, long third) {
        try {
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector), first, second, third);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static void sendVoid(long receiver, String selector) {
        send(receiver, selector);
    }

    private static void sendVoid(long receiver, String selector, long argument) {
        send(receiver, selector, argument);
    }

    private static void sendVoid(long receiver, String selector, long first, long second) {
        send(receiver, selector, first, second);
    }

    private static void sendVoid(long receiver, String selector, long first, long second, long third) {
        try {
            long ignored = (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector), first, second, third);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static void dispatchThreadgroups(Arena arena, long encoder, long x, long y, long z, long gx, long gy, long gz) {
        MemorySegment grid = arena.allocate(MTL_SIZE);
        grid.set(C_LONG, 0, x);
        grid.set(C_LONG, 8, y);
        grid.set(C_LONG, 16, z);
        MemorySegment tg = arena.allocate(MTL_SIZE);
        tg.set(C_LONG, 0, gx);
        tg.set(C_LONG, 8, gy);
        tg.set(C_LONG, 16, gz);
        try {
            msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, MTL_SIZE, MTL_SIZE)).invokeExact(encoder, sel("dispatchThreadgroups:threadsPerThreadgroup:"), grid, tg);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static final Map<Long, String> ARCHITECTURES = new ConcurrentHashMap<>();

    /** The GPU architecture name MLX reads ({@code [[device architecture] name]}, e.g. applegpu_g13s). */
    static String architecture(long queue) {
        long device = send(queue, "device");
        return ARCHITECTURES.computeIfAbsent(device, d -> {
            long arch = send(d, "architecture");
            if (arch == 0) {
                return "";
            }
            long utf8 = send(send(arch, "name"), "UTF8String");
            return utf8 == 0 ? "" : FFMSupport.readCString(FFMSupport.asSegment(utf8, 256), 256);
        });
    }

    private static final Map<String, Boolean> KERNEL_PRESENT = new ConcurrentHashMap<>();

    /** Whether mlx.metallib has a kernel named {@code name} (some tile combinations are not instantiated). */
    static boolean hasKernel(long queue, String name) {
        long device = send(queue, "device");
        return KERNEL_PRESENT.computeIfAbsent(device + ":" + name, k -> {
            long pool = poolPush();
            try {
                long library = LIBRARIES.computeIfAbsent(device, MlxMetalKernels::loadLibrary);
                long function = send(library, "newFunctionWithName:", nsString(name));
                if (function != 0) {
                    sendVoid(function, "release");
                }
                return function != 0;
            } finally {
                poolPop(pool);
            }
        });
    }

    /** MLX's get_architecture_gen: the number after "applegpu_g", or 0. */
    static int architectureGeneration(String architecture) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("applegpu_g(\\d+)").matcher(architecture);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** {@code dispatchThreads:threadsPerThreadgroup:}, whose two {@code MTLSize} arguments go by value. */
    private static void dispatchThreads(Arena arena, long encoder, long threads, long group) {
        dispatchThreads(arena, encoder, threads, group, 1, 1, 1, 1);
    }

    private static void dispatchThreads(Arena arena, long encoder, long x, long gx, long y, long gy, long z, long gz) {
        MemorySegment grid = arena.allocate(MTL_SIZE);
        grid.set(C_LONG, 0, x);
        grid.set(C_LONG, 8, y);
        grid.set(C_LONG, 16, z);
        MemorySegment tg = arena.allocate(MTL_SIZE);
        tg.set(C_LONG, 0, gx);
        tg.set(C_LONG, 8, gy);
        tg.set(C_LONG, 16, gz);
        try {
            msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, MTL_SIZE, MTL_SIZE)).invokeExact(encoder, sel("dispatchThreads:threadsPerThreadgroup:"), grid, tg);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static long poolPush() {
        try {
            return (long) POOL_PUSH.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static void poolPop(long pool) {
        try {
            POOL_POP.invokeExact(pool);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
