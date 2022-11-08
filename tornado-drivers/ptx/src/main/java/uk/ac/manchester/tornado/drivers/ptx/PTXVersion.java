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
 *
 */
package uk.ac.manchester.tornado.drivers.ptx;

public class PTXVersion {
    private enum PTX_VERSION_TO_ARCHITECTURE {
        PTX_76(new CUDAComputeCapability(7, 6), new TargetArchitecture(8, 6)), //
        PTX_70(new CUDAComputeCapability(7, 0), new TargetArchitecture(8, 6)), //
        PTX_63(new CUDAComputeCapability(6, 3), new TargetArchitecture(7, 5)), //
        PTX_61(new CUDAComputeCapability(6, 1), new TargetArchitecture(7, 2)), //
        PTX_60(new CUDAComputeCapability(6, 0), new TargetArchitecture(7, 0)), //
        PTX_50(new CUDAComputeCapability(5, 0), new TargetArchitecture(6, 2)); //

        private final CUDAComputeCapability ptxIsa;
        private final TargetArchitecture targetArchitecture;

        PTX_VERSION_TO_ARCHITECTURE(CUDAComputeCapability ptxIsa, TargetArchitecture targetArchitecture) {
            this.ptxIsa = ptxIsa;
            this.targetArchitecture = targetArchitecture;
        }
    }

    private final CUDAComputeCapability version;
    private TargetArchitecture maxArch;

    public PTXVersion(CUDAComputeCapability actual) {
        this.version = actual;
        for (PTX_VERSION_TO_ARCHITECTURE ptxToArchitecture : PTX_VERSION_TO_ARCHITECTURE.values()) {
            if (ptxToArchitecture.ptxIsa.compareTo(version) <= 0) {
                this.maxArch = ptxToArchitecture.targetArchitecture;
                break;
            }
        }
    }

    @Override
    public String toString() {
        return String.format("%d.%d", version.getMajor(), version.getMinor());
    }

    public TargetArchitecture getArchitecture(CUDAComputeCapability deviceCapability) {
        CUDAComputeCapability computeCapability = maxArch.compareTo(deviceCapability) > 0 ? deviceCapability : maxArch;
        return new TargetArchitecture(computeCapability);
    }
}
