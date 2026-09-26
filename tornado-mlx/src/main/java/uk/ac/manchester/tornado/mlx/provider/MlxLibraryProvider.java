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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.mlx.Mlx;
import uk.ac.manchester.tornado.mlx.MlxOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryInvocation;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;

/**
 * {@link TornadoLibraryProvider} for Apple MLX, reached through mlx-c. Discovered by the
 * TornadoVM runtime through {@link java.util.ServiceLoader}.
 *
 * <p>
 * Inputs are adopted, not copied: each TornadoVM buffer is wrapped as an MLX array over the same
 * shared-storage memory. MLX runs the operation on its own command queue and waits for it; the
 * Metal backend waits after every kernel too, so the two queues never overlap. MLX operations
 * allocate their own result, which is copied into the output buffer.
 * </p>
 *
 * <p>
 * Contiguous element-wise operations skip the C API: {@link MlxMetalKernels} runs MLX's own kernel
 * from {@code mlx.metallib} on TornadoVM's command queue with the TornadoVM buffers bound in place,
 * as a JIT-compiled kernel would run, so there is neither a result array nor a copy.
 * </p>
 *
 * <p>
 * Wrapping a buffer makes MLX create a no-copy MTLBuffer the GPU has to map, which is costly for
 * large arrays, so wrappers are cached per execution plan. The cache key is the data address, so
 * the provider retains each TornadoVM MTLBuffer it has wrapped: while it is retained, no other
 * allocation can take that address, and a cached wrapper can never outlive its memory. Wrappers
 * and retains are dropped when the execution plan's context is destroyed.
 * </p>
 */
public final class MlxLibraryProvider implements TornadoLibraryProvider {

    private static final AtomicLong COPY_FALLBACKS = new AtomicLong();

    /** Operations MLX only implements on the CPU stream. */
    private static final Set<String> CPU_ONLY = Set.of("linalg_cholesky", "linalg_cholesky_inv", "linalg_tri_inv", "linalg_inv", "linalg_solve", "linalg_solve_triangular",
            "linalg_lu", "linalg_lu_factor", "linalg_qr", "linalg_eigh", "linalg_eigvalsh", "linalg_svd", "linalg_svd_values", "linalg_pinv", "linalg_eig", "linalg_eigvals",
            "random_multivariate_normal");

    private interface Unary {
        int apply(MemorySegment res, MemorySegment a, MemorySegment stream);
    }

    private interface Scan {
        int apply(MemorySegment res, MemorySegment a, int axis, boolean reverse, boolean inclusive, MemorySegment stream);
    }

    private interface Reduce {
        int apply(MemorySegment res, MemorySegment a, boolean keepdims, MemorySegment stream);
    }

    private interface ReduceAxis {
        int apply(MemorySegment res, MemorySegment a, int axis, boolean keepdims, MemorySegment stream);
    }

    private interface ReduceAxes {
        int apply(MemorySegment res, MemorySegment a, MemorySegment axes, long axesNum, boolean keepdims, MemorySegment stream);
    }

    private interface AlongAxisUpdate {
        int apply(MemorySegment res, MemorySegment a, MemorySegment indices, MemorySegment values, int axis, MemorySegment stream);
    }

    private interface Scatter {
        int apply(MemorySegment res, MemorySegment a, MemorySegment indices, MemorySegment updates, MemorySegment axes, long axesNum, MemorySegment stream);
    }

    private interface ScatterSingle {
        int apply(MemorySegment res, MemorySegment a, MemorySegment indices, MemorySegment updates, int axis, MemorySegment stream);
    }

    private interface SliceUpdate {
        int apply(MemorySegment res, MemorySegment src, MemorySegment update, MemorySegment start, long startNum, MemorySegment stop, long stopNum, MemorySegment strides,
                long stridesNum, MemorySegment stream);
    }

    private interface MatrixUpper {
        int apply(MemorySegment res, MemorySegment a, boolean upper, MemorySegment stream);
    }

    private interface Fft1d {
        int apply(MemorySegment res, MemorySegment a, int n, int axis, int norm, MemorySegment stream);
    }

    private interface FftNd {
        int apply(MemorySegment res, MemorySegment a, MemorySegment n, long nNum, MemorySegment axes, long axesNum, int norm, MemorySegment stream);
    }

    private interface FftShift {
        int apply(MemorySegment res, MemorySegment a, MemorySegment axes, long axesNum, MemorySegment stream);
    }

    private interface Binary {
        int apply(MemorySegment res, MemorySegment a, MemorySegment b, MemorySegment stream);
    }

