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
#include <cudf/search.hpp>
#include <cudf/sorting.hpp>
#include <cudf/stream_compaction.hpp>
#include <cudf/table/table.hpp>
#include <cudf/table/table_view.hpp>
#include <cudf/types.hpp>
#include <cudf/strings/contains.hpp>
#include <cudf/strings/regex/regex_program.hpp>
#include <cudf/strings/strings_column_view.hpp>
#include <cudf/unary.hpp>
#include <cudf/transform.hpp>

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

/**
 * Reads columns of several physical types from a Parquet file, with their validity if asked.
 *
 * tornado_cudf_read_parquet takes one INT32 key and FP64 values, all non-null, which is a group-by's
 * shape and not a table's. A lakehouse table keys rows on 64-bit ids, stores dates and timestamps as
 * 32- and 64-bit integers, and declares most columns optional. This entry point reads any mix of
 * those into three typed buffers, one a physical type, each packed with a stride of `stride` --
 * the k-th requested column of a kind starts at element k * stride of that kind's buffer.
 *
 * The stride is the caller's capacity rather than this read's row count, so that one set of
 * buffers sized for the largest file serves every file: `rows` is what this read must produce and
 * may be anything up to `stride`.
 *
 * Kinds, which cross the ABI and so are fixed: 0 is a 32-bit integer (INT32, or TIMESTAMP_DAYS,
 * which is how Parquet DATE arrives), 1 is a 64-bit integer (INT64, or any TIMESTAMP with a 64-bit
 * representation), 2 is FP64. A column of another type is refused rather than cast.
 *
 * With `nullable` set, out_valid receives one byte a row a column -- 1 for a value, 0 for a null --
 * at stride `stride` in request order; the payload under a null is whatever the decoder left there.
 * With it clear, a null is refused, which is the contract the other entry points have.
 */
