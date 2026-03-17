/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal.asm;

import java.nio.charset.StandardCharsets;

import jdk.vm.ci.code.TargetDescription;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerationResult;

/**
 * Assembler for CUDA C code generation mode. Lets the parent {@link PTXAssembler}
 * emit PTX normally into the code buffer, then at {@link #close(boolean)} time
 * translates the accumulated PTX text to CUDA C via {@link PTXToCUDACTranslator}.
 *
 * <p>The resulting CUDA C bytes are passed to NVRTC in {@link uk.ac.manchester.tornado.drivers.ptx.PTXModule}
 * for runtime compilation back to PTX/cubin before module loading.
 */
public class CUDACAssembler extends PTXAssembler {

    public CUDACAssembler(TargetDescription target, PTXLIRGenerationResult lirGenRes) {
        super(target, lirGenRes);
    }

    /**
     * Overrides the parent's close to perform PTX → CUDA C translation.
     *
     * @param flush passed to the parent to finalise the code buffer
     * @return CUDA C source bytes (UTF-8) for the compiled function
     */
    @Override
    public byte[] close(boolean flush) {
        byte[] ptxBytes = super.close(flush);
        if (ptxBytes == null || ptxBytes.length == 0) {
            return ptxBytes;
        }
        String ptx = new String(ptxBytes, StandardCharsets.UTF_8);
        String cuda = PTXToCUDACTranslator.translate(ptx);
        return cuda.getBytes(StandardCharsets.UTF_8);
    }
}
