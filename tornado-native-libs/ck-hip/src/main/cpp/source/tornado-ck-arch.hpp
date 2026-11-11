/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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

#pragma once

#include <hip/hip_runtime.h>

#include <mutex>
#include <string>
#include <unordered_map>

namespace tornado_ck {

/**
 * Whether the current device is one of the architectures this library was compiled for
 * (TORNADO_CK_COMPILED_TARGETS, a comma-separated list the build defines). The kernels are
 * selected by architecture family, so on a GPU outside the list one would be picked that has no
 * code object for it; callers report such a device as unsupported instead.
 */
inline bool currentDeviceIsCompiledTarget() {
    static std::mutex mutex;
    static std::unordered_map<int, bool> compiled;
    int device;
    if (hipGetDevice(&device) != hipSuccess) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mutex);
    auto known = compiled.find(device);
    if (known != compiled.end()) {
        return known->second;
    }
    bool found = false;
    hipDeviceProp_t properties;
    if (hipGetDeviceProperties(&properties, device) == hipSuccess) {
        // gcnArchName carries target features after the processor: "gfx942:sramecc+:xnack-"
        std::string arch(properties.gcnArchName);
        arch = arch.substr(0, arch.find(':'));
        std::string targets = "," TORNADO_CK_COMPILED_TARGETS ",";
        found = !arch.empty() && targets.find("," + arch + ",") != std::string::npos;
    }
    compiled.emplace(device, found);
    return found;
}

} // namespace tornado_ck
