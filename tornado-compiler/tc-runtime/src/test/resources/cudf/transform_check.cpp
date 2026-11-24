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
 * Runs one TC device function through the real cudf::transform_extended (CUDA source, JIT), for
 * CudfTransformIT. It knows nothing about the function: the test writes the input columns and reads
 * the output back, and compares it with the Java method itself.
 *
 *   transform_check <spec>
 *
 * The spec is one directive per line:
 *
 *   udf <file>                           the CUDA source tc emit --device-fn printed
 *   rows <n>
 *   null_aware 0|1
 *   output <TYPE> <data file> <validity file>
 *   input <TYPE> <data file> [<validity file>]       fixed width
 *   input STRING <offsets file> <chars file> [<validity file>]
 *   repeat <n>                           runs of the transform, the fastest reported (default 3)
 *
 * TYPE is a cudf::type_id name (INT8, INT16, INT32, INT64, UINT16, FLOAT32, FLOAT64, BOOL8). Data
 * files are raw little-endian arrays; a validity file has one byte per row, 1 for valid. The output
 * validity file gets one byte per row too. Prints one line:
 *
 *   ok <milliseconds of the fastest run> <null count>
 *   error <what>                         cudf::evaluation_error: the UDF failed a row
 */
#include <cudf/column/column.hpp>
#include <cudf/column/column_factories.hpp>
#include <cudf/column/column_view.hpp>
#include <cudf/null_mask.hpp>
#include <cudf/transform.hpp>
#include <cudf/types.hpp>
#include <cudf/utilities/error.hpp>

#include <rmm/device_buffer.hpp>

#include <cuda_runtime.h>

#include <chrono>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

namespace {

std::vector<char> slurp(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        throw std::runtime_error("cannot read " + path);
    }
    return std::vector<char>(std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>());
}

void spill(const std::string& path, const void* data, size_t bytes) {
    std::ofstream out(path, std::ios::binary);
    out.write(static_cast<const char*>(data), static_cast<std::streamsize>(bytes));
}

cudf::type_id type_of(const std::string& name) {
    static const std::map<std::string, cudf::type_id> types = {{"INT8", cudf::type_id::INT8},       {"INT16", cudf::type_id::INT16},
                                                               {"INT32", cudf::type_id::INT32},     {"INT64", cudf::type_id::INT64},
                                                               {"UINT16", cudf::type_id::UINT16},   {"FLOAT32", cudf::type_id::FLOAT32},
                                                               {"FLOAT64", cudf::type_id::FLOAT64}, {"BOOL8", cudf::type_id::BOOL8},
                                                               {"STRING", cudf::type_id::STRING}};
    auto found = types.find(name);
    if (found == types.end()) {
        throw std::runtime_error("unknown type " + name);
    }
    return found->second;
}

rmm::device_buffer device(const std::vector<char>& host) {
    rmm::device_buffer buffer(host.size(), cudf::get_default_stream());
    cudaMemcpy(buffer.data(), host.data(), host.size(), cudaMemcpyHostToDevice);
    return buffer;
}

/** A null mask from one byte per row, and its null count; no mask when the file is not given. */
std::pair<rmm::device_buffer, cudf::size_type> mask(const std::string& path, cudf::size_type rows) {
    if (path.empty()) {
        return {rmm::device_buffer{}, 0};
    }
    auto bytes = slurp(path);
    std::vector<cudf::bitmask_type> words(cudf::num_bitmask_words(rows), 0);
    cudf::size_type nulls = 0;
    for (cudf::size_type i = 0; i < rows; i++) {
        if (bytes.at(i) != 0) {
            words[i / 32] |= cudf::bitmask_type{1} << (i % 32);
        } else {
            nulls++;
        }
    }
    rmm::device_buffer buffer(cudf::bitmask_allocation_size_bytes(rows), cudf::get_default_stream());
    cudaMemset(buffer.data(), 0, buffer.size());
    cudaMemcpy(buffer.data(), words.data(), words.size() * sizeof(cudf::bitmask_type), cudaMemcpyHostToDevice);
    return {std::move(buffer), nulls};
}

}  // namespace

