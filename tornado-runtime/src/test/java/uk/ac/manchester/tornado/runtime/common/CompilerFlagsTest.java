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
package uk.ac.manchester.tornado.runtime.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;

public class CompilerFlagsTest {

    @Test
    public void keepsBothWhenNothingRepeats() {
        assertEquals("--use_fast_math --extra-device-vectorization -lineinfo", CompilerFlags.merge("--use_fast_math --extra-device-vectorization", "-lineinfo"));
    }

    @Test
    public void doesNotRepeatAnOptionBothSet() {
        // NVRTC: "--use_fast_math (-use_fast_math) defined more than once".
        assertEquals("--extra-device-vectorization --use_fast_math", CompilerFlags.merge("--use_fast_math --extra-device-vectorization", "--use_fast_math"));
    }

    @Test
    public void overrideReplacesTheValue() {
        assertEquals("--use_fast_math --fmad=true", CompilerFlags.merge("--fmad=false --use_fast_math", "--fmad=true"));
    }

    @Test
    public void shortAliasReplacesLongForm() {
        assertEquals("-lineinfo", CompilerFlags.merge("--generate-line-info", "-lineinfo"));
        assertEquals("--gpu-architecture=sm_80", CompilerFlags.merge("-arch=sm_75", "--gpu-architecture=sm_80"));
    }

    @Test
    public void repeatableOptionsAreKept() {
        assertEquals("-I/a -DX=1 -I/b -DY=2 --include-path=/c", CompilerFlags.merge("-I/a -DX=1", "-I/b -DY=2 --include-path=/c"));
    }

    @Test
    public void separateValueStaysWithItsOption() {
        assertEquals("-I /a --fmad=true", CompilerFlags.merge("-I /a --fmad=false", "--fmad=true"));
    }

    @Test
    public void blankAndNull() {
        assertEquals("", CompilerFlags.merge(null, null));
        assertEquals("--use_fast_math", CompilerFlags.merge("", "  --use_fast_math "));
        assertEquals("--use_fast_math", CompilerFlags.merge("--use_fast_math", null));
    }

    private static final String OPENCL_DEFAULTS = "-cl-mad-enable -cl-fast-relaxed-math -w";

    @Test
    public void openCLFlagsAreAddedToTheDefaults() {
        assertEquals(OPENCL_DEFAULTS + " -cl-opt-disable", CompilerFlags.forPlan(TornadoVMBackendType.OPENCL, OPENCL_DEFAULTS, "-cl-opt-disable", false));
        assertEquals(OPENCL_DEFAULTS + " -DTILE=16", CompilerFlags.forPlan(TornadoVMBackendType.OPENCL, OPENCL_DEFAULTS, "-DTILE=16", false));
    }

    @Test
    public void openCLOptionWithAValueIsReplaced() {
        assertEquals("-cl-mad-enable -w -cl-std=CL3.0", CompilerFlags.forPlan(TornadoVMBackendType.OPENCL, "-cl-mad-enable -cl-std=CL2.0 -w", "-cl-std=CL3.0", false));
    }

    @Test
    public void openCLRepeatedSwitchIsNotDuplicated() {
        assertEquals("-cl-mad-enable -w -cl-fast-relaxed-math", CompilerFlags.forPlan(TornadoVMBackendType.OPENCL, OPENCL_DEFAULTS, "-cl-fast-relaxed-math", false));
    }

    @Test
    public void noFlagsGivesTheDefaults() {
        assertEquals(OPENCL_DEFAULTS, CompilerFlags.forPlan(TornadoVMBackendType.OPENCL, OPENCL_DEFAULTS, null, false));
        assertEquals("--use_fast_math", CompilerFlags.forPlan(TornadoVMBackendType.CUDA, "--use_fast_math", "", false));
        assertEquals("", CompilerFlags.forPlan(TornadoVMBackendType.METAL, "", null, false));
    }

    @Test
    public void strictCudaPlanKeepsFmadOff() {
        assertEquals("--fmad=false --use_fast_math", CompilerFlags.forPlan(TornadoVMBackendType.CUDA, "", "--use_fast_math", true));
        // An explicit --fmad still wins, as it does in withStrictFloatingPoint().
        assertEquals("--fmad=true", CompilerFlags.forPlan(TornadoVMBackendType.CUDA, "", "--fmad=true", true));
        // Strict adds nothing to OpenCL here; its build options are not where OpenCL fusion is decided.
        assertEquals(OPENCL_DEFAULTS, CompilerFlags.forPlan(TornadoVMBackendType.OPENCL, OPENCL_DEFAULTS, null, true));
    }
}
