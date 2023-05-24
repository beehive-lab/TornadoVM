/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graph.TornadoExecutionContext;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraph;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodeBuilder;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMGraphCompiler;
import uk.ac.manchester.tornado.runtime.interpreter.TornadoVMInterpreter;
import uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph;

import static uk.ac.manchester.tornado.runtime.common.Tornado.VM_USE_DEPS;

/**
 * TornadoVM: it includes a bytecode interpreter (Tornado bytecodes), a memory
 * manager for all devices (FPGAs, GPUs and multicore that follows the OpenCL
 * programming model), and a JIT compiler from Java bytecode to OpenCL.
 * <p>
 * The JIT compiler extends the Graal JIT Compiler for OpenCL compilation.
 * <p>
 * There is an instance of the {@link TornadoVM} per {@link TornadoTaskGraph}.
 * Each TornadoVM contains the logic to orchestrate the execution on the
 * parallel device (e.g., a GPU).
 */


public class TornadoVM extends TornadoLogger {
    private final boolean Parallel_Ints = false;
    private final boolean useDependencies;

    private final TornadoExecutionContext graphContext;


    private double totalTime;
    private long invocations;
    private final TornadoProfiler timeProfiler;


    private final TornadoVMBytecodeBuilder[] tornadoVMBytecodes;

    private final TornadoVMInterpreter[] tornadoVMInterpreters;
//    private final InterpreterManager interpreterManager;

    public TornadoVM(TornadoExecutionContext graphContext, TornadoGraph tornadoGraph, TornadoProfiler timeProfiler, long batchSize) {
        tornadoVMBytecodes = TornadoVMGraphCompiler.compile(tornadoGraph, graphContext, batchSize);
        tornadoVMInterpreters = new TornadoVMInterpreter[graphContext.getDevices().size()]; // context per device == size means total interpretes
        this.graphContext = graphContext;
        this.timeProfiler = timeProfiler;
        useDependencies = graphContext.meta().enableOooExecution() || VM_USE_DEPS;
        totalTime = 0;
        invocations = 0;
        bindBytecodesToInterpreters();
    }

    private void bindBytecodesToInterpreters() {
        int index = 0;
        for (TornadoAcceleratorDevice distinctContext : graphContext.getDevices()) {
            tornadoVMInterpreters[index] = new TornadoVMInterpreter(graphContext, tornadoVMBytecodes[index], timeProfiler, distinctContext);
            index++;
        }
    }
    

    public Event execute() {
        return tornadoVMInterpreters[0].execute(false);
    }

    private void scheduleInterpreters() {
        if (true) {
            for (TornadoVMInterpreter interpreter : tornadoVMInterpreters) {
                interpreter.execute(false);
            }
        }
    }

    private void instantiateInterpreter() {

    }
    //InterpreterManager->

//    private static class InterpreterManager {
//        int totalInterpreters;
//        TornadoVMInterpreter[] tornadoVMInterpreters;
//        TornadoVMBytecodeBuilder tornadoVMBytecode;
//        boolean isParallel;
//
//        public int getTotalInterpreters() {
//            return totalInterpreters;
//        }
//
//        public InterpreterManager(TornadoVMInterpreter[] tornadoVMInterpreters, TornadoVMBytecodeBuilder tornadoVMBytecode) {
//            this.tornadoVMInterpreters = tornadoVMInterpreters;
//            this.tornadoVMBytecode = tornadoVMBytecode;
//        }
//
//    }
}
