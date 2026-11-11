/*
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
package uk.ac.manchester.tornado.tc.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps.Dialect;

/**
 * The HIP MMA helpers pick their matrix instructions with the preprocessor. Every architecture that
 * TargetCaps admits for MMA must be routed there, and any other must stop the compilation rather than
 * fall through to code that computes nothing.
 */
public class HipMmaRoutingTest {

    private static final List<String> ARCHITECTURES = List.of("gfx900", "gfx906", "gfx908", "gfx90a", "gfx940", "gfx941", "gfx942", "gfx950", "gfx1010", "gfx1030", "gfx1100",
            "gfx1101", "gfx1102", "gfx1103", "gfx1150", "gfx1151", "gfx1152", "gfx1153", "gfx1200", "gfx1201", "gfx1250");

    private static final List<String> HELPERS = List.of("tc_mma_m16n8k16", "tc_mma_m16n8k16_bf16", "tc_mma_m16n8k32_s8", "tc_mma_m16n8k32_e4m3", "tc_mma_m16n8k32_e5m2");

    @Test
    public void everyArchitectureWithMatrixCoresIsRouted() {
        for (String helper : HELPERS) {
            String source = Helpers.source(helper, Dialect.HIP);
            assertTrue(source.contains("#error"), helper + " has no #error for an architecture without a lowering");
            for (String arch : ARCHITECTURES) {
                TargetCaps target = TargetCaps.hip(arch, true, true, 16, 1024, 64 * 1024, arch.startsWith("gfx9") ? 64 : 32);
                String macro = switch (target.matrixCores()) {
                    case MFMA -> "defined(__" + arch + "__)";
                    case WMMA_GFX11 -> "defined(__GFX11__)";
                    case WMMA_GFX12, WMMA_GFX1250 -> "defined(__" + arch + "__)";
                    case NONE -> null;
                };
                if (macro != null) {
                    assertTrue(source.contains(macro), helper + " does not route " + arch + " (" + target.matrixCores() + ")");
                }
            }
        }
    }
}
