/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2022 APT Group, Department of Computer Science,
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class PTXModule {
    public final byte[] moduleWrapper;
    public final String kernelFunctionName;
    private int maxBlockSize;
    public final String javaName;
    private final byte[] source;

    private static final Pattern PTX_ENTRY_PAT = Pattern.compile("\\.entry\\s+(\\w+)\\s*\\(");

    public PTXModule(String name, byte[] source, String kernelFunctionName, int[] jitOptions, long[] jitValues, String archOption) {
        byte[] loadSource = source;
        String resolvedKernelName = kernelFunctionName;
        if ("cudac".equals(TornadoOptions.PTX_CODEGEN)) {
            // Prebuilt kernels are stored as raw PTX (starting with ".version").
            // These must be loaded directly via cuModuleLoadDataEx, not compiled by NVRTC.
            String srcStr = new String(source, java.nio.charset.StandardCharsets.UTF_8).stripLeading();
            if (!srcStr.startsWith(".version")) {
                loadSource = nvrtcCompile(source, kernelFunctionName, archOption);
            } else {
                // For prebuilt PTX the kernel name in the file may differ from the
                // name built by buildKernelName (which omits type suffixes in CUDA C
                // mode).  Extract the actual .entry name so cuModuleGetFunction finds
                // the function correctly.
                Matcher m = PTX_ENTRY_PAT.matcher(srcStr);
                if (m.find()) {
                    resolvedKernelName = m.group(1);
                }
            }
        }
        moduleWrapper = cuModuleLoadDataEx(loadSource, jitOptions, jitValues);
        this.source = source;
        this.kernelFunctionName = resolvedKernelName;
        maxBlockSize = -1;
        javaName = name;
    }

    private static native byte[] cuModuleLoadData(byte[] source);

    private static native byte[] cuModuleLoadDataEx(byte[] source, int[] jitOptions, long[] jitValues);

    private static native byte[] nvrtcCompile(byte[] source, String kernelName, String archOption);

    private static native long cuModuleUnload(byte[] module);

    private static native int cuOccupancyMaxPotentialBlockSize(byte[] module, String funcName);

    public int getPotentialBlockSizeMaxOccupancy() {
        if (maxBlockSize < 0) {
            maxBlockSize = cuOccupancyMaxPotentialBlockSize(moduleWrapper, kernelFunctionName);
        }
        return maxBlockSize;
    }

    public byte[] getSource() {
        return source;
    }

    public boolean isPTXJITSuccess() {
        return moduleWrapper.length != 0;
    }

    public void unload() {
        cuModuleUnload(moduleWrapper);
    }
}