    private static final Map<String, Consumer<MlxCall>> OPERATIONS = Map.ofEntries(
            // Element-wise binary: (a, b, out), all the same length.
            entry("add", c -> binary(c, "mlx_add", MlxC::mlx_add)), //
            entry("subtract", c -> binary(c, "mlx_subtract", MlxC::mlx_subtract)), //
            entry("multiply", c -> binary(c, "mlx_multiply", MlxC::mlx_multiply)), //
            entry("divide", c -> binary(c, "mlx_divide", MlxC::mlx_divide)), //
            entry("maximum", c -> binary(c, "mlx_maximum", MlxC::mlx_maximum)), //
            entry("minimum", c -> binary(c, "mlx_minimum", MlxC::mlx_minimum)), //
            // Element-wise unary: (a, out).
            entry("negative", c -> unary(c, "mlx_negative", MlxC::mlx_negative)), //
            entry("exp", c -> unary(c, "mlx_exp", MlxC::mlx_exp)), //
            entry("tanh", c -> unary(c, "mlx_tanh", MlxC::mlx_tanh)), //
            entry("erf", c -> unary(c, "mlx_erf", MlxC::mlx_erf)), //
            entry("sigmoid", c -> unary(c, "mlx_sigmoid", MlxC::mlx_sigmoid)), //
            entry("sqrt", c -> unary(c, "mlx_sqrt", MlxC::mlx_sqrt)), //
            entry("rsqrt", c -> unary(c, "mlx_rsqrt", MlxC::mlx_rsqrt)), //
            entry("square", c -> unary(c, "mlx_square", MlxC::mlx_square)), //
            // Element-wise math (MlxMath).
            entry("abs", c -> unary(c, "mlx_abs", MlxC::mlx_abs)), //
            entry("arccos", c -> unary(c, "mlx_arccos", MlxC::mlx_arccos)), //
            entry("arccosh", c -> unary(c, "mlx_arccosh", MlxC::mlx_arccosh)), //
            entry("arcsin", c -> unary(c, "mlx_arcsin", MlxC::mlx_arcsin)), //
            entry("arcsinh", c -> unary(c, "mlx_arcsinh", MlxC::mlx_arcsinh)), //
            entry("arctan", c -> unary(c, "mlx_arctan", MlxC::mlx_arctan)), //
            entry("arctanh", c -> unary(c, "mlx_arctanh", MlxC::mlx_arctanh)), //
            entry("ceil", c -> unary(c, "mlx_ceil", MlxC::mlx_ceil)), //
            entry("cos", c -> unary(c, "mlx_cos", MlxC::mlx_cos)), //
            entry("cosh", c -> unary(c, "mlx_cosh", MlxC::mlx_cosh)), //
            entry("degrees", c -> unary(c, "mlx_degrees", MlxC::mlx_degrees)), //
            entry("erfinv", c -> unary(c, "mlx_erfinv", MlxC::mlx_erfinv)), //
            entry("expm1", c -> unary(c, "mlx_expm1", MlxC::mlx_expm1)), //
            entry("floor", c -> unary(c, "mlx_floor", MlxC::mlx_floor)), //
            entry("log", c -> unary(c, "mlx_log", MlxC::mlx_log)), //
            entry("log10", c -> unary(c, "mlx_log10", MlxC::mlx_log10)), //
            entry("log1p", c -> unary(c, "mlx_log1p", MlxC::mlx_log1p)), //
            entry("log2", c -> unary(c, "mlx_log2", MlxC::mlx_log2)), //
            entry("radians", c -> unary(c, "mlx_radians", MlxC::mlx_radians)), //
            entry("reciprocal", c -> unary(c, "mlx_reciprocal", MlxC::mlx_reciprocal)), //
            entry("sign", c -> unary(c, "mlx_sign", MlxC::mlx_sign)), //
            entry("sin", c -> unary(c, "mlx_sin", MlxC::mlx_sin)), //
            entry("sinh", c -> unary(c, "mlx_sinh", MlxC::mlx_sinh)), //
            entry("tan", c -> unary(c, "mlx_tan", MlxC::mlx_tan)), //
            entry("arctan2", c -> binary(c, "mlx_arctan2", MlxC::mlx_arctan2)), //
            entry("floor_divide", c -> binary(c, "mlx_floor_divide", MlxC::mlx_floor_divide)), //
            entry("logaddexp", c -> binary(c, "mlx_logaddexp", MlxC::mlx_logaddexp)), //
            entry("power", c -> binary(c, "mlx_power", MlxC::mlx_power)), //
            entry("remainder", c -> binary(c, "mlx_remainder", MlxC::mlx_remainder)), //
            entry("round", MlxLibraryProvider::round), //
            entry("divmod", MlxLibraryProvider::divmod), //
            entry("clip", MlxLibraryProvider::clip), //
            entry("where", MlxLibraryProvider::where), //
            // Reductions (MlxReduce).
            entry("sum", c -> reduce(c, "mlx_sum", MlxC::mlx_sum)), //
            entry("sum_axis", c -> reduceAxis(c, "mlx_sum_axis", MlxC::mlx_sum_axis)), //
            entry("sum_axes", c -> reduceAxes(c, "mlx_sum_axes", MlxC::mlx_sum_axes)), //
            entry("prod", c -> reduce(c, "mlx_prod", MlxC::mlx_prod)), //
            entry("prod_axis", c -> reduceAxis(c, "mlx_prod_axis", MlxC::mlx_prod_axis)), //
            entry("prod_axes", c -> reduceAxes(c, "mlx_prod_axes", MlxC::mlx_prod_axes)), //
            entry("max", c -> reduce(c, "mlx_max", MlxC::mlx_max)), //
            entry("max_axis", c -> reduceAxis(c, "mlx_max_axis", MlxC::mlx_max_axis)), //
            entry("max_axes", c -> reduceAxes(c, "mlx_max_axes", MlxC::mlx_max_axes)), //
            entry("min", c -> reduce(c, "mlx_min", MlxC::mlx_min)), //
            entry("min_axis", c -> reduceAxis(c, "mlx_min_axis", MlxC::mlx_min_axis)), //
            entry("min_axes", c -> reduceAxes(c, "mlx_min_axes", MlxC::mlx_min_axes)), //
            entry("mean", c -> reduce(c, "mlx_mean", MlxC::mlx_mean)), //
            entry("mean_axis", c -> reduceAxis(c, "mlx_mean_axis", MlxC::mlx_mean_axis)), //
            entry("mean_axes", c -> reduceAxes(c, "mlx_mean_axes", MlxC::mlx_mean_axes)), //
            entry("logsumexp", c -> reduce(c, "mlx_logsumexp", MlxC::mlx_logsumexp)), //
            entry("logsumexp_axis", c -> reduceAxis(c, "mlx_logsumexp_axis", MlxC::mlx_logsumexp_axis)), //
            entry("logsumexp_axes", c -> reduceAxes(c, "mlx_logsumexp_axes", MlxC::mlx_logsumexp_axes)), //
            entry("var", c -> reduce(c, "mlx_var", (r, a, k, s) -> MlxC.mlx_var(r, a, k, c.intArg(2), s))), //
            entry("var_axis", c -> reduceAxis(c, "mlx_var_axis", (r, a, ax, k, s) -> MlxC.mlx_var_axis(r, a, ax, k, c.intArg(5), s))), //
            entry("var_axes", c -> reduceAxes(c, "mlx_var_axes", (r, a, axes, n, k, s) -> MlxC.mlx_var_axes(r, a, axes, n, k, c.intArg(6), s))), //
            entry("std", c -> reduce(c, "mlx_std", (r, a, k, s) -> MlxC.mlx_std(r, a, k, c.intArg(2), s))), //
            entry("std_axis", c -> reduceAxis(c, "mlx_std_axis", (r, a, ax, k, s) -> MlxC.mlx_std_axis(r, a, ax, k, c.intArg(5), s))), //
            entry("std_axes", c -> reduceAxes(c, "mlx_std_axes", (r, a, axes, n, k, s) -> MlxC.mlx_std_axes(r, a, axes, n, k, c.intArg(6), s))), //
            entry("all", c -> reduce(c, "mlx_all", MlxC::mlx_all)), //
            entry("all_axis", c -> reduceAxis(c, "mlx_all_axis", MlxC::mlx_all_axis)), //
            entry("all_axes", c -> reduceAxes(c, "mlx_all_axes", MlxC::mlx_all_axes)), //
            entry("any", c -> reduce(c, "mlx_any", MlxC::mlx_any)), //
            entry("any_axis", c -> reduceAxis(c, "mlx_any_axis", MlxC::mlx_any_axis)), //
            entry("any_axes", c -> reduceAxes(c, "mlx_any_axes", MlxC::mlx_any_axes)), //
            entry("argmin", c -> reduce(c, "mlx_argmin", MlxC::mlx_argmin)), //
            entry("argmin_axis", c -> reduceAxis(c, "mlx_argmin_axis", MlxC::mlx_argmin_axis)), //
            entry("cumsum", c -> scan(c, "mlx_cumsum", MlxC::mlx_cumsum)), //
            entry("cumprod", c -> scan(c, "mlx_cumprod", MlxC::mlx_cumprod)), //
            entry("cummax", c -> scan(c, "mlx_cummax", MlxC::mlx_cummax)), //
            entry("cummin", c -> scan(c, "mlx_cummin", MlxC::mlx_cummin)), //
            entry("logcumsumexp", c -> scan(c, "mlx_logcumsumexp", MlxC::mlx_logcumsumexp)), //
            entry("median", c -> reduceAxes(c, "mlx_median", (r, a, axes, n, k, s) -> MlxC.mlx_median(r, a, axes, n, k, s), true)), //
            // Sorting and partitioning (MlxSort).
            entry("sort", c -> whole(c, (r, a, s) -> MlxC.mlx_sort(r, a, s), "mlx_sort")), //
            entry("argsort", c -> whole(c, (r, a, s) -> MlxC.mlx_argsort(r, a, s), "mlx_argsort")), //
            entry("partition", c -> whole(c, (r, a, s) -> MlxC.mlx_partition(r, a, c.intArg(2), s), "mlx_partition")), //
            entry("argpartition", c -> whole(c, (r, a, s) -> MlxC.mlx_argpartition(r, a, c.intArg(2), s), "mlx_argpartition")), //
            entry("sort_axis", c -> alongAxis(c, (r, a, s) -> MlxC.mlx_sort_axis(r, a, 1, s), "mlx_sort_axis")), //
            entry("argsort_axis", c -> alongAxis(c, (r, a, s) -> MlxC.mlx_argsort_axis(r, a, 1, s), "mlx_argsort_axis")), //
            entry("partition_axis", c -> alongAxis(c, (r, a, s) -> MlxC.mlx_partition_axis(r, a, c.intArg(5), 1, s), "mlx_partition_axis")), //
            entry("argpartition_axis", c -> alongAxis(c, (r, a, s) -> MlxC.mlx_argpartition_axis(r, a, c.intArg(5), 1, s), "mlx_argpartition_axis")), //
            // Indexing (MlxIndex).
            entry("take", MlxLibraryProvider::take), //
            entry("take_axis", MlxLibraryProvider::takeAxis), //
            entry("take_along_axis", MlxLibraryProvider::takeAlongAxis), //
            entry("put_along_axis", c -> alongAxisUpdate(c, "mlx_put_along_axis", MlxC::mlx_put_along_axis)), //
            entry("scatter_add_axis", c -> alongAxisUpdate(c, "mlx_scatter_add_axis", MlxC::mlx_scatter_add_axis)), //
            entry("gather", MlxLibraryProvider::gather), //
            entry("gather_single", MlxLibraryProvider::gatherRows), //
            entry("scatter", c -> scatterPoints(c, "mlx_scatter", MlxC::mlx_scatter)), //
            entry("scatter_add", c -> scatterPoints(c, "mlx_scatter_add", MlxC::mlx_scatter_add)), //
            entry("scatter_max", c -> scatterPoints(c, "mlx_scatter_max", MlxC::mlx_scatter_max)), //
            entry("scatter_min", c -> scatterPoints(c, "mlx_scatter_min", MlxC::mlx_scatter_min)), //
            entry("scatter_prod", c -> scatterPoints(c, "mlx_scatter_prod", MlxC::mlx_scatter_prod)), //
            entry("scatter_single", c -> scatterRows(c, "mlx_scatter_single", MlxC::mlx_scatter_single)), //
            entry("scatter_add_single", c -> scatterRows(c, "mlx_scatter_add_single", MlxC::mlx_scatter_add_single)), //
            entry("scatter_max_single", c -> scatterRows(c, "mlx_scatter_max_single", MlxC::mlx_scatter_max_single)), //
            entry("scatter_min_single", c -> scatterRows(c, "mlx_scatter_min_single", MlxC::mlx_scatter_min_single)), //
            entry("scatter_prod_single", c -> scatterRows(c, "mlx_scatter_prod_single", MlxC::mlx_scatter_prod_single)), //
            entry("slice", MlxLibraryProvider::slice), //
            entry("slice_dynamic", MlxLibraryProvider::sliceDynamic), //
            entry("slice_update", c -> sliceUpdate(c, "mlx_slice_update", MlxC::mlx_slice_update)), //
            entry("slice_update_add", c -> sliceUpdate(c, "mlx_slice_update_add", MlxC::mlx_slice_update_add)), //
            entry("slice_update_max", c -> sliceUpdate(c, "mlx_slice_update_max", MlxC::mlx_slice_update_max)), //
            entry("slice_update_min", c -> sliceUpdate(c, "mlx_slice_update_min", MlxC::mlx_slice_update_min)), //
            entry("slice_update_prod", c -> sliceUpdate(c, "mlx_slice_update_prod", MlxC::mlx_slice_update_prod)), //
            entry("slice_update_dynamic", MlxLibraryProvider::sliceUpdateDynamic), //
            entry("masked_scatter", MlxLibraryProvider::maskedScatter), //
            entry("gather_mm", MlxLibraryProvider::gatherMm), //
            // Convolutions (MlxConv).
            entry("conv1d", MlxLibraryProvider::conv1d), //
            entry("conv2d", MlxLibraryProvider::conv2d), //
            entry("conv3d", MlxLibraryProvider::conv3d), //
            entry("conv_transpose1d", MlxLibraryProvider::convTranspose1d), //
            entry("conv_transpose2d", MlxLibraryProvider::convTranspose2d), //
            entry("conv_transpose3d", MlxLibraryProvider::convTranspose3d), //
            entry("conv_general", MlxLibraryProvider::convGeneral), //
            // Linear algebra (MlxLinalg).
            entry("linalg_cross", MlxLibraryProvider::cross), //
            entry("linalg_norm", MlxLibraryProvider::norm), //
            entry("linalg_norm_l2", MlxLibraryProvider::l2Norm), //
            entry("linalg_norm_matrix", MlxLibraryProvider::frobeniusNorm), //
            entry("linalg_cholesky", c -> matrixUpper(c, "mlx_linalg_cholesky", MlxC::mlx_linalg_cholesky)), //
            entry("linalg_cholesky_inv", c -> matrixUpper(c, "mlx_linalg_cholesky_inv", MlxC::mlx_linalg_cholesky_inv)), //
            entry("linalg_tri_inv", c -> matrixUpper(c, "mlx_linalg_tri_inv", MlxC::mlx_linalg_tri_inv)), //
            entry("linalg_inv", MlxLibraryProvider::inv), //
            entry("linalg_solve", MlxLibraryProvider::solve), //
            entry("linalg_solve_triangular", MlxLibraryProvider::solveTriangular), //
            entry("linalg_lu", MlxLibraryProvider::lu), //
            entry("linalg_lu_factor", MlxLibraryProvider::luFactor), //
            entry("linalg_qr", MlxLibraryProvider::qr), //
            entry("linalg_eigh", MlxLibraryProvider::eigh), //
            entry("linalg_eigvalsh", MlxLibraryProvider::eigvalsh), //
            entry("linalg_svd", c -> svd(c, true)), //
            entry("linalg_svd_values", c -> svd(c, false)), //
            entry("linalg_pinv", MlxLibraryProvider::pinv), //
            entry("linalg_eig", MlxLibraryProvider::eig), //
            entry("linalg_eigvals", MlxLibraryProvider::eigvals), //
            // FFT (MlxFft).
            entry("fft_fft", c -> fft1d(c, "mlx_fft_fft", MlxC::mlx_fft_fft, true, true)), //
            entry("fft_ifft", c -> fft1d(c, "mlx_fft_ifft", MlxC::mlx_fft_ifft, true, true)), //
            entry("fft_rfft", c -> fft1d(c, "mlx_fft_rfft", MlxC::mlx_fft_rfft, false, true)), //
            entry("fft_irfft", c -> fft1d(c, "mlx_fft_irfft", MlxC::mlx_fft_irfft, true, false)), //
            entry("fft_fft2", c -> fftNd(c, "mlx_fft_fft2", MlxC::mlx_fft_fft2, 2, true, true, false)), //
            entry("fft_ifft2", c -> fftNd(c, "mlx_fft_ifft2", MlxC::mlx_fft_ifft2, 2, true, true, false)), //
            entry("fft_rfft2", c -> fftNd(c, "mlx_fft_rfft2", MlxC::mlx_fft_rfft2, 2, false, true, false)), //
            entry("fft_irfft2", c -> fftNd(c, "mlx_fft_irfft2", MlxC::mlx_fft_irfft2, 2, true, false, true)), //
            entry("fft_fftn", c -> fftNd(c, "mlx_fft_fftn", MlxC::mlx_fft_fftn, 3, true, true, false)), //
            entry("fft_ifftn", c -> fftNd(c, "mlx_fft_ifftn", MlxC::mlx_fft_ifftn, 3, true, true, false)), //
            entry("fft_rfftn", c -> fftNd(c, "mlx_fft_rfftn", MlxC::mlx_fft_rfftn, 3, false, true, false)), //
            entry("fft_irfftn", c -> fftNd(c, "mlx_fft_irfftn", MlxC::mlx_fft_irfftn, 3, true, false, true)), //
            entry("fft_fftshift", c -> fftShift(c, "mlx_fft_fftshift", MlxC::mlx_fft_fftshift)), //
            entry("fft_ifftshift", c -> fftShift(c, "mlx_fft_ifftshift", MlxC::mlx_fft_ifftshift)), //
            entry("fft_fftfreq", c -> c.store(c.op("mlx_fft_fftfreq", res -> MlxC.mlx_fft_fftfreq(res, c.intArg(1), c.floatArg(2), c.stream())), 0)), //
            entry("fft_rfftfreq", c -> c.store(c.op("mlx_fft_rfftfreq", res -> MlxC.mlx_fft_rfftfreq(res, c.intArg(1), c.floatArg(2), c.stream())), 0)), //
            // Comparisons, logic, bitwise and complex parts (MlxLogic).
            entry("equal", c -> binary(c, "mlx_equal", MlxC::mlx_equal)), //
            entry("not_equal", c -> binary(c, "mlx_not_equal", MlxC::mlx_not_equal)), //
            entry("greater", c -> binary(c, "mlx_greater", MlxC::mlx_greater)), //
            entry("greater_equal", c -> binary(c, "mlx_greater_equal", MlxC::mlx_greater_equal)), //
            entry("less", c -> binary(c, "mlx_less", MlxC::mlx_less)), //
            entry("less_equal", c -> binary(c, "mlx_less_equal", MlxC::mlx_less_equal)), //
            entry("bitwise_and", c -> binary(c, "mlx_bitwise_and", MlxC::mlx_bitwise_and)), //
            entry("bitwise_or", c -> binary(c, "mlx_bitwise_or", MlxC::mlx_bitwise_or)), //
            entry("bitwise_xor", c -> binary(c, "mlx_bitwise_xor", MlxC::mlx_bitwise_xor)), //
            entry("left_shift", c -> binary(c, "mlx_left_shift", MlxC::mlx_left_shift)), //
            entry("right_shift", c -> binary(c, "mlx_right_shift", MlxC::mlx_right_shift)), //
            entry("isfinite", c -> unary(c, "mlx_isfinite", MlxC::mlx_isfinite)), //
            entry("isinf", c -> unary(c, "mlx_isinf", MlxC::mlx_isinf)), //
            entry("isnan", c -> unary(c, "mlx_isnan", MlxC::mlx_isnan)), //
            entry("isneginf", c -> unary(c, "mlx_isneginf", MlxC::mlx_isneginf)), //
            entry("isposinf", c -> unary(c, "mlx_isposinf", MlxC::mlx_isposinf)), //
            entry("bitwise_invert", c -> unary(c, "mlx_bitwise_invert", MlxC::mlx_bitwise_invert)), //
            entry("isclose", MlxLibraryProvider::isclose), //
            entry("allclose", MlxLibraryProvider::allclose), //
            entry("array_equal", MlxLibraryProvider::arrayEqual), //
            entry("logical_and", c -> logical(c, "mlx_logical_and", MlxC::mlx_logical_and)), //
            entry("logical_or", c -> logical(c, "mlx_logical_or", MlxC::mlx_logical_or)), //
            entry("logical_not", MlxLibraryProvider::logicalNot), //
            entry("nan_to_num", MlxLibraryProvider::nanToNum), //
            entry("real", c -> complexPart(c, "mlx_real", MlxC::mlx_real, false)), //
            entry("imag", c -> complexPart(c, "mlx_imag", MlxC::mlx_imag, false)), //
            entry("conjugate", c -> complexPart(c, "mlx_conjugate", MlxC::mlx_conjugate, true)), //
            // Construction and matrix structure (MlxCreate).
            entry("arange", MlxLibraryProvider::arange), //
            entry("linspace", c -> c.store(c.op("mlx_linspace", res -> MlxC.mlx_linspace(res, c.floatArg(1), c.floatArg(2), c.length(0), c.dtype(0), c.stream())), 0)), //
            entry("eye", c -> c.store(c.op("mlx_eye", res -> MlxC.mlx_eye(res, c.intArg(1), c.intArg(2), c.intArg(3), c.dtype(0), c.stream())), 0)), //
            entry("identity", c -> c.store(c.op("mlx_identity", res -> MlxC.mlx_identity(res, c.intArg(1), c.dtype(0), c.stream())), 0)), //
            entry("tri", c -> c.store(c.op("mlx_tri", res -> MlxC.mlx_tri(res, c.intArg(1), c.intArg(2), c.intArg(3), c.dtype(0), c.stream())), 0)), //
            entry("full", c -> c.store(c.op("mlx_full", res -> MlxC.mlx_full(res, c.ints(c.length(0)), 1, c.scalar(c.floatArg(1)), c.dtype(0), c.stream())), 0)), //
            entry("full_like", c -> c.store(c.op("mlx_full_like", res -> MlxC.mlx_full_like(res, c.input(0, c.length(0)), c.scalar(c.floatArg(2)), c.dtype(1), c.stream())), 1)), //
            entry("zeros", c -> c.store(c.op("mlx_zeros", res -> MlxC.mlx_zeros(res, c.ints(c.length(0)), 1, c.dtype(0), c.stream())), 0)), //
            entry("zeros_like", c -> c.store(c.op("mlx_zeros_like", res -> MlxC.mlx_zeros_like(res, c.input(0, c.length(0)), c.stream())), 1)), //
            entry("ones", c -> c.store(c.op("mlx_ones", res -> MlxC.mlx_ones(res, c.ints(c.length(0)), 1, c.dtype(0), c.stream())), 0)), //
            entry("ones_like", c -> c.store(c.op("mlx_ones_like", res -> MlxC.mlx_ones_like(res, c.input(0, c.length(0)), c.stream())), 1)), //
            entry("bartlett", c -> c.store(c.op("mlx_bartlett", res -> MlxC.mlx_bartlett(res, c.length(0), c.stream())), 0)), //
            entry("blackman", c -> c.store(c.op("mlx_blackman", res -> MlxC.mlx_blackman(res, c.length(0), c.stream())), 0)), //
            entry("hamming", c -> c.store(c.op("mlx_hamming", res -> MlxC.mlx_hamming(res, c.length(0), c.stream())), 0)), //
            entry("hanning", c -> c.store(c.op("mlx_hanning", res -> MlxC.mlx_hanning(res, c.length(0), c.stream())), 0)), //
            entry("meshgrid", MlxLibraryProvider::meshgrid), //
            entry("diag", c -> c.store(c.op("mlx_diag", res -> MlxC.mlx_diag(res, c.input(0, c.length(0)), c.intArg(2), c.stream())), 1)), //
            entry("diagonal", c -> c.store(c.op("mlx_diagonal", res -> MlxC.mlx_diagonal(res, c.input(0, c.intArg(2), c.intArg(3)), c.intArg(4), 0, 1, c.stream())), 1)), //
            entry("trace", c -> c.store(c.op("mlx_trace", res -> MlxC.mlx_trace(res, c.input(0, c.intArg(2), c.intArg(3)), c.intArg(4), 0, 1, c.dtype(1), c.stream())), 1)), //
            entry("tril", c -> c.store(c.op("mlx_tril", res -> MlxC.mlx_tril(res, c.input(0, c.intArg(2), c.intArg(3)), c.intArg(4), c.stream())), 1)), //
            entry("triu", c -> c.store(c.op("mlx_triu", res -> MlxC.mlx_triu(res, c.input(0, c.intArg(2), c.intArg(3)), c.intArg(4), c.stream())), 1)), //
            // Shape and layout (MlxShape).
            entry("reshape", c -> c.store(c.op("mlx_reshape", res -> MlxC.mlx_reshape(res, c.input(0, c.length(0)), c.ints(c.intArg(2), c.intArg(3)), 2, c.stream())), 1)), //
            entry("flatten", c -> c.store(c.op("mlx_flatten", res -> MlxC.mlx_flatten(res, c.input(0, c.intArg(2), c.intArg(3), c.intArg(4)), 1, 2, c.stream())), 1)), //
            entry("unflatten", c -> c.store(c.op("mlx_unflatten", res -> MlxC.mlx_unflatten(res, c.input(0, c.length(0)), 0, c.ints(c.intArg(2), c.intArg(3)), 2, c.stream())), 1)), //
            entry("squeeze", c -> c.store(c.op("mlx_squeeze", res -> MlxC.mlx_squeeze(res, c.input(0, c.intArg(2), 1, c.intArg(3)), c.stream())), 1)), //
            entry("squeeze_axis", c -> c.store(c.op("mlx_squeeze_axis", res -> MlxC.mlx_squeeze_axis(res, c.input(0, c.intArg(2), 1, c.intArg(3)), 1, c.stream())), 1)), //
            entry("squeeze_axes", c -> c.store(c.op("mlx_squeeze_axes", res -> MlxC.mlx_squeeze_axes(res, c.input(0, 1, c.intArg(2), 1, c.intArg(3)), c.ints(0, 2), 2, c.stream())), 1)), //
            entry("expand_dims", c -> c.store(c.op("mlx_expand_dims", res -> MlxC.mlx_expand_dims(res, c.input(0, c.intArg(2), c.intArg(3)), 1, c.stream())), 1)), //
            entry("expand_dims_axes", c -> c.store(c.op("mlx_expand_dims_axes", res -> MlxC.mlx_expand_dims_axes(res, c.input(0, c.intArg(2), c.intArg(3)), c.ints(0, 2), 2,
                    c.stream())), 1)), //
            entry("atleast_1d", c -> c.store(c.op("mlx_atleast_1d", res -> MlxC.mlx_atleast_1d(res, c.input(0, c.length(0)), c.stream())), 1)), //
            entry("atleast_2d", c -> c.store(c.op("mlx_atleast_2d", res -> MlxC.mlx_atleast_2d(res, c.input(0, c.length(0)), c.stream())), 1)), //
            entry("atleast_3d", c -> c.store(c.op("mlx_atleast_3d", res -> MlxC.mlx_atleast_3d(res, c.input(0, c.length(0)), c.stream())), 1)), //
            entry("transpose_axes", c -> c.store(c.op("mlx_transpose_axes", res -> MlxC.mlx_transpose_axes(res, c.input(0, c.intArg(2), c.intArg(3), c.intArg(4)), c.ints(c.intArg(5),
                    c.intArg(6), c.intArg(7)), 3, c.stream())), 1)), //
            entry("swapaxes", c -> c.store(c.op("mlx_swapaxes", res -> MlxC.mlx_swapaxes(res, c.input(0, c.intArg(2), c.intArg(3), c.intArg(4)), c.intArg(5), c.intArg(6), c.stream())),
                    1)), //
            entry("moveaxis", c -> c.store(c.op("mlx_moveaxis", res -> MlxC.mlx_moveaxis(res, c.input(0, c.intArg(2), c.intArg(3), c.intArg(4)), c.intArg(5), c.intArg(6), c.stream())),
                    1)), //
            entry("broadcast_to", c -> c.store(c.op("mlx_broadcast_to", res -> MlxC.mlx_broadcast_to(res, c.input(0, c.length(0)), c.ints(c.intArg(2), c.intArg(3)), 2, c.stream())), 1)), //
            entry("broadcast_arrays", MlxLibraryProvider::broadcastArrays), //
            entry("as_strided", c -> c.store(c.op("mlx_as_strided", res -> MlxC.mlx_as_strided(res, c.input(0, c.length(0)), c.ints(c.intArg(2), c.intArg(3)), 2, c.longs(c.intArg(4),
                    c.intArg(5)), 2, c.intArg(6), c.stream())), 1)), //
            entry("contiguous", c -> c.store(c.op("mlx_contiguous", res -> MlxC.mlx_contiguous(res, c.input(0, c.length(0)), false, c.stream())), 1)), //
            entry("copy", c -> c.store(c.op("mlx_copy", res -> MlxC.mlx_copy(res, c.input(0, c.length(0)), c.stream())), 1)), //
            entry("astype", c -> c.store(c.op("mlx_astype", res -> MlxC.mlx_astype(res, c.input(0, c.length(0)), c.dtype(1), c.stream())), 1)), //
            entry("view", c -> c.store(c.op("mlx_view", res -> MlxC.mlx_view(res, c.input(0, c.length(0)), c.dtype(1), c.stream())), 1)), //
            entry("number_of_elements", c -> c.store(c.op("mlx_number_of_elements", res -> MlxC.mlx_number_of_elements(res, c.input(0, c.intArg(2), c.intArg(3), c.intArg(4)), c.ints(1,
                    2), 2, false, MlxNativeLib.MLX_INT32, c.stream())), 1)), //
            entry("concatenate", c -> c.store(c.op("mlx_concatenate", res -> MlxC.mlx_concatenate(res, c.vector(c.input(0, c.length(0)), c.input(1, c.length(1))), c.stream())), 2)), //
            entry("concatenate_axis", c -> c.store(c.op("mlx_concatenate_axis", res -> MlxC.mlx_concatenate_axis(res, c.vector(c.input(0, c.intArg(3), c.intArg(4)), c.input(1,
                    c.intArg(3), c.intArg(5))), 1, c.stream())), 2)), //
            entry("stack", c -> c.store(c.op("mlx_stack", res -> MlxC.mlx_stack(res, c.vector(c.input(0, c.length(0)), c.input(1, c.length(1))), c.stream())), 2)), //
            entry("stack_axis", c -> c.store(c.op("mlx_stack_axis", res -> MlxC.mlx_stack_axis(res, c.vector(c.input(0, c.length(0)), c.input(1, c.length(1))), 1, c.stream())), 2)), //
            entry("split", c -> splitTwo(c, false)), //
            entry("split_sections", c -> splitTwo(c, true)), //
            entry("repeat", c -> c.store(c.op("mlx_repeat", res -> MlxC.mlx_repeat(res, c.input(0, c.length(0)), c.intArg(2), c.stream())), 1)), //
            entry("repeat_axis", c -> c.store(c.op("mlx_repeat_axis", res -> MlxC.mlx_repeat_axis(res, c.input(0, c.intArg(2), c.intArg(3)), c.intArg(4), 0, c.stream())), 1)), //
            entry("tile", c -> c.store(c.op("mlx_tile", res -> MlxC.mlx_tile(res, c.input(0, c.intArg(2), c.intArg(3)), c.ints(c.intArg(4), c.intArg(5)), 2, c.stream())), 1)), //
            entry("roll", c -> c.store(c.op("mlx_roll", res -> MlxC.mlx_roll(res, c.input(0, c.length(0)), c.ints(c.intArg(2)), 1, c.stream())), 1)), //
            entry("roll_axis", c -> c.store(c.op("mlx_roll_axis", res -> MlxC.mlx_roll_axis(res, c.input(0, c.intArg(2), c.intArg(3)), c.ints(c.intArg(4)), 1, 1, c.stream())), 1)), //
            entry("roll_axes", c -> c.store(c.op("mlx_roll_axes", res -> MlxC.mlx_roll_axes(res, c.input(0, c.intArg(2), c.intArg(3)), c.ints(c.intArg(4), c.intArg(5)), 2, c.ints(0,
                    1), 2, c.stream())), 1)), //
            entry("pad", c -> c.store(c.op("mlx_pad", res -> MlxC.mlx_pad(res, c.input(0, c.intArg(2), c.intArg(3)), c.ints(0, 1), 2, c.ints(c.intArg(4), c.intArg(6)), 2,
                    c.ints(c.intArg(5), c.intArg(7)), 2, c.scalar(c.floatArg(8)), c.cString("constant"), c.stream())), 1)), //
            entry("pad_symmetric", c -> c.store(c.op("mlx_pad_symmetric", res -> MlxC.mlx_pad_symmetric(res, c.input(0, c.intArg(2), c.intArg(3)), c.intArg(4), c.scalar(c.floatArg(5)),
                    c.cString("constant"), c.stream())), 1)), //
            // Tensor and special products (MlxProducts).
            entry("einsum_bmm", c -> c.store(c.op("mlx_einsum", res -> MlxC.mlx_einsum(res, c.cString("bij,bjk->bik"), c.vector(c.input(0, c.intArg(3), c.intArg(4), c.intArg(5)),
                    c.input(1, c.intArg(3), c.intArg(5), c.intArg(6))), c.stream())), 2)), //
            entry("inner", c -> c.store(c.op("mlx_inner", res -> MlxC.mlx_inner(res, c.input(0, c.length(0)), c.input(1, c.length(1)), c.stream())), 2)), //
            entry("outer", c -> c.store(c.op("mlx_outer", res -> MlxC.mlx_outer(res, c.input(0, c.length(0)), c.input(1, c.length(1)), c.stream())), 2)), //
            entry("kron", c -> c.store(c.op("mlx_kron", res -> MlxC.mlx_kron(res, c.input(0, c.intArg(3), c.intArg(4)), c.input(1, c.intArg(5), c.intArg(6)), c.stream())), 2)), //
            entry("tensordot", c -> c.store(c.op("mlx_tensordot", res -> MlxC.mlx_tensordot(res, c.input(0, c.intArg(3), c.intArg(4), c.intArg(5)),
                    c.input(1, c.intArg(4), c.intArg(5), c.intArg(6)), c.ints(1, 2), 2, c.ints(0, 1), 2, c.stream())), 2)), //
            entry("tensordot_axis", c -> c.store(c.op("mlx_tensordot_axis", res -> MlxC.mlx_tensordot_axis(res, c.input(0, c.intArg(3), c.intArg(4)), c.input(1, c.intArg(4), c.intArg(5)), 1,
                    c.stream())), 2)), //
            entry("block_masked_mm", MlxLibraryProvider::blockMaskedMm), //
            entry("segmented_mm", MlxLibraryProvider::segmentedMm), //
            entry("hadamard_transform", c -> c.store(c.op("mlx_hadamard_transform", res -> MlxC.mlx_hadamard_transform(res, c.input(0, c.intArg(2), c.intArg(3)), c.optionalFloat(c.floatArg(4)),
                    c.stream())), 1)), //
            entry("to_fp8", c -> c.storeRaw(c.op("mlx_to_fp8", res -> MlxC.mlx_to_fp8(res, c.input(0, c.length(0)), c.stream())), 1)), //
            entry("from_fp8", c -> c.store(c.op("mlx_from_fp8", res -> MlxC.mlx_from_fp8(res, c.input(0, c.length(0)), MlxNativeLib.MLX_FLOAT32, c.stream())), 1)), //
            entry("qqmm", MlxLibraryProvider::qqmm), //
            entry("quantize_mx", MlxLibraryProvider::quantizeMx), //
            // Random sampling (MlxRandom).
            entry("random_bits", c -> c.storeRaw(c.op("mlx_random_bits", res -> MlxC.mlx_random_bits(res, c.ints(c.length(0)), 1, 4, randomKey(c, 1), c.stream())), 0)), //
            entry("random_uniform", c -> c.store(c.op("mlx_random_uniform", res -> MlxC.mlx_random_uniform(res, c.scalar(c.floatArg(1)), c.scalar(c.floatArg(2)), c.ints(c.length(0)), 1,
                    MlxNativeLib.MLX_FLOAT32, randomKey(c, 3), c.stream())), 0)), //
            entry("random_normal", c -> c.store(c.op("mlx_random_normal", res -> MlxC.mlx_random_normal(res, c.ints(c.length(0)), 1, MlxNativeLib.MLX_FLOAT32, c.floatArg(1), c.floatArg(2),
                    randomKey(c, 3), c.stream())), 0)), //
            entry("random_normal_broadcast", c -> c.store(c.op("mlx_random_normal_broadcast", res -> MlxC.mlx_random_normal_broadcast(res, c.ints(c.length(2)), 1, MlxNativeLib.MLX_FLOAT32,
                    c.input(0, c.length(0)), c.input(1, c.length(1)), randomKey(c, 3), c.stream())), 2)), //
            entry("random_bernoulli", c -> c.store(c.op("mlx_random_bernoulli", res -> MlxC.mlx_random_bernoulli(res, c.input(0, c.length(0)), c.ints(c.length(1)), 1, randomKey(c, 2),
                    c.stream())), 1)), //
            entry("random_randint", c -> c.store(c.op("mlx_random_randint", res -> MlxC.mlx_random_randint(res, c.scalar(c.intArg(1)), c.scalar(c.intArg(2)), c.ints(c.length(0)), 1,
                    MlxNativeLib.MLX_INT32, randomKey(c, 3), c.stream())), 0)), //
            entry("random_truncated_normal", c -> c.store(c.op("mlx_random_truncated_normal", res -> MlxC.mlx_random_truncated_normal(res, c.scalar(c.floatArg(1)), c.scalar(c.floatArg(2)),
                    c.ints(c.length(0)), 1, MlxNativeLib.MLX_FLOAT32, randomKey(c, 3), c.stream())), 0)), //
            entry("random_gumbel", c -> c.store(c.op("mlx_random_gumbel", res -> MlxC.mlx_random_gumbel(res, c.ints(c.length(0)), 1, MlxNativeLib.MLX_FLOAT32, randomKey(c, 1), c.stream())),
                    0)), //
            entry("random_laplace", c -> c.store(c.op("mlx_random_laplace", res -> MlxC.mlx_random_laplace(res, c.ints(c.length(0)), 1, MlxNativeLib.MLX_FLOAT32, c.floatArg(1), c.floatArg(2),
                    randomKey(c, 3), c.stream())), 0)), //
            entry("random_categorical", c -> c.store(c.op("mlx_random_categorical", res -> MlxC.mlx_random_categorical(res, c.input(0, c.intArg(2), c.intArg(3)), 1, randomKey(c, 4),
                    c.stream())), 1)), //
            entry("random_categorical_num_samples", c -> c.store(c.op("mlx_random_categorical_num_samples", res -> MlxC.mlx_random_categorical_num_samples(res, c.input(0, c.intArg(2),
                    c.intArg(3)), 1, c.intArg(4), randomKey(c, 5), c.stream())), 1)), //
            entry("random_categorical_shape", c -> c.store(c.op("mlx_random_categorical_shape", res -> MlxC.mlx_random_categorical_shape(res, c.input(0, c.intArg(2), c.intArg(3)), 1,
                    c.ints(c.intArg(4), c.intArg(2)), 2, randomKey(c, 5), c.stream())), 1)), //
            entry("random_multivariate_normal", c -> c.store(c.op("mlx_random_multivariate_normal", res -> MlxC.mlx_random_multivariate_normal(res, c.input(0, c.intArg(4)), c.input(1,
                    c.intArg(4), c.intArg(4)), c.ints(c.intArg(3)), 1, MlxNativeLib.MLX_FLOAT32, randomKey(c, 5), c.stream())), 2)), //
            entry("random_permutation", c -> c.store(c.op("mlx_random_permutation", res -> MlxC.mlx_random_permutation(res, c.input(0, c.length(0)), 0, randomKey(c, 2), c.stream())), 1)), //
            entry("random_permutation_arange", c -> c.store(c.op("mlx_random_permutation_arange", res -> MlxC.mlx_random_permutation_arange(res, c.length(0), randomKey(c, 1), c.stream())),
                    0)), //
            // Linear algebra.
            entry("matmul", MlxLibraryProvider::matmul), //
            entry("matmul_transposed", MlxLibraryProvider::matmulTransposed), //
            entry("addmm", MlxLibraryProvider::addmm), //
            // Affine group quantization.
            entry("quantized_matmul", MlxLibraryProvider::quantizedMatmul), //
            entry("gather_qmm", MlxLibraryProvider::gatherQmm), //
            entry("quantize", MlxLibraryProvider::quantize), //
            entry("dequantize", MlxLibraryProvider::dequantize), //
            // mlx.fast
            entry("fast_rms_norm", MlxLibraryProvider::rmsNorm), //
            entry("fast_layer_norm", MlxLibraryProvider::layerNorm), //
            entry("fast_rope", MlxLibraryProvider::rope), //
            entry("fast_rope_dynamic", MlxLibraryProvider::ropeDynamic), //
            entry("fast_scaled_dot_product_attention", MlxLibraryProvider::sdpa), //
            // Softmax, argmax, top-k.
            entry("softmax", MlxLibraryProvider::softmax), //
            entry("softmax_axis", MlxLibraryProvider::softmaxRows), //
            entry("softmax_axes", MlxLibraryProvider::softmaxLastTwoAxes), //
            entry("argmax", MlxLibraryProvider::argmax), //
            entry("argmax_axis", MlxLibraryProvider::argmaxRows), //
            entry("topk", MlxLibraryProvider::topk), //
            entry("topk_axis", MlxLibraryProvider::topkRows));

