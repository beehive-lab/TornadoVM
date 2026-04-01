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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.common.logging;

import uk.ac.manchester.tornado.drivers.common.utils.Colour;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * Class for logging internals of the JIT Compiler, code generation, LIR Builder
 * and TornadoVM runtime.
 */
public class Logger {

    /**
     * Method to track the code generation.
     *
     * @param backend
     *     Backend selection
     * @param message
     *     String message with code gen trace
     * @param args
     *     Arguments to the string message
     */
    public static void traceCodeGen(final BACKEND backend, final String message, final Object... args) {
        if (TornadoOptions.TRACE_CODE_GEN) {
            System.out.printf(Colour.CYAN + "[" + backend.backendName() + "-CodeGen] " + message + Colour.RESET + "\n", args);
        }
    }

    /**
     * Method to track SPIR-V IR Builder (from last IR phase to IR Builder for
     * codegen).
     *
     * @param backend
     *     Backend selection
     * @param message
     *     String message with the IR Builder
     * @param args
     *     Arguments to the string message
     */
    public static void traceBuildLIR(final BACKEND backend, String message, final Object... args) {
        if (TornadoOptions.TRACE_BUILD_LIR) {
            System.out.printf(Colour.GREEN + "[" + backend.backendName() + "-BuildLIR] " + message + Colour.RESET + "\n", args);
        }
    }

    /**
     * Method to track internal calls in the TornadoVM Runtime for running the
     * SPIR-V code.
     *
     * @param backend
     *     Backend selection
     * @param message
     *     String track message
     * @param args
     *     Arguments to the string.
     */
    public static void traceRuntime(final BACKEND backend, String message, final Object... args) {
        System.out.printf(Colour.YELLOW + "[" + backend.backendName() + "-Runtime] " + message + Colour.RESET + "\n", args);
    }

    public enum BACKEND {

        OpenCL("OpenCL"), //
        PTX("PTX"), //
        SPIRV("SPIRV"), //
        Metal("Metal"); //

        String backendName;

        BACKEND(String name) {
            this.backendName = name;
        }

        public String backendName() {
            return this.backendName;
        }
    }
}
