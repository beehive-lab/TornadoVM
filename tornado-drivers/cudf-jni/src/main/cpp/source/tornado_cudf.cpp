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
#include <cudf/io/parquet.hpp>
#include <cudf/io/types.hpp>
#include <cudf/column/column.hpp>
#include <cudf/column/column_view.hpp>
#include <cudf/groupby.hpp>
#include <cudf/filling.hpp>
#include <cudf/join/join.hpp>
#include <cudf/reduction.hpp>
#include <cudf/sorting.hpp>
#include <cudf/stream_compaction.hpp>
#include <cudf/table/table.hpp>
#include <cudf/table/table_view.hpp>
#include <cudf/types.hpp>
#include <cudf/strings/contains.hpp>
#include <cudf/strings/regex/regex_program.hpp>
#include <cudf/strings/strings_column_view.hpp>
#include <cudf/unary.hpp>

#include <rmm/cuda_stream_view.hpp>
#include <rmm/mr/cuda_memory_resource.hpp>
#include <rmm/mr/per_device_resource.hpp>
#include <rmm/mr/cuda_async_memory_resource.hpp>
#include <rmm/device_uvector.hpp>

#include <cuda_runtime.h>

#include <cstdint>
#include <map>
#include <string>
#include <vector>
#include <cstring>
#include <cstdlib>
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

/**
 * The aggregation codes the Java side sends, which have to be stable because they cross the ABI.
 * They match CudfAggregation's ordinals and nothing else may reorder them.
 */
enum tornado_agg : int32_t { AGG_SUM = 0, AGG_MIN = 1, AGG_MAX = 2, AGG_MEAN = 3, AGG_COUNT = 4 };

/** Builds the groupby aggregation named by a code, or nullptr if the code is not one. */
std::unique_ptr<cudf::groupby_aggregation> groupby_agg_for(int32_t code) {
    switch (code) {
        case AGG_SUM:
            return cudf::make_sum_aggregation<cudf::groupby_aggregation>();
        case AGG_MIN:
            return cudf::make_min_aggregation<cudf::groupby_aggregation>();
        case AGG_MAX:
            return cudf::make_max_aggregation<cudf::groupby_aggregation>();
        case AGG_MEAN:
            return cudf::make_mean_aggregation<cudf::groupby_aggregation>();
        case AGG_COUNT:
            return cudf::make_count_aggregation<cudf::groupby_aggregation>();
        default:
            return nullptr;
    }
}

/** The same for a whole-column reduction. COUNT is excluded: ungrouped, it is the row count. */
std::unique_ptr<cudf::reduce_aggregation> reduce_agg_for(int32_t code) {
    switch (code) {
        case AGG_SUM:
            return cudf::make_sum_aggregation<cudf::reduce_aggregation>();
        case AGG_MIN:
            return cudf::make_min_aggregation<cudf::reduce_aggregation>();
        case AGG_MAX:
            return cudf::make_max_aggregation<cudf::reduce_aggregation>();
        case AGG_MEAN:
            return cudf::make_mean_aggregation<cudf::reduce_aggregation>();
        default:
            return nullptr;
    }
}

/**
 * Installs an RMM pool the first time anything here touches the device.
 *
 * Without this, cuDF allocates through RMM's default resource, which is a raw cudaMalloc/cudaFree
 * per allocation -- and a Parquet read makes many: one per column, per page, plus decompression
 * scratch. Every one of those synchronises. spark-rapids treats the pool as mandatory and has a
 * whole GpuDeviceManager to size it; this module had none at all, which is why its read ran at
 * 2.2 GB/s against a page cache that delivers the same file at 13.7 GB/s.
 *
 * Sized from free device memory so it coexists with TornadoVM's own allocations rather than
 * claiming the card. TORNADO_CUDF_POOL_MB overrides the initial size; 0 disables pooling.
 */
