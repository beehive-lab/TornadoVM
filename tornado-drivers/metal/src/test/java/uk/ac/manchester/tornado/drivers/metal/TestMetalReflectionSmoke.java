/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.drivers.metal.MetalInstalledCode;

public class TestMetalReflectionSmoke {

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac");
    }

    private static boolean hasXcrun() {
        try {
            Process p = new ProcessBuilder("xcrun", "--version").start();
            int rc = p.waitFor();
            return rc == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    public void smokeReflection() throws Exception {
        assumeTrue(isMac(), "macOS required for Metal tests");
        assumeTrue(hasXcrun(), "xcrun required for Metal toolchain");

            // trivial Metal kernel: takes 2 buffers and writes result to out[0]
            String msl = "using namespace metal; kernel void add(device const float* a [[buffer(0)]], device const float* b [[buffer(1)]], device float* out [[buffer(2)]]) { uint id = static_cast<uint>(get_thread_position_in_grid().x); out[id] = a[id] + b[id]; }";

        // Obtain platform/context/device context using public APIs
        MetalPlatform platform = (MetalPlatform) Metal.getPlatform(0);
        MetalContext context = platform.createContext();
        context.createCommandQueue(0);
        MetalDeviceContext ctx = context.createDeviceContext(0);

        MetalCodeCache cache = new MetalCodeCache(ctx);
        MetalInstalledCode installed = cache.compileAndLink("add", msl);
        assertTrue(installed != null, "installed code should not be null");
        MetalKernel k = installed.getKernel();
        assertTrue(k != null, "kernel must be present");

        int n = k.getArgCount();
        // Expect at least 3 buffer arguments (a,b,out)
        assertTrue(n >= 3, "expected at least 3 args, got " + n);
        MetalKernel.KernelArgInfo info0 = k.getArgInfoObject(0);
        assertTrue(info0 != null, "arg0 info should be non-null");
        // Name may be empty depending on reflection granularity; just ensure parsing succeeded
        assertTrue(info0.index == 0, "index must match");
    }
}
