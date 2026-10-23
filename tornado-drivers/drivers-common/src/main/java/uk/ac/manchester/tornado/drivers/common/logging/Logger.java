/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
     * Method to track the IR Builder (from last IR phase to IR Builder for
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
     * generated code.
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
