/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.enums;

public enum CUDABuildStatus {
    CL_BUILD_SUCCESS(0), //
    CL_BUILD_NONE(-1), //
    CL_BUILD_ERROR(-2), //
    CL_BUILD_IN_PROGRESS(-3), //
    CL_BUILD_UNKNOWN(-4);

    private final int buildStatusCode;

    CUDABuildStatus(final int v) {
        buildStatusCode = v;
    }

    public int getBuildStatusCode() {
        return buildStatusCode;
    }

    public static CUDABuildStatus toEnum(final int errorCode) {
        return switch (errorCode) {
            case 0 -> CUDABuildStatus.CL_BUILD_SUCCESS;
            case -1 -> CUDABuildStatus.CL_BUILD_NONE;
            case -2 -> CUDABuildStatus.CL_BUILD_ERROR;
            case -3 -> CUDABuildStatus.CL_BUILD_IN_PROGRESS;
            default -> null;
        };
    }

}