int main(int argc, char** argv) {
    if (argc != 2) {
        std::fprintf(stderr, "usage: transform_check <spec>\n");
        return 2;
    }
    try {
        std::string udf;
        cudf::size_type rows = 0;
        bool null_aware = false;
        int repeat = 3;
        cudf::type_id output_type = cudf::type_id::EMPTY;
        std::string output_data, output_valid;
        std::vector<std::unique_ptr<cudf::column>> columns;

        std::istringstream spec(std::string(slurp(argv[1]).data(), slurp(argv[1]).size()));
        for (std::string line; std::getline(spec, line);) {
            std::istringstream words(line);
            std::string directive;
            words >> directive;
            if (directive == "udf") {
                std::string path;
                words >> path;
                auto source = slurp(path);
                udf.assign(source.begin(), source.end());
            } else if (directive == "rows") {
                words >> rows;
            } else if (directive == "null_aware") {
                int flag = 0;
                words >> flag;
                null_aware = flag != 0;
            } else if (directive == "repeat") {
                words >> repeat;
            } else if (directive == "output") {
                std::string type;
                words >> type >> output_data >> output_valid;
                output_type = type_of(type);
            } else if (directive == "input") {
                std::string type, first, second, third;
                words >> type >> first >> second >> third;
                if (type_of(type) == cudf::type_id::STRING) {
                    auto offsets = slurp(first);
                    auto off = cudf::make_numeric_column(cudf::data_type{cudf::type_id::INT32}, rows + 1);
                    cudaMemcpy(off->mutable_view().data<int32_t>(), offsets.data(), offsets.size(), cudaMemcpyHostToDevice);
                    auto [null_mask, nulls] = mask(third, rows);
                    columns.push_back(cudf::make_strings_column(rows, std::move(off), device(slurp(second)), nulls, std::move(null_mask)));
                } else {
                    auto [null_mask, nulls] = mask(second, rows);
                    columns.push_back(std::make_unique<cudf::column>(cudf::data_type{type_of(type)}, rows, device(slurp(first)), std::move(null_mask), nulls));
                }
            } else if (!directive.empty()) {
                throw std::runtime_error("unknown directive " + directive);
            }
        }

        std::vector<cudf::transform_input> inputs;
        for (auto& c : columns) {
            inputs.emplace_back(c->view());
        }
        std::unique_ptr<cudf::column> result;
        double best = 1e300;
        for (int run = 0; run < repeat; run++) {
            cudaDeviceSynchronize();
            auto start = std::chrono::steady_clock::now();
            result = cudf::transform_extended(inputs, udf, cudf::data_type{output_type}, cudf::udf_source_type::CUDA, std::nullopt,
                                              null_aware ? cudf::null_aware::YES : cudf::null_aware::NO);
            cudaDeviceSynchronize();
            double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
            // The first run includes the JIT compile (or the kernel cache's load).
            if (run > 0 || repeat == 1) {
                best = std::min(best, ms);
            }
        }

        auto view = result->view();
        std::vector<char> data(static_cast<size_t>(rows) * cudf::size_of(view.type()));
        cudaMemcpy(data.data(), view.head<char>(), data.size(), cudaMemcpyDeviceToHost);
        spill(output_data, data.data(), data.size());
        std::vector<char> valid(rows, 1);
        if (view.nullable()) {
            std::vector<cudf::bitmask_type> words(cudf::num_bitmask_words(rows));
            cudaMemcpy(words.data(), view.null_mask(), words.size() * sizeof(cudf::bitmask_type), cudaMemcpyDeviceToHost);
            for (cudf::size_type i = 0; i < rows; i++) {
                valid[i] = (words[i / 32] >> (i % 32)) & 1;
            }
        }
        spill(output_valid, valid.data(), valid.size());
        std::printf("ok %.3f %d\n", best, static_cast<int>(view.null_count()));
        return 0;
    } catch (const cudf::evaluation_error& e) {
        std::printf("error %s\n", e.what());
        return 0;
    } catch (const std::exception& e) {
        std::fprintf(stderr, "transform_check: %s\n", e.what());
        return 1;
    }
}