void ensure_pool() {
    static bool installed = false;
    if (installed) {
        return;
    }
    installed = true;
    try {
        if (const char* env = std::getenv("TORNADO_CUDF_POOL_MB")) {
            if (std::strtoul(env, nullptr, 10) == 0) {
                return;  // an escape hatch for measuring what the pool is worth
            }
        }
        // CUDA's own stream-ordered pool, via cudaMallocAsync. A pool_memory_resource would do
        // the same and needs an initial and a maximum size chosen up front; this one grows and
        // returns memory on its own, which matters because TornadoVM allocates from the same card
        // and a fixed reservation would have to be tuned against it.
        static rmm::mr::cuda_async_memory_resource async_mr;
        rmm::mr::set_current_device_resource(async_mr);
    } catch (...) {
        // A pool is an optimisation; the default resource still works without it.
    }
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
    ensure_pool();
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
    ensure_pool();
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
    ensure_pool();
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
    ensure_pool();
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


/**
 * SUM, MIN, MAX, MEAN or COUNT per distinct key, over several value columns at once.
 *
 * The general form of group_sum, and the one a real aggregation needs: a k-means iteration sums d
 * coordinate columns per cluster, and doing that as d separate calls re-hashes the same keys d
 * times for no reason. cuDF takes a vector of requests; this passes it one per column.
 *
 * Columns are packed with a stride of n -- column c of the input starts at values[c * n] -- and the
 * results the same way, at out_results[c * n], of which the first *out_groups entries are live. The
 * stride is n rather than the group count because the caller sizes the buffer before knowing how
 * many groups there will be.
 *
 * COUNT is cast to FLOAT64 on the way out so that one output buffer serves every aggregation; it is
 * exact to 2^53, which is more rows than the rest of this shim can address.
 */
int tornado_cudf_group_aggregate(void* stream, const void* keys, const void* values, int32_t n, int32_t columns, int32_t agg, void* out_keys, void* out_results,
        void* out_groups) {
    ensure_pool();
    try {
        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        if (columns < 1) {
            g_last_error = "groupAggregate needs at least one value column, got " + std::to_string(columns);
            return 6;
        }
        if (groupby_agg_for(agg) == nullptr) {
            g_last_error = "groupAggregate: unknown aggregation code " + std::to_string(agg);
            return 6;
        }

        cudf::column_view key_col = view_of<int32_t>(keys, n, cudf::type_id::INT32);
        const double* base = static_cast<const double*>(values);

        std::vector<cudf::column_view> value_cols;
        std::vector<cudf::groupby::aggregation_request> requests;
        value_cols.reserve(columns);
        requests.reserve(columns);
        for (int32_t c = 0; c < columns; ++c) {
            value_cols.push_back(view_of<double>(base + static_cast<size_t>(c) * n, n, cudf::type_id::FLOAT64));
        }
        for (int32_t c = 0; c < columns; ++c) {
            cudf::groupby::aggregation_request request;
            request.values = value_cols[c];
            request.aggregations.push_back(groupby_agg_for(agg));
            requests.push_back(std::move(request));
        }

        cudf::groupby::groupby grouper(cudf::table_view{{key_col}}, cudf::null_policy::EXCLUDE, cudf::sorted::NO);
        auto [group_keys, results] = grouper.aggregate(requests, view);

        auto keys_view = group_keys->view().column(0);
        int32_t groups = keys_view.size();
        cudaStream_t raw = static_cast<cudaStream_t>(stream);

        cudaError_t rc = copy_out(out_keys, keys_view.data<int32_t>(), static_cast<size_t>(groups) * sizeof(int32_t), raw);
        double* out_base = static_cast<double*>(out_results);
        for (int32_t c = 0; c < columns && rc == cudaSuccess; ++c) {
            auto column = results[c].results[0]->view();
            // COUNT comes back as INT32; everything else is already FLOAT64.
            std::unique_ptr<cudf::column> promoted;
            if (column.type().id() != cudf::type_id::FLOAT64) {
                promoted = cudf::cast(column, cudf::data_type{cudf::type_id::FLOAT64}, view);
                column = promoted->view();
            }
            rc = copy_out(out_base + static_cast<size_t>(c) * n, column.data<double>(), static_cast<size_t>(groups) * sizeof(double), raw);
        }
        if (rc == cudaSuccess) {
            rc = cudaMemcpyAsync(out_groups, &groups, sizeof(int32_t), cudaMemcpyHostToDevice, raw);
        }
        if (rc != cudaSuccess) {
            g_last_error = std::string("groupAggregate copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("groupAggregate", e);
    } catch (...) {
        return fail("groupAggregate");
    }
}

/**
 * SUM, MIN, MAX or MEAN over a whole column, written to out[0].
 *
 * What normalisation and a convergence test need, and the one shape that is not a group-by: "has
 * any centroid moved more than epsilon" is a MAX over a column of deltas, and there is no key to
 * group by.
 */
int tornado_cudf_reduce(void* stream, const void* values, int32_t n, int32_t op, void* out) {
    ensure_pool();
    try {
        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        auto aggregation = reduce_agg_for(op);
        if (aggregation == nullptr) {
            g_last_error = "reduce: unknown or unsupported aggregation code " + std::to_string(op) + " (COUNT of a whole column is its row count)";
            return 6;
        }

        cudf::column_view col = view_of<double>(values, n, cudf::type_id::FLOAT64);
        auto result = cudf::reduce(col, *aggregation, cudf::data_type{cudf::type_id::FLOAT64}, view);
        auto* typed = static_cast<cudf::numeric_scalar<double>*>(result.get());
        if (!typed->is_valid(view)) {
            g_last_error = "reduce produced no value; the column is empty";
            return 6;
        }

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        cudaError_t rc = copy_out(out, typed->data(), sizeof(double), raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("reduce copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("reduce", e);
    } catch (...) {
        return fail("reduce");
    }
}

/**
 * The positions where an 8-bit mask is non-zero, in order.
 *
 * Stream compaction, which is the filter every pipeline needs and the one operation here a
 * generated kernel genuinely cannot do: computing the predicate per row is a map, but moving the
 * survivors together is cross-row and no @Parallel loop states it.
 *
 * Positions rather than compacted rows, for the same reason sorted_order returns a permutation: the
 * caller gathers whatever columns it is holding, and none of them has to be device-expressible.
 * Element 0 of out_count receives how many survived, and a result larger than capacity fails rather
 * than truncating.
 */
int tornado_cudf_selected_indices(void* stream, const void* mask, int32_t n, int32_t capacity, void* out_indices, void* out_count) {
    ensure_pool();
    try {
        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        cudf::column_view mask_col = view_of<int8_t>(mask, n, cudf::type_id::BOOL8);

        cudf::numeric_scalar<int32_t> zero(0, true, view);
        cudf::numeric_scalar<int32_t> one(1, true, view);
        auto positions = cudf::sequence(n, zero, one, view);

        auto kept = cudf::apply_boolean_mask(cudf::table_view{{positions->view()}}, mask_col, view);
        auto kept_view = kept->view().column(0);
        int32_t selected = kept_view.size();
        if (selected > capacity) {
            g_last_error = "selectedIndices kept " + std::to_string(selected) + " rows but capacity is " + std::to_string(capacity);
            return 5;
        }

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        cudaError_t rc = cudaSuccess;
        if (selected > 0) {
            rc = copy_out(out_indices, kept_view.data<int32_t>(), static_cast<size_t>(selected) * sizeof(int32_t), raw);
        }
        if (rc == cudaSuccess) {
            rc = cudaMemcpyAsync(out_count, &selected, sizeof(int32_t), cudaMemcpyHostToDevice, raw);
        }
        if (rc != cudaSuccess) {
            g_last_error = std::string("selectedIndices copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("selectedIndices", e);
    } catch (...) {
        return fail("selectedIndices");
    }
}

/**
 * The permutation that sorts several key columns, each ascending or descending.
 *
 * The general form of sorted_order. Key columns are packed with a stride of n -- column c starts at
 * keys[c * n] -- and bit c of descending_mask makes column c descending. Stable, so a caller can
 * still rely on ties keeping their arrival order.
 */
int tornado_cudf_sorted_order_multi(void* stream, const void* keys, int32_t n, int32_t key_columns, int32_t descending_mask, void* out_order) {
    ensure_pool();
    try {
        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        if (key_columns < 1 || key_columns > 31) {
            g_last_error = "sortedOrderMulti takes 1 to 31 key columns, got " + std::to_string(key_columns);
            return 6;
        }

        const int32_t* base = static_cast<const int32_t*>(keys);
        std::vector<cudf::column_view> key_cols;
        std::vector<cudf::order> orders;
        std::vector<cudf::null_order> nulls;
        key_cols.reserve(key_columns);
        for (int32_t c = 0; c < key_columns; ++c) {
            key_cols.push_back(view_of<int32_t>(base + static_cast<size_t>(c) * n, n, cudf::type_id::INT32));
            orders.push_back((descending_mask & (1 << c)) ? cudf::order::DESCENDING : cudf::order::ASCENDING);
            nulls.push_back(cudf::null_order::AFTER);
        }

        auto order = cudf::stable_sorted_order(cudf::table_view{key_cols}, orders, nulls, view);

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        cudaError_t rc = copy_out(out_order, order->view().data<int32_t>(), static_cast<size_t>(n) * sizeof(int32_t), raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("sortedOrderMulti copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("sortedOrderMulti", e);
    } catch (...) {
        return fail("sortedOrderMulti");
    }
}

/**
 * Parquet footer metadata, without reading a single column of data.
 *
 * Sizing has to come before allocation: every other entry point here writes into a buffer the
 * caller already owns, and a caller cannot own a buffer for a file whose row count it does not
 * know. libcudf reads the footer on its own, so this costs a seek and a parse rather than a scan.
 *
 * out_counts receives, in order: total rows, row-group count, column count.
 */
int tornado_cudf_parquet_metadata(const char* path, int64_t* out_counts) {
    try {
        if (path == nullptr || out_counts == nullptr) {
            g_last_error = "parquetMetadata: null path or output";
            return 5;
        }
        auto source = cudf::io::source_info{std::string(path)};
        auto metadata = cudf::io::read_parquet_metadata(source);
        out_counts[0] = static_cast<int64_t>(metadata.num_rows());
        out_counts[1] = static_cast<int64_t>(metadata.num_rowgroups());
        out_counts[2] = static_cast<int64_t>(metadata.schema().root().num_children());
        return 0;
    } catch (const std::exception& e) {
        return fail("parquetMetadata", e);
    } catch (...) {
        return fail("parquetMetadata");
    }
}

/**
 * The rows in each row group, so a caller can turn Flink's split assignment into a row-group range
 * and size the buffer for exactly the rows it will read.
 *
 * A capacity smaller than the row-group count is refused rather than partly filled: a short answer
 * here is a buffer too small for the read that follows, which is a corruption rather than an error
 * anyone would notice.
 */
int tornado_cudf_parquet_rowgroup_rows(const char* path, int32_t capacity, int64_t* out_rows) {
    try {
        if (path == nullptr || out_rows == nullptr) {
            g_last_error = "parquetRowGroupRows: null path or output";
            return 5;
        }
        auto source = cudf::io::source_info{std::string(path)};
        auto metadata = cudf::io::read_parquet_metadata(source);
        const auto groups = metadata.num_rowgroups();
        if (static_cast<int64_t>(capacity) < static_cast<int64_t>(groups)) {
            g_last_error = "parquetRowGroupRows: capacity " + std::to_string(capacity) +
                           " is smaller than the row-group count " + std::to_string(groups);
            return 6;
        }
        // rowgroup_metadata() gives one map a row group, keyed by the names Parquet's own footer
        // uses, so this reads what the file says rather than what a reader inferred.
        const auto per_group = metadata.rowgroup_metadata();
        for (size_t i = 0; i < per_group.size(); i++) {
            const auto found = per_group[i].find("num_rows");
            if (found == per_group[i].end()) {
                g_last_error = "parquetRowGroupRows: row group " + std::to_string(i) +
                               " has no num_rows in its footer";
                return 7;
            }
            out_rows[i] = static_cast<int64_t>(found->second);
        }
        return 0;
    } catch (const std::exception& e) {
        return fail("parquetRowGroupRows", e);
    } catch (...) {
        return fail("parquetRowGroupRows");
    }
}

/**
 * Reads a row-group range of a Parquet file straight onto the device.
 *
 * This is the point of the whole exercise. Without it a row reaches a device by being decoded on
 * the CPU, turned into a Flink row, handed to an operator and staged field by field into a
 * TornadoVM array: measured at ~17.3 ns a field for the decode and ~17.0 ns a field for the
 * staging, against ~57 ms of actual device work for 8M rows of 31 columns. With it, neither
 * happens.
 *
 * Column selection is by index into the file's schema, resolved to names here because that is what
 * cuDF's reader takes. Indices rather than names in the ABI because the caller is a planner that
 * has already decided which columns the query needs, and it knows them positionally.
 *
 * The operand contract is this module's usual one, and it is checked rather than assumed: the key
 * column is INT32, the value columns are FP64, and no column may contain a null. A file that
 * breaks it is refused -- reading a nullable column as dense would return whatever the padding
 * held wherever a value was absent, and a row count of the right length would agree with it.
 *
 * out_values is packed with a stride of `rows`, so column c starts at element c * rows -- the same
 * convention tornado_cudf_sorted_order_multi already uses for its key columns.
 *
 * One device-to-device copy is paid per column: cuDF allocates the table from its own memory
 * resource, and TornadoVM owns the buffers a task graph operates on. Handing cuDF's allocation
 * over directly would need TornadoVM to adopt an rmm buffer, which is a larger change than this.
 */
// value_width is the caller's buffer element width in bytes: 8 for a DoubleArray of FP64 columns,
// 4 for a FloatArray of FP32 ones. It is declared rather than inferred because the shim cannot see
// the element type of the buffer it was handed, and guessing from the file's column type would
// write FP64 values into a half-sized buffer whenever the two disagreed.
//
// A mismatch between the declared width and the file's column type is refused rather than cast. A
// cast is a change of value, and which side of it a query should land on is a decision for the
// planner that typed the query, not for the reader.
int tornado_cudf_read_parquet(void* stream, const char* path, int32_t row_group_start, int32_t row_group_count, int32_t int_column, int32_t double_column_count,
        const int32_t* double_columns, int64_t rows, void* out_keys, void* out_values, int32_t value_width) {
    ensure_pool();
    try {
        if (path == nullptr || (double_column_count > 0 && double_columns == nullptr)) {
            g_last_error = "readParquet: null path or column list";
            return 5;
        }
        if (double_column_count > 0 && value_width != 4 && value_width != 8) {
            g_last_error = "readParquet: value width must be 4 or 8 bytes, not " + std::to_string(value_width);
            return 5;
        }
        if (int_column < 0 && double_column_count == 0) {
            g_last_error = "readParquet: no columns requested";
            return 5;
        }

        auto source = cudf::io::source_info{std::string(path)};
        auto metadata = cudf::io::read_parquet_metadata(source);
        const auto& root = metadata.schema().root();
        const int32_t schema_columns = static_cast<int32_t>(root.num_children());

        // Resolve indices to names, and keep the order the caller asked for so that the copy-out
        // loop below can address its outputs positionally.
        std::vector<std::string> names;
        if (int_column >= 0) {
            if (int_column >= schema_columns) {
                g_last_error = "readParquet: key column " + std::to_string(int_column) + " is beyond the file's " + std::to_string(schema_columns) + " columns";
                return 6;
            }
            names.push_back(root.child(int_column).name());
        }
        for (int32_t c = 0; c < double_column_count; c++) {
            const int32_t index = double_columns[c];
            if (index < 0 || index >= schema_columns) {
                g_last_error = "readParquet: value column " + std::to_string(index) + " is beyond the file's " + std::to_string(schema_columns) + " columns";
                return 6;
            }
            names.push_back(root.child(index).name());
        }

        auto options = cudf::io::parquet_reader_options::builder(source).columns(names).build();
        if (row_group_count > 0) {
            std::vector<cudf::size_type> groups;
            for (int32_t g = 0; g < row_group_count; g++) {
                groups.push_back(static_cast<cudf::size_type>(row_group_start + g));
            }
            options.set_row_groups({groups});
        }

        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        auto result = cudf::io::read_parquet(options, view);
        const auto table = result.tbl->view();

        if (static_cast<int64_t>(table.num_rows()) != rows) {
            g_last_error = "readParquet: file gave " + std::to_string(table.num_rows()) + " rows where the caller sized for " + std::to_string(rows);
            return 7;
        }

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        int32_t column = 0;

        if (int_column >= 0) {
            const auto col = table.column(column++);
            if (col.type().id() != cudf::type_id::INT32) {
                g_last_error = "readParquet: key column is not INT32";
                return 8;
            }
            if (col.null_count() != 0) {
                g_last_error = "readParquet: key column contains nulls; declare it NOT NULL or use a provider that reads validity masks";
                return 9;
            }
            if (out_keys == nullptr) {
                g_last_error = "readParquet: a key column was requested with no buffer to put it in";
                return 5;
            }
            cudaError_t rc = copy_out(out_keys, col.data<int32_t>(), static_cast<size_t>(rows) * sizeof(int32_t), raw);
            if (rc != cudaSuccess) {
                g_last_error = std::string("readParquet key copy-out: ") + cudaGetErrorString(rc);
                return 3;
            }
        }

        const bool narrow = value_width == 4;
        const cudf::type_id expected = narrow ? cudf::type_id::FLOAT32 : cudf::type_id::FLOAT64;
        for (int32_t c = 0; c < double_column_count; c++) {
            const auto col = table.column(column++);
            if (col.type().id() != expected) {
                g_last_error = "readParquet: value column " + std::to_string(c) + " is not " + (narrow ? "FP32" : "FP64")
                        + "; the file's column and the buffer the caller sized must agree, and this reader does not cast between them";
                return 8;
            }
            if (col.null_count() != 0) {
                g_last_error = "readParquet: value column " + std::to_string(c) + " contains nulls; declare it NOT NULL or use a provider that reads validity masks";
                return 9;
            }
            // Column-major, one column after another at the caller's stride, which is what both
            // the packed kernel input and the existing DoubleArray callers expect.
            const size_t offset = static_cast<size_t>(c) * static_cast<size_t>(rows);
            cudaError_t rc;
            if (narrow) {
                rc = copy_out(static_cast<float*>(out_values) + offset, col.data<float>(), static_cast<size_t>(rows) * sizeof(float), raw);
            } else {
                rc = copy_out(static_cast<double*>(out_values) + offset, col.data<double>(), static_cast<size_t>(rows) * sizeof(double), raw);
            }
            if (rc != cudaSuccess) {
                g_last_error = std::string("readParquet value copy-out: ") + cudaGetErrorString(rc);
                return 3;
            }
        }

        // The table is freed when result goes out of scope, so the copies have to have landed.
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("readParquet", e);
    } catch (...) {
        return fail("readParquet");
    }
}

/**
 * Reads a STRING column of a Parquet file into TornadoVM-owned buffers.
 *
 * The two buffers are the two halves of a cuDF strings column as libcudf lays it out: an INT32
 * offsets array of `rows + 1` entries and a byte blob the offsets index into. Copying them out
 * rather than keeping cuDF's own column is what lets everything after the read be an ordinary
 * TornadoVM buffer -- a generated kernel can read the bytes, and tornado_cudf_contains_re
 * rebuilds a column_view over them without owning anything.
 *
 * `out_chars_bytes` reports the decoded size, which the caller cannot know from the footer: a
 * Parquet column chunk's uncompressed size bounds it but does not give it. A caller that sized
 * too small is told so rather than overrun, and `chars_capacity` is what it sized.
 */
int tornado_cudf_read_parquet_strings(void* stream, const char* path, int32_t row_group_start, int32_t row_group_count, int32_t column, int64_t rows,
        void* out_offsets, void* out_chars, int64_t chars_capacity, int64_t* out_chars_bytes) {
    ensure_pool();
    try {
        if (path == nullptr || out_offsets == nullptr || out_chars == nullptr) {
            g_last_error = "readParquetStrings: null path or buffer";
            return 5;
        }
        auto source = cudf::io::source_info{std::string(path)};
        auto metadata = cudf::io::read_parquet_metadata(source);
        const auto& root = metadata.schema().root();
        const int32_t schema_columns = static_cast<int32_t>(root.num_children());
        if (column < 0 || column >= schema_columns) {
            g_last_error = "readParquetStrings: column " + std::to_string(column) + " is beyond the file's " + std::to_string(schema_columns) + " columns";
            return 6;
        }
        auto options = cudf::io::parquet_reader_options::builder(source).columns({root.child(column).name()}).build();
        if (row_group_count > 0) {
            std::vector<cudf::size_type> groups;
            for (int32_t g = 0; g < row_group_count; g++) {
                groups.push_back(row_group_start + g);
            }
            options.set_row_groups({groups});
        }

        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        auto result = cudf::io::read_parquet(options, view);
        const auto table = result.tbl->view();
        if (static_cast<int64_t>(table.num_rows()) != rows) {
            g_last_error = "readParquetStrings: file gave " + std::to_string(table.num_rows()) + " rows where the caller sized for " + std::to_string(rows);
            return 7;
        }
        const auto col = table.column(0);
        if (col.type().id() != cudf::type_id::STRING) {
            g_last_error = "readParquetStrings: column " + std::to_string(column) + " is not a STRING column";
            return 8;
        }
        if (col.null_count() != 0) {
            g_last_error = "readParquetStrings: the column contains nulls; declare it NOT NULL or use a provider that reads validity masks";
            return 9;
        }

        cudf::strings_column_view scv(col);
        const int64_t chars_bytes = scv.chars_size(view);
        if (out_chars_bytes != nullptr) {
            *out_chars_bytes = chars_bytes;
        }
        if (chars_bytes > chars_capacity) {
            g_last_error = "readParquetStrings: the column decodes to " + std::to_string(chars_bytes) + " bytes and the caller sized " + std::to_string(chars_capacity);
            return 6;
        }

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        const auto offsets = scv.offsets();
        if (offsets.type().id() != cudf::type_id::INT32) {
            g_last_error = "readParquetStrings: the offsets child is not INT32, which this binding's buffer layout assumes";
            return 8;
        }
        cudaError_t rc = copy_out(out_offsets, offsets.data<int32_t>(), static_cast<size_t>(rows + 1) * sizeof(int32_t), raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("readParquetStrings offsets copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        if (chars_bytes > 0) {
            rc = copy_out(out_chars, scv.chars_begin(view), static_cast<size_t>(chars_bytes), raw);
            if (rc != cudaSuccess) {
                g_last_error = std::string("readParquetStrings chars copy-out: ") + cudaGetErrorString(rc);
                return 3;
            }
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("readParquetStrings", e);
    } catch (...) {
        return fail("readParquetStrings");
    }
}

/**
 * Matches a regex against a strings column held in TornadoVM buffers, writing one byte a row.
 *
 * Nothing is owned here. A `column_view` is a non-owning descriptor, so the strings column is
 * rebuilt over the caller's offsets and chars for the duration of the call and the match reads
 * the same device memory the reader wrote -- no copy between the read and the match.
 *
 * The compiled program is cached by pattern. `regex_program::create` parses and compiles, which is
 * host work proportional to the pattern and not to the data, so doing it per batch would show up
 * as a per-file cost that has nothing to do with the query.
 */
int tornado_cudf_contains_re(void* stream, int64_t rows, const void* offsets, const void* chars, int64_t chars_bytes, const char* pattern, void* out_mask) {
    ensure_pool();
    try {
        if (offsets == nullptr || chars == nullptr || pattern == nullptr || out_mask == nullptr) {
            g_last_error = "containsRe: null buffer or pattern";
            return 5;
        }
        static std::map<std::string, std::unique_ptr<cudf::strings::regex_program>> programs;
        const std::string key(pattern);
        auto found = programs.find(key);
        if (found == programs.end()) {
            found = programs.emplace(key, cudf::strings::regex_program::create(key)).first;
        }

        auto view = rmm::cuda_stream_view{static_cast<cudaStream_t>(stream)};
        cudf::column_view offsets_view(cudf::data_type{cudf::type_id::INT32}, static_cast<cudf::size_type>(rows + 1), offsets, nullptr, 0);
        cudf::column_view strings_view(cudf::data_type{cudf::type_id::STRING}, static_cast<cudf::size_type>(rows), chars, nullptr, 0, 0, {offsets_view});
        cudf::strings_column_view scv(strings_view);

        auto mask = cudf::strings::contains_re(scv, *found->second, view);
        if (mask->type().id() != cudf::type_id::BOOL8) {
            g_last_error = "containsRe: contains_re did not return BOOL8";
            return 8;
        }
        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        cudaError_t rc = copy_out(out_mask, mask->view().data<int8_t>(), static_cast<size_t>(rows), raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("containsRe copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("containsRe", e);
    } catch (...) {
        return fail("containsRe");
    }
}

}  // extern "C"
