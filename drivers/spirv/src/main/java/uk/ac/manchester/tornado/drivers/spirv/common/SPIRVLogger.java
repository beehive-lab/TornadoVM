/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.spirv.common;

import uk.ac.manchester.tornado.drivers.common.Colour;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVLogger {

    /**
     * Method to track SPIR-V code generation
     * 
     * @param message
     *            String message with code gen trace
     * @param args
     *            Arguments to the string message
     */
    public static void traceCodeGen(final String message, final Object... args) {
        if (TornadoOptions.TRACE_CODE_GEN) {
            System.out.printf(Colour.CYAN + "[SPIRV-CodeGen] " + message + Colour.RESET + "\n", args);
        }
    }

    /**
     * Method to track SPIR-V IR Builder (from last IR phase to IR Builder for
     * codegen)
     *
     * @param message
     *            String message with the IR Builder
     * @param args
     *            Arguments to the string message
     */
    public static void traceBuildLIR(String message, final Object... args) {
        if (TornadoOptions.TRACE_BUILD_LIR) {
            System.out.printf(Colour.GREEN + "[SPIRV-BuildLIR] " + message + Colour.RESET + "\n", args);
        }
    }

    /**
     * Method to track internal calls in the TornadoVM Runtime for running the
     * SPIR-V code.
     * 
     * @param message
     *            String track message
     * @param args
     *            Arguments to the string.
     */
    public static void traceRuntime(String message, final Object... args) {
        System.out.printf(Colour.YELLOW + "[SPIRV-Runtime] " + message + Colour.RESET + "\n", args);
    }
}
