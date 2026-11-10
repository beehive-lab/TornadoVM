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

#include "tornado-ck-gemm.hpp"

namespace tornado_ck {

int gemmBF16(hipStream_t stream, int m, int n, int k, float alpha, const void *a, const void *b, float beta, void *c, size_t *workspaceQuery, void *workspace) {
    return gemm<BF16>(stream, m, n, k, alpha, a, b, beta, c, workspaceQuery, workspace);
}

} // namespace tornado_ck