    /** Affine group quantization mode (scales and biases per group), as used by MLX-LM. */
    private static final String AFFINE = "affine";

    /** Whether libmlxc can be loaded on this host. */
    public static boolean isAvailable() {
        try {
            MlxNativeLib.load();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** MLX version string, e.g. "0.32.1". */
    public static String mlxVersion() {
        return MlxNativeLib.version();
    }

    /**
     * Number of inputs MLX copied instead of adopting, since start-up. MLX falls back to a copy,
     * silently, when Metal refuses to wrap the memory; results stay correct but the zero-copy
     * path is lost. Tests assert this stays zero.
     */
    public static long copyFallbacks() {
        return COPY_FALLBACKS.get();
    }

    /**
     * Bytes MLX currently has allocated for live arrays (excluding its buffer cache). Used by the
     * leak tests.
     */
    public static long activeMemoryBytes() {
        MlxNativeLib.load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bytes = arena.allocate(ValueLayout.JAVA_LONG);
            MlxNativeLib.check(MlxC.mlx_get_active_memory(bytes), "mlx_get_active_memory");
            return bytes.get(ValueLayout.JAVA_LONG, 0);
        }
    }

    /** Names of the operations this provider dispatches, e.g. "add". */
    public static Set<String> operations() {
        return OPERATIONS.keySet();
    }

    private record WrapKey(long address, List<Integer> shape, int dtype) {
    }

    /** Per execution plan: MLX streams and the wrappers of TornadoVM buffers. */
    static final class MlxContext implements LibraryContext {
        private final MemorySegment gpuStream = MlxC.mlx_default_gpu_stream_new();
        private MemorySegment cpuStream;
        private final Map<WrapKey, MemorySegment> wrappers = new HashMap<>();
        private final Map<Long, Integer> retainedBuffers = new HashMap<>();

        MemorySegment stream(MlxOptions.Device device) {
            if (device == MlxOptions.Device.GPU) {
                return gpuStream;
            }
            if (cpuStream == null) {
                cpuStream = MlxC.mlx_default_cpu_stream_new();
            }
            return cpuStream;
        }

        /** The MLX array over a TornadoVM buffer, wrapped once per plan and shape. */
        MemorySegment wrapper(long address, long nativeBuffer, int[] shape, int dtype) {
            WrapKey key = new WrapKey(address, Arrays.stream(shape).boxed().toList(), dtype);
            MemorySegment wrapper = wrappers.get(key);
            if (wrapper != null) {
                return wrapper;
            }
            MlxNativeLib.retain(nativeBuffer);
            retainedBuffers.merge(nativeBuffer, 1, Integer::sum);

            long releasesBefore = MlxNativeLib.releases();
            wrapper = MlxNativeLib.wrap(address, shape, dtype);
            MlxNativeLib.eval(wrapper);
            if (MlxNativeLib.releases() != releasesBefore || MlxNativeLib.dataAddress(wrapper) != address) {
                COPY_FALLBACKS.incrementAndGet();
            }
            wrappers.put(key, wrapper);
            return wrapper;
        }

        void destroy() {
            wrappers.values().forEach(MlxNativeLib::free);
            wrappers.clear();
            retainedBuffers.forEach((buffer, count) -> {
                for (int i = 0; i < count; i++) {
                    MlxNativeLib.release(buffer);
                }
            });
            retainedBuffers.clear();
            MlxC.mlx_stream_free(gpuStream);
            if (cpuStream != null) {
                MlxC.mlx_stream_free(cpuStream);
            }
        }
    }

    @Override
    public String libraryName() {
        return Mlx.LIBRARY_NAME;
    }

    @Override
    public boolean canHandle(TornadoXPUDevice device) {
        return device.getTornadoVMBackend() == TornadoVMBackendType.METAL && isAvailable();
    }

    @Override
    public LibraryContext createContext(TornadoXPUDevice device, long executionPlanId) {
        MlxNativeLib.load();
        return new MlxContext();
    }

    @Override
    public void destroyContext(LibraryContext context) {
        MlxContext ctx = (MlxContext) context;
        synchronized (ctx) {
            ctx.destroy();
        }
    }

    @Override
    public void dispatch(String functionName, LibraryInvocation invocation) {
        Consumer<MlxCall> operation = OPERATIONS.get(functionName);
        if (operation == null) {
            throw new TornadoRuntimeException("[ERROR] Unknown MLX function: " + functionName);
        }
        MlxContext ctx = (MlxContext) invocation.getContext();
        MlxOptions options = invocation.getTuning() instanceof MlxOptions tuning ? tuning : defaultOptions;
        MlxOptions.Device device = CPU_ONLY.contains(functionName) ? MlxOptions.Device.CPU : options.getDevice();
        lastDevice = device;
        lastInPlace = false;
        if (device == MlxOptions.Device.GPU && options.isInPlaceKernels() && MlxKernelRoutes.dispatch(functionName, invocation)) {
            lastInPlace = true;
            return;
        }
        synchronized (ctx) {
            try (MlxCall call = new MlxCall(ctx, invocation, ctx.stream(device))) {
                operation.accept(call);
            }
        }
    }

    // ---------------------------------------------------------------- MLX kernels in place

    /** Options for calls that carry none, normally the GPU with in-place kernels; benchmarks change it to compare paths. */
    private static volatile MlxOptions defaultOptions = MlxOptions.gpu();
    private static volatile MlxOptions.Device lastDevice = MlxOptions.Device.GPU;
    private static volatile boolean lastInPlace;

    /** Sets the options used by MLX tasks that have no tuning of their own (null restores the GPU default). */
    public static void setDefaultOptions(MlxOptions options) {
        defaultOptions = options == null ? MlxOptions.gpu() : options;
    }

    /** The device the most recent MLX call ran on (CPU for operations MLX only implements there). */
    public static MlxOptions.Device lastDevice() {
        return lastDevice;
    }

    /** Whether the most recent MLX call ran as an in-place MLX kernel. */
    public static boolean lastInPlace() {
        return lastInPlace;
    }

    /** How many operations ran as in-place MLX kernels rather than through the C API. */
    public static long kernelDispatches() {
        return MlxKernelRoutes.dispatches();
    }

    /** Whether {@code functionName} can run as an in-place MLX kernel (when its arguments allow). */
    public static boolean hasInPlaceKernel(String functionName) {
        return MlxKernelRoutes.hasRoute(functionName);
    }

    // ---------------------------------------------------------------- operation families

    // The MLX PRNG key for the seed in argument seedIndex
    private static MemorySegment randomKey(MlxCall c, int seedIndex) {
        int seed = c.intArg(seedIndex);
        return c.op("mlx_random_key", res -> MlxC.mlx_random_key(res, seed));
    }

    // block_masked_mm(a, b, maskOut, maskLhs, maskRhs, out, m, k, n, blockSize): byte masks at block granularity
    private static void blockMaskedMm(MlxCall c) {
        int m = c.intArg(6);
        int k = c.intArg(7);
        int n = c.intArg(8);
        int bs = c.intArg(9);
        MemorySegment a = c.input(0, m, k);
        MemorySegment b = c.input(1, k, n);
        MemorySegment maskOut = asBool(c, c.input(2, m / bs, n / bs));
        MemorySegment maskLhs = asBool(c, c.input(3, m / bs, k / bs));
        MemorySegment maskRhs = asBool(c, c.input(4, k / bs, n / bs));
        c.store(c.op("mlx_block_masked_mm", res -> MlxC.mlx_block_masked_mm(res, a, b, bs, maskOut, maskLhs, maskRhs, c.stream())), 5);
    }

    // segmented_mm(a, b, segments, out, m, k, n): segments holds (k0, k1) pairs
    private static void segmentedMm(MlxCall c) {
        int m = c.intArg(4);
        int k = c.intArg(5);
        int n = c.intArg(6);
        MemorySegment a = c.input(0, m, k);
        MemorySegment b = c.input(1, k, n);
        MemorySegment segments = c.input(2, c.length(2) / 2, 2);
        c.store(c.op("mlx_segmented_mm", res -> MlxC.mlx_segmented_mm(res, a, b, segments, c.stream())), 3);
    }

    private static String mxMode(int mode) {
        return switch (mode) {
            case 0 -> "mxfp8";
            case 1 -> "nvfp4";
            default -> "mxfp4";
        };
    }

    // quantize_mx(w, wq, scales, rows, cols, mode): MX modes produce codes and scales, no biases
    private static void quantizeMx(MlxCall c) {
        MemorySegment w = c.input(0, c.intArg(3), c.intArg(4));
        String mode = mxMode(c.intArg(5));
        MemorySegment[] q = c.vectorOp("mlx_quantize", 2, vec -> MlxC.mlx_quantize(vec, w, c.noInt(), c.noInt(), c.cString(mode), MlxCall.none(), c.stream()));
        c.storeRaw(q[0], 1);
        c.storeRaw(q[1], 2);
    }

    // qqmm(x, w, wScales, out, m, k, n, mode)
    private static void qqmm(MlxCall c) {
        int m = c.intArg(4);
        int k = c.intArg(5);
        int n = c.intArg(6);
        String mode = mxMode(c.intArg(7));
        MemorySegment x = c.input(0, m, k);
        MemorySegment w = c.inputAs(1, MlxNativeLib.MLX_UINT32, n, c.length(1) / n);
        MemorySegment scales = c.input(2, n, c.length(2) / n);
        c.store(c.op("mlx_qqmm", res -> MlxC.mlx_qqmm(res, x, w, scales, c.noInt(), c.noInt(), c.cString(mode), MlxCall.none(), MlxCall.none(), c.stream())), 3);
    }

    // broadcast_arrays(a, b, outA, outB, rows, cols): a row [1, cols] and a column [rows, 1]
    private static void broadcastArrays(MlxCall c) {
        int rows = c.intArg(4);
        int cols = c.intArg(5);
        MemorySegment pair = c.vector(c.input(0, 1, cols), c.input(1, rows, 1));
        MemorySegment[] out = c.vectorOp("mlx_broadcast_arrays", 2, vec -> MlxC.mlx_broadcast_arrays(vec, pair, c.stream()));
        c.store(out[0], 2);
        c.store(out[1], 3);
    }

    // split(x, first, second, rows, cols) in halves, or split_sections(..., index) at a column, along axis 1
    private static void splitTwo(MlxCall c, boolean atIndex) {
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4));
        MemorySegment[] parts = atIndex ? c.vectorOp("mlx_split_sections", 2, vec -> MlxC.mlx_split_sections(vec, x, c.ints(c.intArg(5)), 1, 1, c.stream()))
                : c.vectorOp("mlx_split", 2, vec -> MlxC.mlx_split(vec, x, 2, 1, c.stream()));
        c.store(parts[0], 1);
        c.store(parts[1], 2);
    }

    // arange(out, start, stop, step): MLX must generate exactly out.length values
    private static void arange(MlxCall c) {
        double start = c.floatArg(1);
        double stop = c.floatArg(2);
        double step = c.floatArg(3);
        c.store(c.op("mlx_arange", res -> MlxC.mlx_arange(res, start, stop, step, c.dtype(0), c.stream())), 0);
    }

    // meshgrid(x, y, outX, outY, ij)
    private static void meshgrid(MlxCall c) {
        MemorySegment xy = c.vector(c.input(0, c.length(0)), c.input(1, c.length(1)));
        MemorySegment indexing = c.cString(c.boolArg(4) ? "ij" : "xy");
        MemorySegment[] grids = c.vectorOp("mlx_meshgrid", 2, vec -> MlxC.mlx_meshgrid(vec, xy, false, indexing, c.stream()));
        c.store(grids[0], 2);
        c.store(grids[1], 3);
    }

    // isclose(a, b, out, rtol, atol, equalNan)
    private static void isclose(MlxCall c) {
        int n = c.length(2);
        MemorySegment a = c.input(0, n);
        MemorySegment b = c.input(1, n);
        c.store(c.op("mlx_isclose", res -> MlxC.mlx_isclose(res, a, b, c.floatArg(3), c.floatArg(4), c.boolArg(5), c.stream())), 2);
    }

    // allclose(a, b, out, rtol, atol, equalNan): one flag
    private static void allclose(MlxCall c) {
        int n = c.length(0);
        MemorySegment a = c.input(0, n);
        MemorySegment b = c.input(1, n);
        c.store(c.op("mlx_allclose", res -> MlxC.mlx_allclose(res, a, b, c.floatArg(3), c.floatArg(4), c.boolArg(5), c.stream())), 2);
    }

    // array_equal(a, b, out, equalNan): one flag
    private static void arrayEqual(MlxCall c) {
        MemorySegment a = c.input(0, c.length(0));
        MemorySegment b = c.input(1, c.length(1));
        c.store(c.op("mlx_array_equal", res -> MlxC.mlx_array_equal(res, a, b, c.boolArg(3), c.stream())), 2);
    }

    // logical_and / logical_or(a, b, out) on byte masks
    private static void logical(MlxCall c, String name, Binary op) {
        int n = c.length(2);
        MemorySegment a = asBool(c, c.input(0, n));
        MemorySegment b = asBool(c, c.input(1, n));
        c.store(c.op(name, res -> op.apply(res, a, b, c.stream())), 2);
    }

    // logical_not(a, out) on a byte mask
    private static void logicalNot(MlxCall c) {
        MemorySegment a = asBool(c, c.input(0, c.length(1)));
        c.store(c.op("mlx_logical_not", res -> MlxC.mlx_logical_not(res, a, c.stream())), 1);
    }

    private static MemorySegment asBool(MlxCall c, MemorySegment bytes) {
        return c.op("mlx_astype", res -> MlxC.mlx_astype(res, bytes, MlxNativeLib.MLX_BOOL, c.stream()));
    }

    // nan_to_num(a, out, nan, posinf, neginf)
    private static void nanToNum(MlxCall c) {
        MemorySegment a = c.input(0, c.length(1));
        c.store(c.op("mlx_nan_to_num", res -> MlxC.mlx_nan_to_num(res, a, c.floatArg(2), c.optionalFloat(c.floatArg(3)), c.optionalFloat(c.floatArg(4)), c.stream())), 1);
    }

    // real / imag / conjugate(z, out): z holds (re, im) pairs
    private static void complexPart(MlxCall c, String name, Unary op, boolean complexOut) {
        MemorySegment z = complexInput(c, 0, c.length(0) / 2);
        MemorySegment y = c.op(name, res -> op.apply(res, z, c.stream()));
        c.store(complexOut ? complexAsFloats(c, y) : y, 1);
    }

    // complex [dims..., len] input given as float pairs: wrapped as float32 [dims..., 2 * len], viewed as complex64
    private static MemorySegment complexInput(MlxCall c, int index, int... shape) {
        int[] floats = shape.clone();
        floats[floats.length - 1] *= 2;
        MemorySegment pairs = c.input(index, floats);
        return c.op("mlx_view", res -> MlxC.mlx_view(res, pairs, MlxNativeLib.MLX_COMPLEX64, c.stream()));
    }

    // fft/ifft/rfft/irfft(x, out, rows, len, n, norm) along the last axis
    private static void fft1d(MlxCall c, String name, Fft1d op, boolean complexIn, boolean complexOut) {
        int rows = c.intArg(2);
        int len = c.intArg(3);
        MemorySegment x = complexIn ? complexInput(c, 0, rows, len) : c.input(0, rows, len);
        MemorySegment y = c.op(name, res -> op.apply(res, x, c.intArg(4), -1, c.intArg(5), c.stream()));
        c.store(complexOut ? complexAsFloats(c, y) : y, 1);
    }

    // 2D (x, out, batch, h, w, norm) or 3D (x, out, batch, d, h, w, norm) transforms over the last axes;
    // halfIn: the input's last axis holds w / 2 + 1 complex values (irfft2, irfftn)
    private static void fftNd(MlxCall c, String name, FftNd op, int dims, boolean complexIn, boolean complexOut, boolean halfIn) {
        int[] shape = new int[dims + 1];
        for (int i = 0; i <= dims; i++) {
            shape[i] = c.intArg(2 + i);
        }
        int norm = c.intArg(3 + dims);
        int[] sizes = java.util.Arrays.copyOfRange(shape, 1, dims + 1);
        int[] inShape = shape.clone();
        if (halfIn) {
            inShape[dims] = shape[dims] / 2 + 1;
        }
        MemorySegment x = complexIn ? complexInput(c, 0, inShape) : c.input(0, inShape);
        MemorySegment axes = dims == 2 ? c.ints(1, 2) : c.ints(1, 2, 3);
        MemorySegment y = c.op(name, res -> op.apply(res, x, c.ints(sizes), dims, axes, dims, norm, c.stream()));
        c.store(complexOut ? complexAsFloats(c, y) : y, 1);
    }

    // fftshift / ifftshift(x, out, rows, len) along the last axis
    private static void fftShift(MlxCall c, String name, FftShift op) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3));
        c.store(c.op(name, res -> op.apply(res, x, c.ints(1), 1, c.stream())), 1);
    }

    // linalg_cross(a, b, out, count): 3-vectors along the last axis
    private static void cross(MlxCall c) {
        int count = c.intArg(3);
        MemorySegment a = c.input(0, count, 3);
        MemorySegment b = c.input(1, count, 3);
        c.store(c.op("mlx_linalg_cross", res -> MlxC.mlx_linalg_cross(res, a, b, -1, c.stream())), 2);
    }

    // linalg_norm(x, out, rows, cols, ord): per row
    private static void norm(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3));
        double ord = c.floatArg(4);
        c.store(c.op("mlx_linalg_norm", res -> MlxC.mlx_linalg_norm(res, x, ord, c.ints(-1), 1, false, c.stream())), 1);
    }

    // linalg_norm_l2(x, out, rows, cols): per row
    private static void l2Norm(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3));
        c.store(c.op("mlx_linalg_norm_l2", res -> MlxC.mlx_linalg_norm_l2(res, x, c.ints(-1), 1, false, c.stream())), 1);
    }

    // linalg_norm_matrix(x, out, batch, rows, cols): Frobenius norm per matrix
    private static void frobeniusNorm(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3), c.intArg(4));
        c.store(c.op("mlx_linalg_norm_matrix", res -> MlxC.mlx_linalg_norm_matrix(res, x, c.cString("fro"), c.ints(1, 2), 2, false, c.stream())), 1);
    }

    // cholesky / cholesky_inv / tri_inv(a, out, batch, n, upper)
    private static void matrixUpper(MlxCall c, String name, MatrixUpper op) {
        int n = c.intArg(3);
        MemorySegment a = c.input(0, c.intArg(2), n, n);
        boolean upper = c.boolArg(4);
        c.store(c.op(name, res -> op.apply(res, a, upper, c.stream())), 1);
    }

    // linalg_lu(a, perm, l, u, batch, n)
    private static void lu(MlxCall c) {
        int n = c.intArg(5);
        MemorySegment a = c.input(0, c.intArg(4), n, n);
        MemorySegment[] plu = c.vectorOp("mlx_linalg_lu", 3, vec -> MlxC.mlx_linalg_lu(vec, a, c.stream()));
        c.store(plu[0], 1);
        c.store(plu[1], 2);
        c.store(plu[2], 3);
    }

    // linalg_lu_factor(a, lu, pivots, batch, n)
    private static void luFactor(MlxCall c) {
        int n = c.intArg(4);
        MemorySegment a = c.input(0, c.intArg(3), n, n);
        MemorySegment[] factors = c.pair("mlx_linalg_lu_factor", (r0, r1) -> MlxC.mlx_linalg_lu_factor(r0, r1, a, c.stream()));
        c.store(factors[0], 1);
        c.store(factors[1], 2);
    }

    // linalg_qr(a, q, r, batch, n)
    private static void qr(MlxCall c) {
        int n = c.intArg(4);
        MemorySegment a = c.input(0, c.intArg(3), n, n);
        MemorySegment[] qr = c.pair("mlx_linalg_qr", (r0, r1) -> MlxC.mlx_linalg_qr(r0, r1, a, c.stream()));
        c.store(qr[0], 1);
        c.store(qr[1], 2);
    }

    // linalg_eigh(a, values, vectors, batch, n, upper)
    private static void eigh(MlxCall c) {
        int n = c.intArg(4);
        MemorySegment a = c.input(0, c.intArg(3), n, n);
        MemorySegment uplo = c.cString(c.boolArg(5) ? "U" : "L");
        MemorySegment[] wv = c.pair("mlx_linalg_eigh", (r0, r1) -> MlxC.mlx_linalg_eigh(r0, r1, a, uplo, c.stream()));
        c.store(wv[0], 1);
        c.store(wv[1], 2);
    }

    // linalg_eigvalsh(a, values, batch, n, upper)
    private static void eigvalsh(MlxCall c) {
        int n = c.intArg(3);
        MemorySegment a = c.input(0, c.intArg(2), n, n);
        MemorySegment uplo = c.cString(c.boolArg(4) ? "U" : "L");
        c.store(c.op("mlx_linalg_eigvalsh", res -> MlxC.mlx_linalg_eigvalsh(res, a, uplo, c.stream())), 1);
    }

    // linalg_svd(a, u, s, vt, batch, n) or linalg_svd_values(a, s, batch, n)
    private static void svd(MlxCall c, boolean vectors) {
        int batchArg = vectors ? 4 : 2;
        int n = c.intArg(batchArg + 1);
        MemorySegment a = c.input(0, c.intArg(batchArg), n, n);
        MemorySegment[] usv = c.vectorOp("mlx_linalg_svd", vectors ? 3 : 1, vec -> MlxC.mlx_linalg_svd(vec, a, vectors, c.stream()));
        if (vectors) {
            c.store(usv[0], 1);
            c.store(usv[1], 2);
            c.store(usv[2], 3);
        } else {
            c.store(usv[0], 1);
        }
    }

    // complex64 viewed as interleaved float32 (real, imaginary) pairs
    private static MemorySegment complexAsFloats(MlxCall c, MemorySegment z) {
        return c.op("mlx_view", res -> MlxC.mlx_view(res, z, MlxNativeLib.MLX_FLOAT32, c.stream()));
    }

    // linalg_eig(a, values, vectors, batch, n): complex outputs as (re, im) pairs
    private static void eig(MlxCall c) {
        int n = c.intArg(4);
        MemorySegment a = c.input(0, c.intArg(3), n, n);
        MemorySegment[] wv = c.pair("mlx_linalg_eig", (r0, r1) -> MlxC.mlx_linalg_eig(r0, r1, a, c.stream()));
        c.store(complexAsFloats(c, wv[0]), 1);
        c.store(complexAsFloats(c, wv[1]), 2);
    }

    // linalg_eigvals(a, values, batch, n)
    private static void eigvals(MlxCall c) {
        int n = c.intArg(3);
        MemorySegment a = c.input(0, c.intArg(2), n, n);
        c.store(complexAsFloats(c, c.op("mlx_linalg_eigvals", res -> MlxC.mlx_linalg_eigvals(res, a, c.stream()))), 1);
    }

    // linalg_pinv(a, out, batch, n)
    private static void pinv(MlxCall c) {
        int n = c.intArg(3);
        MemorySegment a = c.input(0, c.intArg(2), n, n);
        c.store(c.op("mlx_linalg_pinv", res -> MlxC.mlx_linalg_pinv(res, a, c.stream())), 1);
    }

    // linalg_inv(a, out, batch, n)
    private static void inv(MlxCall c) {
        int n = c.intArg(3);
        MemorySegment a = c.input(0, c.intArg(2), n, n);
        c.store(c.op("mlx_linalg_inv", res -> MlxC.mlx_linalg_inv(res, a, c.stream())), 1);
    }

    // linalg_solve(a, rhs, x, batch, n, nrhs)
    private static void solve(MlxCall c) {
        int batch = c.intArg(3);
        int n = c.intArg(4);
        MemorySegment a = c.input(0, batch, n, n);
        MemorySegment b = c.input(1, batch, n, c.intArg(5));
        c.store(c.op("mlx_linalg_solve", res -> MlxC.mlx_linalg_solve(res, a, b, c.stream())), 2);
    }

    // linalg_solve_triangular(a, rhs, x, batch, n, nrhs, upper)
    private static void solveTriangular(MlxCall c) {
        int batch = c.intArg(3);
        int n = c.intArg(4);
        MemorySegment a = c.input(0, batch, n, n);
        MemorySegment b = c.input(1, batch, n, c.intArg(5));
        boolean upper = c.boolArg(6);
        c.store(c.op("mlx_linalg_solve_triangular", res -> MlxC.mlx_linalg_solve_triangular(res, a, b, upper, c.stream())), 2);
    }

    // conv1d(x, w, out, n, len, cin, cout, k, stride, padding, dilation, groups)
    private static void conv1d(MlxCall c) {
        int cin = c.intArg(5);
        int groups = c.intArg(11);
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4), cin);
        MemorySegment w = c.input(1, c.intArg(6), c.intArg(7), cin / groups);
        c.store(c.op("mlx_conv1d", res -> MlxC.mlx_conv1d(res, x, w, c.intArg(8), c.intArg(9), c.intArg(10), groups, c.stream())), 2);
    }

    // conv2d(x, w, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, groups)
    private static void conv2d(MlxCall c) {
        int cin = c.intArg(6);
        int groups = c.intArg(13);
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4), c.intArg(5), cin);
        MemorySegment w = c.input(1, c.intArg(7), c.intArg(8), c.intArg(9), cin / groups);
        int s = c.intArg(10);
        int p = c.intArg(11);
        int d = c.intArg(12);
        c.store(c.op("mlx_conv2d", res -> MlxC.mlx_conv2d(res, x, w, s, s, p, p, d, d, groups, c.stream())), 2);
    }

    // conv3d(x, w, out, n, d, h, w, cin, cout, kd, kh, kw, stride, padding, dilation, groups)
    private static void conv3d(MlxCall c) {
        int cin = c.intArg(7);
        int groups = c.intArg(15);
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4), c.intArg(5), c.intArg(6), cin);
        MemorySegment w = c.input(1, c.intArg(8), c.intArg(9), c.intArg(10), c.intArg(11), cin / groups);
        int s = c.intArg(12);
        int p = c.intArg(13);
        int d = c.intArg(14);
        c.store(c.op("mlx_conv3d", res -> MlxC.mlx_conv3d(res, x, w, s, s, s, p, p, p, d, d, d, groups, c.stream())), 2);
    }

    // conv_transpose1d(x, w, out, n, len, cin, cout, k, stride, padding, dilation, outputPadding, groups)
    private static void convTranspose1d(MlxCall c) {
        int cin = c.intArg(5);
        int groups = c.intArg(12);
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4), cin);
        MemorySegment w = c.input(1, c.intArg(6), c.intArg(7), cin / groups);
        c.store(c.op("mlx_conv_transpose1d", res -> MlxC.mlx_conv_transpose1d(res, x, w, c.intArg(8), c.intArg(9), c.intArg(10), c.intArg(11), groups, c.stream())), 2);
    }

    // conv_transpose2d(x, w, out, n, h, w, cin, cout, kh, kw, stride, padding, dilation, outputPadding, groups)
    private static void convTranspose2d(MlxCall c) {
        int cin = c.intArg(6);
        int groups = c.intArg(14);
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4), c.intArg(5), cin);
        MemorySegment w = c.input(1, c.intArg(7), c.intArg(8), c.intArg(9), cin / groups);
        int s = c.intArg(10);
        int p = c.intArg(11);
        int d = c.intArg(12);
        int o = c.intArg(13);
        c.store(c.op("mlx_conv_transpose2d", res -> MlxC.mlx_conv_transpose2d(res, x, w, s, s, p, p, d, d, o, o, groups, c.stream())), 2);
    }

    // conv_transpose3d(x, w, out, n, d, h, w, cin, cout, kd, kh, kw, stride, padding, dilation, outputPadding, groups)
    private static void convTranspose3d(MlxCall c) {
        int cin = c.intArg(7);
        int groups = c.intArg(16);
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4), c.intArg(5), c.intArg(6), cin);
        MemorySegment w = c.input(1, c.intArg(8), c.intArg(9), c.intArg(10), c.intArg(11), cin / groups);
        int s = c.intArg(12);
        int p = c.intArg(13);
        int d = c.intArg(14);
        int o = c.intArg(15);
        c.store(c.op("mlx_conv_transpose3d", res -> MlxC.mlx_conv_transpose3d(res, x, w, s, s, s, p, p, p, d, d, d, o, o, o, groups, c.stream())), 2);
    }

    // conv_general(x, w, out, n, h, w, cin, cout, kh, kw, stride, padLo, padHi, kernelDilation, inputDilation, groups, flip): 2D
    private static void convGeneral(MlxCall c) {
        int cin = c.intArg(6);
        int groups = c.intArg(15);
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4), c.intArg(5), cin);
        MemorySegment w = c.input(1, c.intArg(7), c.intArg(8), c.intArg(9), cin / groups);
        int s = c.intArg(10);
        int lo = c.intArg(11);
        int hi = c.intArg(12);
        int kd = c.intArg(13);
        int id = c.intArg(14);
        boolean flip = c.boolArg(16);
        c.store(c.op("mlx_conv_general", res -> MlxC.mlx_conv_general(res, x, w, c.ints(s, s), 2, c.ints(lo, lo), 2, c.ints(hi, hi), 2, c.ints(kd, kd), 2, c.ints(id, id), 2, groups, flip,
                c.stream())), 2);
    }

    // take(x, indices, out): out[i] = x[indices[i]], x flat
    private static void take(MlxCall c) {
        MemorySegment x = c.input(0, c.length(0));
        MemorySegment indices = c.input(1, c.length(1));
        c.store(c.op("mlx_take", res -> MlxC.mlx_take(res, x, indices, c.stream())), 2);
    }

    // take_axis(x, indices, out, outer, len, inner): along axis 1 of [outer, len, inner]
    private static void takeAxis(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4), c.intArg(5));
        MemorySegment indices = c.input(1, c.length(1));
        c.store(c.op("mlx_take_axis", res -> MlxC.mlx_take_axis(res, x, indices, 1, c.stream())), 2);
    }

    // take_along_axis(x, indices, out, outer, len, m, inner)
    private static void takeAlongAxis(MlxCall c) {
        int outer = c.intArg(3);
        int inner = c.intArg(6);
        MemorySegment x = c.input(0, outer, c.intArg(4), inner);
        MemorySegment indices = c.input(1, outer, c.intArg(5), inner);
        c.store(c.op("mlx_take_along_axis", res -> MlxC.mlx_take_along_axis(res, x, indices, 1, c.stream())), 2);
    }

    // put_along_axis / scatter_add_axis(x, indices, values, out, outer, len, m, inner)
    private static void alongAxisUpdate(MlxCall c, String name, AlongAxisUpdate op) {
        int outer = c.intArg(4);
        int m = c.intArg(6);
        int inner = c.intArg(7);
        MemorySegment x = c.input(0, outer, c.intArg(5), inner);
        MemorySegment indices = c.input(1, outer, m, inner);
        MemorySegment values = c.input(2, outer, m, inner);
        c.store(c.op(name, res -> op.apply(res, x, indices, values, 1, c.stream())), 3);
    }

    // gather(x, rows, cols, out, rowCount, colCount): out[i] = x[rows[i], cols[i]]
    private static void gather(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(4), c.intArg(5));
        MemorySegment indices = c.vector(c.input(1, c.length(1)), c.input(2, c.length(2)));
        c.store(c.op("mlx_gather", res -> MlxC.mlx_gather(res, x, indices, c.ints(0, 1), 2, c.ints(1, 1), 2, c.stream())), 3);
    }

    // gather_single(x, indices, out, rows, cols, sliceRows): out[i, r, :] = x[indices[i] + r, :]
    private static void gatherRows(MlxCall c) {
        int cols = c.intArg(4);
        MemorySegment x = c.input(0, c.intArg(3), cols);
        MemorySegment indices = c.input(1, c.length(1));
        int sliceRows = c.intArg(5);
        c.store(c.op("mlx_gather_single", res -> MlxC.mlx_gather_single(res, x, indices, 0, c.ints(sliceRows, cols), 2, c.stream())), 2);
    }

    // scatter*(x, rows, cols, updates, out, rowCount, colCount): point updates at (rows[i], cols[i])
    private static void scatterPoints(MlxCall c, String name, Scatter op) {
        MemorySegment x = c.input(0, c.intArg(5), c.intArg(6));
        int count = c.length(1);
        MemorySegment indices = c.vector(c.input(1, count), c.input(2, count));
        MemorySegment updates = c.input(3, count, 1, 1);
        c.store(c.op(name, res -> op.apply(res, x, indices, updates, c.ints(0, 1), 2, c.stream())), 4);
    }

    // scatter*_single(x, indices, updates, out, rows, cols): row updates at indices[i]
    private static void scatterRows(MlxCall c, String name, ScatterSingle op) {
        int cols = c.intArg(5);
        MemorySegment x = c.input(0, c.intArg(4), cols);
        int count = c.length(1);
        MemorySegment indices = c.input(1, count);
        MemorySegment updates = c.input(2, count, 1, cols);
        c.store(c.op(name, res -> op.apply(res, x, indices, updates, 0, c.stream())), 3);
    }

    // slice(x, out, rows, cols, r0, r1, rowStep, c0, c1, colStep)
    private static void slice(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3));
        MemorySegment start = c.ints(c.intArg(4), c.intArg(7));
        MemorySegment stop = c.ints(c.intArg(5), c.intArg(8));
        MemorySegment strides = c.ints(c.intArg(6), c.intArg(9));
        c.store(c.op("mlx_slice", res -> MlxC.mlx_slice(res, x, start, 2, stop, 2, strides, 2, c.stream())), 1);
    }

    // slice_dynamic(x, start, out, rows, cols, sliceRows): rows start[0] .. start[0] + sliceRows
    private static void sliceDynamic(MlxCall c) {
        int cols = c.intArg(4);
        MemorySegment x = c.input(0, c.intArg(3), cols);
        MemorySegment start = c.input(1, 1);
        int sliceRows = c.intArg(5);
        c.store(c.op("mlx_slice_dynamic", res -> MlxC.mlx_slice_dynamic(res, x, start, c.ints(0), 1, c.ints(sliceRows, cols), 2, c.stream())), 2);
    }

    // slice_update*(x, update, out, rows, cols, r0, c0, updateRows, updateCols)
    private static void sliceUpdate(MlxCall c, String name, SliceUpdate op) {
        MemorySegment x = c.input(0, c.intArg(3), c.intArg(4));
        int r0 = c.intArg(5);
        int c0 = c.intArg(6);
        int updateRows = c.intArg(7);
        int updateCols = c.intArg(8);
        MemorySegment update = c.input(1, updateRows, updateCols);
        MemorySegment start = c.ints(r0, c0);
        MemorySegment stop = c.ints(r0 + updateRows, c0 + updateCols);
        MemorySegment strides = c.ints(1, 1);
        c.store(c.op(name, res -> op.apply(res, x, update, start, 2, stop, 2, strides, 2, c.stream())), 2);
    }

    // slice_update_dynamic(x, update, start, out, rows, cols, updateRows): rows start[0] .. replaced
    private static void sliceUpdateDynamic(MlxCall c) {
        int cols = c.intArg(5);
        MemorySegment x = c.input(0, c.intArg(4), cols);
        MemorySegment update = c.input(1, c.intArg(6), cols);
        MemorySegment start = c.input(2, 1);
        c.store(c.op("mlx_slice_update_dynamic", res -> MlxC.mlx_slice_update_dynamic(res, x, update, start, c.ints(0), 1, c.stream())), 3);
    }

    // masked_scatter(x, mask, src, out): mask is a byte array, non-zero positions take src in order
    private static void maskedScatter(MlxCall c) {
        int n = c.length(0);
        MemorySegment x = c.input(0, n);
        MemorySegment maskBytes = c.input(1, n);
        MemorySegment mask = c.op("mlx_astype", res -> MlxC.mlx_astype(res, maskBytes, MlxNativeLib.MLX_BOOL, c.stream()));
        MemorySegment src = c.input(2, c.length(2));
        c.store(c.op("mlx_masked_scatter", res -> MlxC.mlx_masked_scatter(res, x, mask, src, c.stream())), 3);
    }

    // gather_mm(a, b, lhs, rhs, out, batchesA, batchesB, m, k, n)
    private static void gatherMm(MlxCall c) {
        int m = c.intArg(7);
        int k = c.intArg(8);
        int n = c.intArg(9);
        MemorySegment a = c.input(0, c.intArg(5), m, k);
        MemorySegment b = c.input(1, c.intArg(6), k, n);
        MemorySegment lhs = c.input(2, c.length(2));
        MemorySegment rhs = c.input(3, c.length(3));
        c.store(c.op("mlx_gather_mm", res -> MlxC.mlx_gather_mm(res, a, b, lhs, rhs, false, c.stream())), 4);
    }

    // whole(x, out, ...): a unary operation on x as a flat array
    private static void whole(MlxCall c, Unary op, String name) {
        MemorySegment x = c.input(0, c.length(0));
        c.store(c.op(name, res -> op.apply(res, x, c.stream())), 1);
    }

    // along_axis(x, out, outer, len, inner, ...): an operation along axis 1 of x viewed as [outer, len, inner]
    private static void alongAxis(MlxCall c, Unary op, String name) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3), c.intArg(4));
        c.store(c.op(name, res -> op.apply(res, x, c.stream())), 1);
    }

    // scan(x, out, outer, len, inner, reverse, inclusive): along axis 1 of x viewed as [outer, len, inner]
    private static void scan(MlxCall c, String name, Scan op) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3), c.intArg(4));
        boolean reverse = c.boolArg(5);
        boolean inclusive = c.boolArg(6);
        c.store(c.op(name, res -> op.apply(res, x, 1, reverse, inclusive, c.stream())), 1);
    }
    // reduce(x, out, ...): over the whole array, into out[0]
    private static void reduce(MlxCall c, String name, Reduce op) {
        MemorySegment x = c.input(0, c.length(0));
        c.store(c.op(name, res -> op.apply(res, x, false, c.stream())), 1);
    }

    // reduce_axis(x, out, outer, len, inner, ...): over axis 1 of x viewed as [outer, len, inner]
    private static void reduceAxis(MlxCall c, String name, ReduceAxis op) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3), c.intArg(4));
        c.store(c.op(name, res -> op.apply(res, x, 1, false, c.stream())), 1);
    }

    // reduce_axes(x, out, outer, len1, len2, inner, ...): over axes 1 and 2 of x viewed as [outer, len1, len2, inner]
    private static void reduceAxes(MlxCall c, String name, ReduceAxes op) {
        reduceAxes(c, name, op, false);
    }

    // with singleAxis, the arguments are (x, out, outer, len, inner) and axis 1 is reduced through the axes form
    private static void reduceAxes(MlxCall c, String name, ReduceAxes op, boolean singleAxis) {
        MemorySegment x;
        MemorySegment axes;
        long count;
        if (singleAxis) {
            x = c.input(0, c.intArg(2), c.intArg(3), c.intArg(4));
            axes = c.ints(1);
            count = 1;
        } else {
            x = c.input(0, c.intArg(2), c.intArg(3), c.intArg(4), c.intArg(5));
            axes = c.ints(1, 2);
            count = 2;
        }
        c.store(c.op(name, res -> op.apply(res, x, axes, count, false, c.stream())), 1);
    }

    // round(a, out, decimals)
    private static void round(MlxCall c) {
        int n = c.length(1);
        MemorySegment a = c.input(0, n);
        int decimals = c.intArg(2);
        c.store(c.op("mlx_round", res -> MlxC.mlx_round(res, a, decimals, c.stream())), 1);
    }

    // divmod(a, b, quotient, remainder)
    private static void divmod(MlxCall c) {
        int n = c.length(2);
        MemorySegment a = c.input(0, n);
        MemorySegment b = c.input(1, n);
        MemorySegment[] qr = c.vectorOp("mlx_divmod", 2, vec -> MlxC.mlx_divmod(vec, a, b, c.stream()));
        c.store(qr[0], 2);
        c.store(qr[1], 3);
    }

    // clip(a, out, lo, hi): integer bounds for integer arrays, float bounds otherwise
    private static void clip(MlxCall c) {
        int n = c.length(1);
        MemorySegment a = c.input(0, n);
        MemorySegment lo;
        MemorySegment hi;
        if (c.dtype(0) == MlxNativeLib.MLX_INT32) {
            lo = c.scalar(c.intArg(2));
            hi = c.scalar(c.intArg(3));
        } else {
            lo = c.scalar(c.floatArg(2));
            hi = c.scalar(c.floatArg(3));
        }
        c.store(c.op("mlx_clip", res -> MlxC.mlx_clip(res, a, lo, hi, c.stream())), 1);
    }

    // where(condition, x, y, out): condition is a byte mask, non-zero selects x
    private static void where(MlxCall c) {
        int n = c.length(3);
        MemorySegment condition = c.input(0, n);
        MemorySegment x = c.input(1, n);
        MemorySegment y = c.input(2, n);
        c.store(c.op("mlx_where", res -> MlxC.mlx_where(res, condition, x, y, c.stream())), 3);
    }

    private static void binary(MlxCall c, String name, Binary op) {
        int n = c.length(2);
        if (c.length(0) != n || c.length(1) != n) {
            throw new TornadoRuntimeException("[ERROR] MLX element-wise operands must have the same length");
        }
        MemorySegment a = c.input(0, n);
        MemorySegment b = c.input(1, n);
        c.store(c.op(name, res -> op.apply(res, a, b, c.stream())), 2);
    }

    // matmul(a, b, c, m, k, n): c[m, n] = a[m, k] @ b[k, n]
    private static void matmul(MlxCall c) {
        int m = c.intArg(3);
        int k = c.intArg(4);
        int n = c.intArg(5);
        MemorySegment a = c.input(0, m, k);
        MemorySegment b = c.input(1, k, n);
        c.store(c.op("mlx_matmul", res -> MlxC.mlx_matmul(res, a, b, c.stream())), 2);
    }

    // matmul_transposed(a, w, c, m, k, n): c[m, n] = a[m, k] @ w[n, k]^T (weights stored row per output)
    private static void matmulTransposed(MlxCall c) {
        int m = c.intArg(3);
        int k = c.intArg(4);
        int n = c.intArg(5);
        MemorySegment a = c.input(0, m, k);
        MemorySegment w = c.input(1, n, k);
        MemorySegment wt = c.op("mlx_transpose", res -> MlxC.mlx_transpose(res, w, c.stream()));
        c.store(c.op("mlx_matmul", res -> MlxC.mlx_matmul(res, a, wt, c.stream())), 2);
    }

    // addmm(cIn, a, b, out, m, k, n, alpha, beta): out = alpha * a @ b + beta * cIn
    private static void addmm(MlxCall c) {
        int m = c.intArg(4);
        int k = c.intArg(5);
        int n = c.intArg(6);
        float alpha = c.floatArg(7);
        float beta = c.floatArg(8);
        MemorySegment cIn = c.input(0, m, n);
        MemorySegment a = c.input(1, m, k);
        MemorySegment b = c.input(2, k, n);
        c.store(c.op("mlx_addmm", res -> MlxC.mlx_addmm(res, cIn, a, b, alpha, beta, c.stream())), 3);
    }

    // quantized_matmul(x, wq, scales, biases, y, m, k, n, groupSize, bits): y[m, n] = x[m, k] @ dequant(w[n, k])^T
    private static void quantizedMatmul(MlxCall c) {
        int m = c.intArg(5);
        int k = c.intArg(6);
        int n = c.intArg(7);
        int groupSize = c.intArg(8);
        int bits = c.intArg(9);
        MemorySegment x = c.input(0, m, k);
        MemorySegment wq = c.inputAs(1, MlxNativeLib.MLX_UINT32, n, k * bits / 32);
        MemorySegment scales = c.input(2, n, k / groupSize);
        MemorySegment biases = c.input(3, n, k / groupSize);
        c.store(c.op("mlx_quantized_matmul", res -> MlxC.mlx_quantized_matmul(res, x, wq, scales, biases, true, c.optionalInt(groupSize), c.optionalInt(bits), c.cString(AFFINE),
                c.stream())), 4);
    }

    // gather_qmm(x, wq, scales, biases, lhsIndices, rhsIndices, y, batches, experts, m, k, n, groupSize, bits):
    // y[b] = x[lhsIndices[b]] @ dequant(w[rhsIndices[b]])^T, with x [batches, m, k] and w [experts, n, k]
    private static void gatherQmm(MlxCall c) {
        int batches = c.intArg(7);
        int experts = c.intArg(8);
        int m = c.intArg(9);
        int k = c.intArg(10);
        int n = c.intArg(11);
        int groupSize = c.intArg(12);
        int bits = c.intArg(13);
        MemorySegment x = c.input(0, batches, m, k);
        MemorySegment wq = c.inputAs(1, MlxNativeLib.MLX_UINT32, experts, n, k * bits / 32);
        MemorySegment scales = c.input(2, experts, n, k / groupSize);
        MemorySegment biases = c.input(3, experts, n, k / groupSize);
        MemorySegment lhs = c.inputAs(4, MlxNativeLib.MLX_UINT32, batches);
        MemorySegment rhs = c.inputAs(5, MlxNativeLib.MLX_UINT32, batches);
        c.store(c.op("mlx_gather_qmm", res -> MlxC.mlx_gather_qmm(res, x, wq, scales, biases, lhs, rhs, true, c.optionalInt(groupSize), c.optionalInt(bits), c.cString(AFFINE), false,
                c.stream())), 6);
    }

    // quantize(w, wq, scales, biases, rows, cols, groupSize, bits): w[rows, cols] -> packed wq, scales, biases
    private static void quantize(MlxCall c) {
        int rows = c.intArg(4);
        int cols = c.intArg(5);
        int groupSize = c.intArg(6);
        int bits = c.intArg(7);
        MemorySegment w = c.input(0, rows, cols);
        MemorySegment[] q = c.vectorOp("mlx_quantize", 3, vec -> MlxC.mlx_quantize(vec, w, c.optionalInt(groupSize), c.optionalInt(bits), c.cString(AFFINE), MlxCall.none(), c.stream()));
        c.storeRaw(q[0], 1);
        c.store(q[1], 2);
        c.store(q[2], 3);
    }

    // dequantize(wq, scales, biases, w, rows, cols, groupSize, bits): packed wq, scales, biases -> w[rows, cols]
    private static void dequantize(MlxCall c) {
        int rows = c.intArg(4);
        int cols = c.intArg(5);
        int groupSize = c.intArg(6);
        int bits = c.intArg(7);
        MemorySegment wq = c.inputAs(0, MlxNativeLib.MLX_UINT32, rows, cols * bits / 32);
        MemorySegment scales = c.input(1, rows, cols / groupSize);
        MemorySegment biases = c.input(2, rows, cols / groupSize);
        c.store(c.op("mlx_dequantize", res -> MlxC.mlx_dequantize(res, wq, scales, biases, c.optionalInt(groupSize), c.optionalInt(bits), c.cString(AFFINE), MlxCall.none(), c.noDtype(),
                c.stream())), 3);
    }

    // fast_rms_norm(x, weight, out, rows, dim, eps)
    private static void rmsNorm(MlxCall c) {
        int rows = c.intArg(3);
        int dim = c.intArg(4);
        float eps = c.floatArg(5);
        MemorySegment x = c.input(0, rows, dim);
        MemorySegment w = c.input(1, dim);
        c.store(c.op("mlx_fast_rms_norm", res -> MlxC.mlx_fast_rms_norm(res, x, w, eps, c.stream())), 2);
    }

    // fast_layer_norm(x, weight, bias, out, rows, dim, eps)
    private static void layerNorm(MlxCall c) {
        int rows = c.intArg(4);
        int dim = c.intArg(5);
        float eps = c.floatArg(6);
        MemorySegment x = c.input(0, rows, dim);
        MemorySegment w = c.input(1, dim);
        MemorySegment b = c.input(2, dim);
        c.store(c.op("mlx_fast_layer_norm", res -> MlxC.mlx_fast_layer_norm(res, x, w, b, eps, c.stream())), 3);
    }

    // fast_rope(x, out, batch, heads, seqLen, headDim, dims, traditional, base, scale, offset): x [batch, heads, seqLen, headDim]
    private static void rope(MlxCall c) {
        int batch = c.intArg(2);
        int heads = c.intArg(3);
        int seqLen = c.intArg(4);
        int headDim = c.intArg(5);
        int dims = c.intArg(6);
        boolean traditional = c.boolArg(7);
        float base = c.floatArg(8);
        float scale = c.floatArg(9);
        int offset = c.intArg(10);
        MemorySegment x = c.input(0, batch, heads, seqLen, headDim);
        c.store(c.op("mlx_fast_rope", res -> MlxC.mlx_fast_rope(res, x, dims, traditional, c.optionalFloat(base), scale, offset, MlxCall.none(), c.stream())), 1);
    }

    // fast_rope_dynamic(x, offset, out, batch, heads, seqLen, headDim, dims, traditional, base, scale): offset is a one-element IntArray
    private static void ropeDynamic(MlxCall c) {
        int batch = c.intArg(3);
        int heads = c.intArg(4);
        int seqLen = c.intArg(5);
        int headDim = c.intArg(6);
        int dims = c.intArg(7);
        boolean traditional = c.boolArg(8);
        float base = c.floatArg(9);
        float scale = c.floatArg(10);
        MemorySegment x = c.input(0, batch, heads, seqLen, headDim);
        MemorySegment offset = c.input(1);
        c.store(c.op("mlx_fast_rope_dynamic", res -> MlxC.mlx_fast_rope_dynamic(res, x, dims, traditional, c.optionalFloat(base), scale, offset, MlxCall.none(), c.stream())), 2);
    }

    // fast_scaled_dot_product_attention(q, k, v, out, batch, qHeads, kvHeads, qLen, kvLen, headDim, scale, causal)
    private static void sdpa(MlxCall c) {
        int batch = c.intArg(4);
        int qHeads = c.intArg(5);
        int kvHeads = c.intArg(6);
        int qLen = c.intArg(7);
        int kvLen = c.intArg(8);
        int headDim = c.intArg(9);
        float scale = c.floatArg(10);
        boolean causal = c.boolArg(11);
        MemorySegment q = c.input(0, batch, qHeads, qLen, headDim);
        MemorySegment k = c.input(1, batch, kvHeads, kvLen, headDim);
        MemorySegment v = c.input(2, batch, kvHeads, kvLen, headDim);
        c.store(c.op("mlx_fast_scaled_dot_product_attention", res -> MlxC.mlx_fast_scaled_dot_product_attention(res, q, k, v, scale, c.cString(causal ? "causal" : ""), MlxCall.none(),
                MlxCall.none(), c.stream())), 3);
    }

    // softmax(x, out): over the whole array. Low-precision inputs are accumulated in float32.
    private static void softmax(MlxCall c) {
        MemorySegment x = c.input(0, c.length(0));
        c.store(c.op("mlx_softmax", res -> MlxC.mlx_softmax(res, x, true, c.stream())), 1);
    }

    // softmax_axis(x, out, rows, cols): per row
    private static void softmaxRows(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3));
        c.store(c.op("mlx_softmax_axis", res -> MlxC.mlx_softmax_axis(res, x, -1, true, c.stream())), 1);
    }

    // softmax_axes(x, out, d0, d1, d2): over the last two axes of a [d0, d1, d2] tensor
    private static void softmaxLastTwoAxes(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3), c.intArg(4));
        c.store(c.op("mlx_softmax_axes", res -> MlxC.mlx_softmax_axes(res, x, c.ints(1, 2), 2, true, c.stream())), 1);
    }

    // argmax(x, out): index of the largest element of the whole array, into a one-element IntArray
    private static void argmax(MlxCall c) {
        MemorySegment x = c.input(0, c.length(0));
        c.store(c.op("mlx_argmax", res -> MlxC.mlx_argmax(res, x, false, c.stream())), 1);
    }

    // argmax_axis(x, out, rows, cols): per row, into an IntArray of length rows
    private static void argmaxRows(MlxCall c) {
        MemorySegment x = c.input(0, c.intArg(2), c.intArg(3));
        c.store(c.op("mlx_argmax_axis", res -> MlxC.mlx_argmax_axis(res, x, -1, false, c.stream())), 1);
    }

    // topk(x, out, k): the k largest elements of the whole array, in no particular order
    private static void topk(MlxCall c) {
        int k = c.intArg(2);
        MemorySegment x = c.input(0, c.length(0));
        c.store(c.op("mlx_topk", res -> MlxC.mlx_topk(res, x, k, c.stream())), 1);
    }

    // topk_axis(x, out, rows, cols, k): the k largest elements of each row, in no particular order
    private static void topkRows(MlxCall c) {
        int rows = c.intArg(2);
        int cols = c.intArg(3);
        int k = c.intArg(4);
        MemorySegment x = c.input(0, rows, cols);
        c.store(c.op("mlx_topk_axis", res -> MlxC.mlx_topk_axis(res, x, k, -1, c.stream())), 1);
    }

    private static void unary(MlxCall c, String name, Unary op) {
        int n = c.length(1);
        if (c.length(0) != n) {
            throw new TornadoRuntimeException("[ERROR] MLX element-wise input and output must have the same length");
        }
        MemorySegment a = c.input(0, n);
        c.store(c.op(name, res -> op.apply(res, a, c.stream())), 1);
    }
}
