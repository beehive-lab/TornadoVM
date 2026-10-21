/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class CudaCompilerFlagsTest {

    @Test
    public void keepsBothWhenNothingRepeats() {
        assertEquals("--use_fast_math --extra-device-vectorization -lineinfo", CudaCompilerFlags.merge("--use_fast_math --extra-device-vectorization", "-lineinfo"));
    }

    @Test
    public void doesNotRepeatAnOptionBothSet() {
        // NVRTC: "--use_fast_math (-use_fast_math) defined more than once".
        assertEquals("--extra-device-vectorization --use_fast_math", CudaCompilerFlags.merge("--use_fast_math --extra-device-vectorization", "--use_fast_math"));
    }

    @Test
    public void overrideReplacesTheValue() {
        assertEquals("--use_fast_math --fmad=true", CudaCompilerFlags.merge("--fmad=false --use_fast_math", "--fmad=true"));
    }

    @Test
    public void shortAliasReplacesLongForm() {
        assertEquals("-lineinfo", CudaCompilerFlags.merge("--generate-line-info", "-lineinfo"));
        assertEquals("--gpu-architecture=sm_80", CudaCompilerFlags.merge("-arch=sm_75", "--gpu-architecture=sm_80"));
    }

    @Test
    public void repeatableOptionsAreKept() {
        assertEquals("-I/a -DX=1 -I/b -DY=2 --include-path=/c", CudaCompilerFlags.merge("-I/a -DX=1", "-I/b -DY=2 --include-path=/c"));
    }

    @Test
    public void separateValueStaysWithItsOption() {
        assertEquals("-I /a --fmad=true", CudaCompilerFlags.merge("-I /a --fmad=false", "--fmad=true"));
    }

    @Test
    public void blankAndNull() {
        assertEquals("", CudaCompilerFlags.merge(null, null));
        assertEquals("--use_fast_math", CudaCompilerFlags.merge("", "  --use_fast_math "));
        assertEquals("--use_fast_math", CudaCompilerFlags.merge("--use_fast_math", null));
    }
}