int tornado_cudf_read_parquet_columns(void* stream, const char* path, int32_t row_group_start, int32_t row_group_count, int32_t column_count,
        const int32_t* columns, const int32_t* kinds, int64_t rows, int64_t stride, void* out_int32, void* out_int64, void* out_fp64, void* out_valid,
        int32_t nullable) {
    ensure_pool();
    try {
        // Nothing to read is a read of nothing: a plan whose graph always holds a read can then
        // skip it for the executions that have no file for it, which a graph cannot otherwise do.
        if (rows == 0 && (path == nullptr || path[0] == '\0')) {
            return 0;
        }
        if (path == nullptr || columns == nullptr || kinds == nullptr || column_count <= 0) {
            g_last_error = "readParquetColumns: null path or empty column list";
            return 5;
        }
        if (rows < 0 || rows > stride) {
            g_last_error = "readParquetColumns: " + std::to_string(rows) + " rows do not fit a stride of " + std::to_string(stride);
            return 5;
        }

        // Several files, one a line, are read as one table: their rows concatenated in order.
        // One call over many small files is what keeps the device busy -- a read of one 1M-row
        // file is latency, not bandwidth -- and their schemas have to agree, which the column
        // names resolved from the first file then check.
        std::vector<std::string> paths;
        {
            std::string all(path);
            size_t begin = 0;
            while (begin <= all.size()) {
                size_t end = all.find('\n', begin);
                if (end == std::string::npos) {
                    end = all.size();
                }
                if (end > begin) {
                    paths.push_back(all.substr(begin, end - begin));
                }
                begin = end + 1;
            }
        }
        if (paths.empty()) {
            g_last_error = "readParquetColumns: no path";
            return 5;
        }
        if (paths.size() > 1 && row_group_count > 0) {
            g_last_error = "readParquetColumns: a row-group range applies to one file, not to " + std::to_string(paths.size());
            return 5;
        }
        auto metadata = cudf::io::read_parquet_metadata(cudf::io::source_info{paths.front()});
        auto source = cudf::io::source_info{paths};
        const auto& root = metadata.schema().root();
        const int32_t schema_columns = static_cast<int32_t>(root.num_children());

        std::vector<std::string> names;
        for (int32_t c = 0; c < column_count; c++) {
            if (columns[c] < 0 || columns[c] >= schema_columns) {
                g_last_error = "readParquetColumns: column " + std::to_string(columns[c]) + " is beyond the file's " + std::to_string(schema_columns) + " columns";
                return 6;
            }
            if (kinds[c] < 0 || kinds[c] > 2) {
                g_last_error = "readParquetColumns: kind " + std::to_string(kinds[c]) + " is not 0 (INT32), 1 (INT64) or 2 (FP64)";
                return 5;
            }
            names.push_back(root.child(columns[c]).name());
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
            g_last_error = "readParquetColumns: file gave " + std::to_string(table.num_rows()) + " rows where the caller asked for " + std::to_string(rows);
            return 7;
        }

        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        int64_t slot[3] = {0, 0, 0};
        void* base[3] = {out_int32, out_int64, out_fp64};
        const size_t width[3] = {sizeof(int32_t), sizeof(int64_t), sizeof(double)};
        for (int32_t c = 0; c < column_count; c++) {
            const auto col = table.column(c);
            const auto id = col.type().id();
            const int32_t kind = kinds[c];
            bool accepted = false;
            switch (kind) {
                case 0:
                    accepted = id == cudf::type_id::INT32 || id == cudf::type_id::TIMESTAMP_DAYS;
                    break;
                case 1:
                    accepted = id == cudf::type_id::INT64 || id == cudf::type_id::TIMESTAMP_SECONDS || id == cudf::type_id::TIMESTAMP_MILLISECONDS
                            || id == cudf::type_id::TIMESTAMP_MICROSECONDS || id == cudf::type_id::TIMESTAMP_NANOSECONDS;
                    break;
                default:
                    accepted = id == cudf::type_id::FLOAT64;
                    break;
            }
            if (!accepted) {
                g_last_error = "readParquetColumns: column " + std::to_string(columns[c]) + " (" + names[c] + ") has cuDF type id " + std::to_string(static_cast<int32_t>(id))
                        + ", which is not kind " + std::to_string(kind) + "; this reader does not cast";
                return 8;
            }
            if (col.null_count() != 0 && nullable == 0) {
                g_last_error = "readParquetColumns: column " + std::to_string(columns[c]) + " (" + names[c] + ") contains nulls and validity was not requested";
                return 9;
            }
            if (base[kind] == nullptr) {
                g_last_error = "readParquetColumns: a column of kind " + std::to_string(kind) + " was requested with no buffer for that kind";
                return 5;
            }
            if (rows > 0) {
                char* dst = static_cast<char*>(base[kind]) + static_cast<size_t>(slot[kind]) * static_cast<size_t>(stride) * width[kind];
                cudaError_t rc = copy_out(dst, col.head<char>() + static_cast<size_t>(col.offset()) * width[kind], static_cast<size_t>(rows) * width[kind], raw);
                if (rc != cudaSuccess) {
                    g_last_error = std::string("readParquetColumns copy-out: ") + cudaGetErrorString(rc);
                    return 3;
                }
            }
            slot[kind]++;

            if (nullable != 0 && rows > 0) {
                if (out_valid == nullptr) {
                    g_last_error = "readParquetColumns: validity was requested with no buffer for it";
                    return 5;
                }
                char* valid = static_cast<char*>(out_valid) + static_cast<size_t>(c) * static_cast<size_t>(stride);
                cudaError_t rc;
                if (col.null_count() == 0) {
                    rc = cudaMemsetAsync(valid, 1, static_cast<size_t>(rows), raw);
                } else {
                    // is_valid turns the packed null mask into one BOOL8 a row, which is what a
                    // generated kernel can read without bit arithmetic on someone else's layout.
                    auto flags = cudf::is_valid(col, view);
                    rc = copy_out(valid, flags->view().data<int8_t>(), static_cast<size_t>(rows), raw);
                }
                if (rc != cudaSuccess) {
                    g_last_error = std::string("readParquetColumns validity copy-out: ") + cudaGetErrorString(rc);
                    return 3;
                }
            }
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("readParquetColumns", e);
    } catch (...) {
        return fail("readParquetColumns");
    }
}

/**
 * Set membership: for each of n keys, whether it occurs among m set entries, as one BOOL8 a row.
 *
 * The half of an anti-join or semi-join that produces a mask rather than index pairs. An
 * equality delete is exactly this -- a row is deleted when its key is in the delete set -- and a
 * mask is what the rest of a filter combines with, where inner_join's pairs would have to be
 * scattered back into one first. cuDF builds a hash table over the set and probes it with the keys.
 *
 * kind 0 is INT32 keys, 1 is INT64. m may be 0, which answers false for every row without a probe.
 */
int tornado_cudf_contains(void* stream, int32_t kind, const void* set, int32_t m, const void* keys, int32_t n, void* out_mask) {
    ensure_pool();
    try {
        if (keys == nullptr || out_mask == nullptr || (m > 0 && set == nullptr)) {
            g_last_error = "containedIn: null buffer";
            return 5;
        }
        if (kind != 0 && kind != 1) {
            g_last_error = "containedIn: kind " + std::to_string(kind) + " is not 0 (INT32) or 1 (INT64)";
            return 5;
        }
        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        if (n <= 0) {
            return 0;
        }
        if (m <= 0) {
            cudaError_t rc = cudaMemsetAsync(out_mask, 0, static_cast<size_t>(n), raw);
            if (rc != cudaSuccess) {
                g_last_error = std::string("containedIn memset: ") + cudaGetErrorString(rc);
                return 3;
            }
            return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
        }
        auto view = rmm::cuda_stream_view{raw};
        const auto id = kind == 0 ? cudf::type_id::INT32 : cudf::type_id::INT64;
        cudf::column_view haystack(cudf::data_type{id}, m, set, nullptr, 0);
        cudf::column_view needles(cudf::data_type{id}, n, keys, nullptr, 0);
        auto found = cudf::contains(haystack, needles, view);
        if (found->type().id() != cudf::type_id::BOOL8) {
            g_last_error = "containedIn: contains did not return BOOL8";
            return 8;
        }
        cudaError_t rc = copy_out(out_mask, found->view().data<int8_t>(), static_cast<size_t>(n), raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("containedIn copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("containedIn", e);
    } catch (...) {
        return fail("containedIn");
    }
}

/**
 * Writes columns held in TornadoVM's typed device buffers to a Parquet file, from the device.
 *
 * The inverse of tornado_cudf_read_parquet_columns, with the same buffer layout: the k-th column of
 * a physical kind is at element k * stride of that kind's buffer, and validity bytes (1 = value) are
 * at c * stride for the c-th column. Nothing is copied to the host first; libcudf encodes and
 * compresses on the device and writes the file.
 *
 * types, one a column, fix both the buffer and the Parquet type, and cross the ABI:
 *   0 INT32, 1 INT64, 2 FP64, 3 DATE (days, from the INT32 buffer), 4 TIMESTAMP in microseconds
 *   (from the INT64 buffer).
 * field_ids, one a column, are written as Parquet field ids (-1 for none): a table format that
 * resolves columns by id -- Iceberg -- reads the file back by them. optional, one a column, makes
 * the column nullable, with its validity bytes as the null mask; a required column is written as
 * required and its validity bytes are not read.
 *
 * The row count is rows, or, when rows is negative, the INT32 at device_rows: the count a compaction
 * on the device (tornado_cudf_selected_indices) produced, which the host does not know when the
 * plan runs. compression: 0 none, 1 Snappy, 2 ZSTD. row_group_rows <= 0 keeps libcudf's default.
 * Column names are one a line in names. Footer statistics are written per row group.
 */
int tornado_cudf_write_parquet_columns(void* stream, const char* path, int32_t column_count, const char* names, const int32_t* field_ids,
        const int32_t* types, const int32_t* optional, int64_t rows, const void* device_rows, int64_t stride, const void* in_int32, const void* in_int64,
        const void* in_fp64, const void* in_valid, int32_t compression, int32_t row_group_rows) {
    ensure_pool();
    try {
        if (path == nullptr || names == nullptr || field_ids == nullptr || types == nullptr || optional == nullptr || column_count <= 0) {
            g_last_error = "writeParquetColumns: null argument or no columns";
            return 5;
        }
        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        auto view = rmm::cuda_stream_view{raw};
        if (rows < 0) {
            if (device_rows == nullptr) {
                g_last_error = "writeParquetColumns: no row count";
                return 5;
            }
            int32_t count = 0;
            cudaError_t rc = cudaMemcpyAsync(&count, device_rows, sizeof(int32_t), cudaMemcpyDeviceToHost, raw);
            if (rc != cudaSuccess || cudaStreamSynchronize(raw) != cudaSuccess) {
                g_last_error = "writeParquetColumns: cannot read the row count from the device";
                return 3;
            }
            rows = count;
        }
        if (rows > stride) {
            g_last_error = "writeParquetColumns: " + std::to_string(rows) + " rows do not fit a stride of " + std::to_string(stride);
            return 5;
        }

        std::vector<std::string> column_names;
        {
            std::string all(names);
            size_t begin = 0;
            while (begin <= all.size()) {
                size_t end = all.find('\n', begin);
                if (end == std::string::npos) {
                    end = all.size();
                }
                column_names.push_back(all.substr(begin, end - begin));
                begin = end + 1;
            }
        }
        if (static_cast<int32_t>(column_names.size()) != column_count) {
            g_last_error = "writeParquetColumns: " + std::to_string(column_names.size()) + " names for " + std::to_string(column_count) + " columns";
            return 5;
        }

        int64_t slot[3] = {0, 0, 0};
        const char* base[3] = {static_cast<const char*>(in_int32), static_cast<const char*>(in_int64), static_cast<const char*>(in_fp64)};
        const size_t width[3] = {sizeof(int32_t), sizeof(int64_t), sizeof(double)};
        std::vector<cudf::column_view> views;
        std::vector<std::unique_ptr<rmm::device_buffer>> masks;  // owned here until the write returns
        for (int32_t c = 0; c < column_count; c++) {
            int32_t kind;
            cudf::type_id id;
            switch (types[c]) {
                case 0: kind = 0; id = cudf::type_id::INT32; break;
                case 1: kind = 1; id = cudf::type_id::INT64; break;
                case 2: kind = 2; id = cudf::type_id::FLOAT64; break;
                case 3: kind = 0; id = cudf::type_id::TIMESTAMP_DAYS; break;
                case 4: kind = 1; id = cudf::type_id::TIMESTAMP_MICROSECONDS; break;
                default:
                    g_last_error = "writeParquetColumns: unknown type " + std::to_string(types[c]);
                    return 5;
            }
            if (base[kind] == nullptr) {
                g_last_error = "writeParquetColumns: a column needs a buffer that was not given";
                return 5;
            }
            const void* data = base[kind] + static_cast<size_t>(slot[kind]) * static_cast<size_t>(stride) * width[kind];
            slot[kind]++;
            const cudf::bitmask_type* mask = nullptr;
            cudf::size_type nulls = 0;
            if (optional[c] != 0 && rows > 0) {
                if (in_valid == nullptr) {
                    g_last_error = "writeParquetColumns: an optional column with no validity buffer";
                    return 5;
                }
                cudf::column_view flags(cudf::data_type{cudf::type_id::BOOL8}, static_cast<cudf::size_type>(rows),
                        static_cast<const char*>(in_valid) + static_cast<size_t>(c) * static_cast<size_t>(stride), nullptr, 0);
                auto [buffer, null_count] = cudf::bools_to_mask(flags, view);
                nulls = null_count;
                mask = static_cast<const cudf::bitmask_type*>(buffer->data());
                masks.push_back(std::move(buffer));
            }
            views.emplace_back(cudf::data_type{id}, static_cast<cudf::size_type>(rows), data, mask, nulls);
        }

        cudf::table_view table{views};
        cudf::io::table_input_metadata metadata(table);
        for (int32_t c = 0; c < column_count; c++) {
            metadata.column_metadata[c].set_name(column_names[c]).set_nullability(optional[c] != 0);
            if (field_ids[c] >= 0) {
                metadata.column_metadata[c].set_parquet_field_id(field_ids[c]);
            }
        }
        auto builder = cudf::io::parquet_writer_options::builder(cudf::io::sink_info{std::string(path)}, table)
                .metadata(std::move(metadata))
                .stats_level(cudf::io::statistics_freq::STATISTICS_ROWGROUP)
                .compression(compression == 2 ? cudf::io::compression_type::ZSTD
                        : compression == 1 ? cudf::io::compression_type::SNAPPY : cudf::io::compression_type::NONE);
        auto options = builder.build();
        if (row_group_rows > 0) {
            options.set_row_group_size_rows(row_group_rows);
        }
        cudf::io::write_parquet(options, view);
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("writeParquetColumns", e);
    } catch (...) {
        return fail("writeParquetColumns");
    }
}

/**
 * Sorts n keys ascending into a buffer the caller owns: the values, not a permutation.
 *
 * What a sorted probe needs: a set sorted once can be searched by every row of every batch that
 * follows, where a hash probe (tornado_cudf_contains) builds its table again on each call. kind 0
 * is INT32, 1 is INT64. n <= 0 sorts nothing and succeeds, so a plan can keep the sort in its graph
 * for the executions whose set has not changed.
 */
int tornado_cudf_sort_keys(void* stream, int32_t kind, const void* keys, int32_t n, void* out) {
    ensure_pool();
    try {
        if (n <= 0) {
            return 0;
        }
        if (keys == nullptr || out == nullptr || (kind != 0 && kind != 1)) {
            g_last_error = "sortKeys: null buffer or a kind other than 0 (INT32) or 1 (INT64)";
            return 5;
        }
        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        auto view = rmm::cuda_stream_view{raw};
        const auto id = kind == 0 ? cudf::type_id::INT32 : cudf::type_id::INT64;
        const size_t width = kind == 0 ? sizeof(int32_t) : sizeof(int64_t);
        cudf::column_view column(cudf::data_type{id}, n, keys, nullptr, 0);
        auto sorted = cudf::sort(cudf::table_view{{column}}, {cudf::order::ASCENDING}, {cudf::null_order::AFTER}, view);
        cudaError_t rc = copy_out(out, sorted->view().column(0).head<char>(), static_cast<size_t>(n) * width, raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("sortKeys copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("sortKeys", e);
    } catch (...) {
        return fail("sortKeys");
    }
}

/**
 * The stable ascending order of n rows keyed by key_columns INT64 columns, compared
 * lexicographically; column c starts at element c * stride of keys. Writes n INT32 row indices to
 * out_order. n <= 0 is a no-op.
 */
int tornado_cudf_sorted_order_longs(void* stream, const void* keys, int32_t n, int32_t key_columns, int64_t stride, void* out_order) {
    ensure_pool();
    try {
        if (n <= 0) {
            return 0;
        }
        if (keys == nullptr || out_order == nullptr || key_columns < 1 || key_columns > 64 || stride < n) {
            g_last_error = "sortedOrderLongs: null buffer, 1 to 64 key columns, or a stride below the row count";
            return 5;
        }
        cudaStream_t raw = static_cast<cudaStream_t>(stream);
        auto view = rmm::cuda_stream_view{raw};
        std::vector<cudf::column_view> columns;
        std::vector<cudf::order> orders(key_columns, cudf::order::ASCENDING);
        std::vector<cudf::null_order> nulls(key_columns, cudf::null_order::AFTER);
        const int64_t* base = static_cast<const int64_t*>(keys);
        for (int32_t c = 0; c < key_columns; c++) {
            columns.emplace_back(cudf::data_type{cudf::type_id::INT64}, n, base + c * stride, nullptr, 0);
        }
        auto order = cudf::stable_sorted_order(cudf::table_view{columns}, orders, nulls, view);
        cudaError_t rc = copy_out(out_order, order->view().data<int32_t>(), static_cast<size_t>(n) * sizeof(int32_t), raw);
        if (rc != cudaSuccess) {
            g_last_error = std::string("sortedOrderLongs copy-out: ") + cudaGetErrorString(rc);
            return 3;
        }
        return cudaStreamSynchronize(raw) == cudaSuccess ? 0 : 4;
    } catch (const std::exception& e) {
        return fail("sortedOrderLongs", e);
    } catch (...) {
        return fail("sortedOrderLongs");
    }
}

}  // extern "C"
