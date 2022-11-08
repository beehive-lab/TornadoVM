/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;

public class CUDAVersion {
    private static final CUDAVersion[] cudaVersions = new CUDAVersion[] {
            // 8.0 is the first version to have PTX ISA documentation available therefore
            // this is the oldest supported
            new CUDAVersion(8000, new CUDAComputeCapability(5, 0)), //
            new CUDAVersion(9000, new CUDAComputeCapability(6, 0)), //
            new CUDAVersion(9010, new CUDAComputeCapability(6, 1)), //
            new CUDAVersion(9020, new CUDAComputeCapability(6, 2)), //
            new CUDAVersion(10000, new CUDAComputeCapability(6, 3)), //
            new CUDAVersion(10010, new CUDAComputeCapability(6, 4)), //
            new CUDAVersion(10020, new CUDAComputeCapability(6, 5)), //
            new CUDAVersion(11000, new CUDAComputeCapability(7, 0)), //
            new CUDAVersion(11060, new CUDAComputeCapability(7, 6)), //
            new CUDAVersion(11070, new CUDAComputeCapability(7, 6)), //
    };

    private final int sdkVersion;
    private final PTXVersion maxPTXVersion;

    private CUDAVersion(int cudaVersion, CUDAComputeCapability ptxVersion) {
        this.sdkVersion = cudaVersion;
        this.maxPTXVersion = new PTXVersion(ptxVersion);
    }

    private static int extractMajorVersion(int cudaVersion) {
        return cudaVersion / 1000;
    }

    private static int extractMinorVersion(int cudaVersion) {
        return (cudaVersion % 1000) / 10;
    }

    public static PTXVersion getMaxPTXVersion(int cudaVersion) {
        for (int i = cudaVersions.length - 1; i >= 0; i--) {
            if (cudaVersion >= cudaVersions[i].sdkVersion) {
                return cudaVersions[i].maxPTXVersion;
            }
        }
        TornadoInternalError.shouldNotReachHere(String.format("Unsupported CUDA toolkit version: %d.%d. Please consider upgrading to version %d.%d or higher.", extractMajorVersion(cudaVersion),
                extractMinorVersion(cudaVersion), extractMajorVersion(cudaVersions[0].sdkVersion), extractMinorVersion(cudaVersions[0].sdkVersion)));
        return null;
    }
}
