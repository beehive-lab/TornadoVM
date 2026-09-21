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
#include <cudf/groupby.hpp>
#include <cudf/filling.hpp>
#include <cudf/join/join.hpp>
#include <cudf/reduction.hpp>
#include <cudf/sorting.hpp>
#include <cudf/stream_compaction.hpp>
#include <cudf/table/table.hpp>
#include <cudf/table/table_view.hpp>
#include <cudf/types.hpp>
#include <cudf/unary.hpp>

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

}  // extern "C"
