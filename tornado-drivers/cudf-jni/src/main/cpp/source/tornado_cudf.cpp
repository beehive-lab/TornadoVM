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
 * ---------------------------------------------------------------------------
 *
 * An extern "C" shim over RAPIDS cuDF, so that java.lang.foreign can call it.
 *
 * cuDF is C++: it returns std::unique_ptr<column> by value, takes table_view by value, and its
 * symbols are mangled. FFM can call none of that, which is why every other library TornadoVM
 * binds -- cuBLAS, cuFFT, cuDNN, cuSPARSE -- needs no shim and this one does.
 *
 * Plain C++, not CUDA: it declares no __global__ kernel of its own -- every kernel it runs is
 * already compiled inside libcudf -- and touches the CUDA runtime only for stream copies and a
 * synchronise. So a host compiler builds it and nvcc is not needed, which is also why this module
 * does not enable the CUDA language and cannot break a CUDA build that has no usable nvcc.
 *
 * Two rules the whole file obeys:
 *
 *   1. It allocates no caller-visible memory. Every operand is a device pointer TornadoVM already
 *      owns, wrapped in a non-owning column_view. That is what lets a cuDF task share a task graph
 *      with a generated kernel and read what it wrote, with no copy between them.
 *   2. No C++ exception crosses the boundary. cuDF reports failure by throwing, and an exception
 *      unwinding into the JVM aborts the process, so every entry point catches everything and
 *      returns a status. The message is kept for tornado_cudf_last_error.
 */
#include <cudf/aggregation.hpp>
#include <cudf/column/column.hpp>
#include <cudf/column/column_view.hpp>
#include <cudf/copying.hpp>
#include <cudf/groupby.hpp>
#include <cudf/join/join.hpp>
#include <cudf/reduction.hpp>
#include <cudf/sorting.hpp>
#include <cudf/table/table.hpp>
#include <cudf/table/table_view.hpp>
#include <cudf/types.hpp>

#include <rmm/cuda_stream_view.hpp>
#include <rmm/device_uvector.hpp>

#include <cuda_runtime.h>

#include <cstdint>
#include <cstring>
#include <exception>
#include <string>
#include <vector>

namespace {

thread_local std::string g_last_error;

/** A non-owning view over memory TornadoVM allocated. */
template <typename T>
cudf::column_view view_of(const void* data, cudf::size_type n, cudf::type_id id) {
    return cudf::column_view(cudf::data_type{id}, n, data, nullptr, 0);
}

int fail(const char* what, const std::exception& e) {
    g_last_error = std::string(what) + ": " + e.what();
    return 1;
}

int fail(const char* what) {
    g_last_error = std::string(what) + ": unknown error";
    return 2;
}

/** Copies a device column's payload into a buffer the caller already owns. */
cudaError_t copy_out(void* dst, const void* src, size_t bytes, cudaStream_t stream) {
    return cudaMemcpyAsync(dst, src, bytes, cudaMemcpyDeviceToDevice, stream);
}

}  // namespace

extern "C" {

/** The message behind the last non-zero status, for the Java side to append. */
const char* tornado_cudf_last_error() {
    return g_last_error.c_str();
}

/**
 * The permutation that sorts n keys ascending, written as row positions rather than as sorted data.
 *
 * This is what a SQL ORDER BY actually needs. Sorting key/value pairs reorders one carried column;
 * a query's rows have arbitrary columns of arbitrary types, and most of them are of no interest to
 * a device. Returning the permutation lets the caller reorder whatever it is holding, so nothing
 * but the key ever crosses the interconnect and no payload column has to be device-expressible.
 *
 * Stable: SQL's ORDER BY is stable over equal keys, and a caller that sorts twice on different
 * columns is relying on it.
 */
int tornado_cudf_sorted_order(void* stream, const void* keys, int32_t n, void* out_order) {
    try {
        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        cudf::column_view key_col = view_of<int32_t>(keys, n, cudf::type_id::INT32);
        cudf::table_view table{{key_col}};

        // The null order is immaterial: view_of builds every column with no validity mask, so no
        // column reaching this shim can contain a null. Exposing a choice the data cannot exercise
        // would be an API that lies, so the parameter is not offered.
        auto order = cudf::stable_sorted_order(table, {cudf::order::ASCENDING}, {cudf::null_order::AFTER}, view);

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        cudaError_t rc = copy_out(out_order, order->view().data<int32_t>(), static_cast<size_t>(n) * sizeof(int32_t), raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("sortedOrder copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("sortedOrder", e);
    } catch (...) {
        return fail("sortedOrder");
    }
}

/**
 * SUM of values grouped by key. Writes one row per distinct key, and the number of groups to
 * element 0 of out_groups -- which the caller cannot know in advance.
 */
int tornado_cudf_group_sum(void* stream, const void* keys, const void* values, int32_t n, void* out_keys, void* out_sums, void* out_groups) {
    try {
        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        cudf::column_view key_col = view_of<int32_t>(keys, n, cudf::type_id::INT32);
        cudf::column_view val_col = view_of<double>(values, n, cudf::type_id::FLOAT64);

        cudf::groupby::groupby grouper(cudf::table_view{{key_col}}, cudf::null_policy::EXCLUDE, cudf::sorted::NO);
        std::vector<cudf::groupby::aggregation_request> requests;
        cudf::groupby::aggregation_request request;
        request.values = val_col;
        request.aggregations.push_back(cudf::make_sum_aggregation<cudf::groupby_aggregation>());
        requests.push_back(std::move(request));

        auto [group_keys, results] = grouper.aggregate(requests, view);

        auto keys_view = group_keys->view().column(0);
        auto sums_view = results[0].results[0]->view();
        int32_t groups = keys_view.size();

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        cudaError_t rc = copy_out(out_keys, keys_view.data<int32_t>(), static_cast<size_t>(groups) * sizeof(int32_t), raw);
        if (rc == cudaSuccess) {
            rc = copy_out(out_sums, sums_view.data<double>(), static_cast<size_t>(groups) * sizeof(double), raw);
        }
        if (rc == cudaSuccess) {
            rc = cudaMemcpyAsync(out_groups, &groups, sizeof(int32_t), cudaMemcpyHostToDevice, raw);
        }
        if (rc != cudaSuccess) {
            g_last_error = std::string("groupSum copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("groupSum", e);
    } catch (...) {
        return fail("groupSum");
    }
}

/** Inclusive running total -- SUM(x) OVER (ORDER BY ... ROWS UNBOUNDED PRECEDING). */
int tornado_cudf_running_sum(void* stream, const void* values, int32_t n, void* out) {
    try {
        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        cudf::column_view val_col = view_of<double>(values, n, cudf::type_id::FLOAT64);

        auto scanned = cudf::scan(val_col, *cudf::make_sum_aggregation<cudf::scan_aggregation>(), cudf::scan_type::INCLUSIVE, cudf::null_policy::EXCLUDE, view);

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        cudaError_t rc = copy_out(out, scanned->view().data<double>(), static_cast<size_t>(n) * sizeof(double), raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("runningSum copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("runningSum", e);
    } catch (...) {
        return fail("runningSum");
    }
}

/**
 * Inner join on one 32-bit key column, writing matched row positions rather than joined rows.
 *
 * Indices because the caller has both sides on the device already, and gathering the payload is a
 * per-row map a generated kernel does well. A result larger than capacity fails rather than
 * truncating: a silently short join is a wrong answer.
 */
int tornado_cudf_inner_join(void* stream, const void* left_keys, int32_t left_count, const void* right_keys, int32_t right_count, int32_t capacity, void* out_left, void* out_right,
        void* out_count) {
    try {
        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        cudf::column_view left_col = view_of<int32_t>(left_keys, left_count, cudf::type_id::INT32);
        cudf::column_view right_col = view_of<int32_t>(right_keys, right_count, cudf::type_id::INT32);

        auto [left_idx, right_idx] = cudf::inner_join(cudf::table_view{{left_col}}, cudf::table_view{{right_col}}, cudf::null_equality::UNEQUAL, view);

        int32_t matched = static_cast<int32_t>(left_idx->size());
        if (matched > capacity) {
            g_last_error = "innerJoin produced " + std::to_string(matched) + " pairs but capacity is " + std::to_string(capacity);
            return 5;
        }

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        cudaError_t rc = copy_out(out_left, left_idx->data(), static_cast<size_t>(matched) * sizeof(int32_t), raw);
        if (rc == cudaSuccess) {
            rc = copy_out(out_right, right_idx->data(), static_cast<size_t>(matched) * sizeof(int32_t), raw);
        }
        if (rc == cudaSuccess) {
            rc = cudaMemcpyAsync(out_count, &matched, sizeof(int32_t), cudaMemcpyHostToDevice, raw);
        }
        if (rc != cudaSuccess) {
            g_last_error = std::string("innerJoin copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("innerJoin", e);
    } catch (...) {
        return fail("innerJoin");
    }
}

}  // extern "C"
