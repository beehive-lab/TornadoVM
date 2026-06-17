/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.graal.compiler;

import static uk.ac.manchester.tornado.runtime.graal.TornadoLIRGenerator.trace;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstruction.OperandFlag;
import jdk.graal.compiler.lir.LIRInstruction.OperandMode;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.DataBuilder;
import jdk.graal.compiler.lir.asm.FrameContext;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalControlFlow;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalControlFlow.LoopConditionOp;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalControlFlow.LoopInitOp;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalControlFlow.LoopPostOp;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class MetalCompilationResultBuilder extends CompilationResultBuilder {

    private final Set<ResolvedJavaMethod> nonInlinedMethods;
    protected LIR lir;
    private int currentBlockIndex;
    private boolean isKernel;
    private int loops = 0;
    private boolean isParallel;
    private TaskDataContext metaData;
    private long[] localGrid;
    private MetalDeviceContextInterface deviceContext;

    public MetalCompilationResultBuilder(CoreProviders providers, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext, OptionValues options, DebugContext debug,
            CompilationResult compilationResult, LIR lir) {
        super(providers, frameMap, asm, dataBuilder, frameContext, options, debug, compilationResult, Register.None, NO_VERIFIERS, lir);
        nonInlinedMethods = new HashSet<ResolvedJavaMethod>();
    }

    private static boolean isMergeHIRBlock(HIRBlock block) {
        return block.getBeginNode() instanceof AbstractMergeNode;
    }

    /**
     * Checks if the {@link MetalNodeLIRBuilder(LoopBeginNode)} has been called right
     * before {@link MetalNodeLIRBuilder#emitIf}. In other words, that there is no
     * data flow/control flow between the {@link LoopBeginNode} and the
     * corresponding {@link IfNode} loop condition.
     *
     * @return true if the {@param loopCondIndex} is right after the LIR
     *     instructions of a loop header ({@param loopPostOpIndex} and
     *     {@param loopInitOpIndex}).
     */
    private static boolean isLoopConditionRightAfterHeader(int loopCondIndex, int loopPostOpIndex, int loopInitOpIndex) {
        return (loopCondIndex - 1 == loopPostOpIndex) && (loopCondIndex - 2 == loopInitOpIndex);
    }

    /**
     * Checks if there are any LIR instructions between the loop condition and the
     * {@link LoopInitOp} and {@link LoopPostOp}. If there are no instructions, it
     * is possible to move the loop condition to the loop header.
     *
     * @return true if there are no instructions.
     */
    private static boolean shouldFormatLoopHeader(List<LIRInstruction> instructions) {
        int loopInitOpIndex = -1, loopPostOpIndex = -1, loopConditionOpIndex = -1;

        for (int index = 0, instructionsSize = instructions.size(); index < instructionsSize; index++) {
            LIRInstruction instruction = instructions.get(index);
            if (instruction instanceof LoopInitOp) {
                loopInitOpIndex = index;
            }
            if (instruction instanceof LoopPostOp) {
                loopPostOpIndex = index;
            }
            if (instruction instanceof LoopConditionOp) {
                loopConditionOpIndex = index;
            }
        }

        return isLoopConditionRightAfterHeader(loopConditionOpIndex, loopPostOpIndex, loopInitOpIndex);
    }

    private static void formatLoopHeader(List<LIRInstruction> instructions) {
        int index = instructions.size() - 1;

        LIRInstruction condition = instructions.get(index);
        while (!(condition instanceof LoopConditionOp)) {
            index--;
            condition = instructions.get(index);
        }
        ((LoopConditionOp) condition).setGenerateIfBreakStatement(false);

        instructions.remove(index);

        final Set<Value> dependencies = new HashSet<>();
        DepFinder df = new DepFinder(dependencies);
        condition.forEachInput(df);

        index--;
        final List<LIRInstruction> moved = new ArrayList<>();
        LIRInstruction insn = instructions.get(index);
        while (!(insn instanceof LoopPostOp)) {
            if (insn instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) insn;
                if (assign.getResult() instanceof Variable) {
                    Variable var = (Variable) assign.getResult();
                    if (dependencies.contains(var)) {
                        moved.add(instructions.remove(index));
                    }
                }
            }
            index--;
            insn = instructions.get(index);
        }

        LIRInstruction loopInit = instructions.get(instructions.size() - 1);
        while (!(loopInit instanceof LoopInitOp)) {
            index--;
            loopInit = instructions.get(index);
        }

        instructions.add(index + 1, condition);
        instructions.addAll(index - 1, moved);
    }

    private static boolean isLoopDependencyNode(LIRInstruction op) {
        return ((op instanceof MetalControlFlow.LoopInitOp || op instanceof MetalControlFlow.LoopConditionOp || op instanceof MetalControlFlow.LoopPostOp));
    }

    private static void emitOp(CompilationResultBuilder crb, LIRInstruction op) {
        try {
            trace("op: " + op);
            op.emitCode(crb);
        } catch (AssertionError | RuntimeException t) {
            throw new TornadoInternalError(t);
        }
    }

    public boolean isParallel() {
        return isParallel;
    }

    public void setParallel(boolean parallel) {
        this.isParallel = parallel;
    }

    public MetalCompilationResult getResult() {
        return (MetalCompilationResult) compilationResult;
    }

    public boolean shouldRemoveLoop() {
        return false;
    }

    public boolean isKernel() {
        return isKernel;
    }

    public void setKernel(boolean value) {
        isKernel = value;
    }

    public MetalAssembler getAssembler() {
        return (MetalAssembler) asm;
    }

    public void addNonInlinedMethod(ResolvedJavaMethod method) {
        nonInlinedMethods.add(method);
    }

    Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder() code
     * emitting order}.
     */
    public void emit(LIR lir) {
        assert this.lir == null;
        assert currentBlockIndex == 0;
        this.lir = lir;
        this.currentBlockIndex = 0;
        frameContext.enter(this);

        final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();
        trace("Traversing CFG: ", cfg.graph.name);
        cfg.computePostdominators();
        new MetalStructuredControlFlow(this).emit(cfg);

        trace("Finished traversing CFG");
        this.lir = null;
        this.currentBlockIndex = 0;

    }

    @Override
    public void finish() {
        int position = asm.position();
        compilationResult.setTargetCode(asm.close(true), position);
    }

    void emitLoopBlock(HIRBlock block) {
        final List<LIRInstruction> headerInstructions = lir.getLIRforBlock(block);
        if (shouldFormatLoopHeader(headerInstructions)) {
            formatLoopHeader(headerInstructions);
        }
        emitBlock(block);
    }

    void emitRelocatedInstructions(HIRBlock block) {
        if (block == null) {
            return;
        }

        trace("block on exit %d", block.getId());

        boolean relocatableInstruction = false;
        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (op instanceof MetalLIRStmt.MarkRelocateInstruction) {
                relocatableInstruction = true;
            }

            if (op != null && relocatableInstruction) {
                try {
                    emitOp(this, op);
                } catch (TornadoInternalError e) {
                    throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n");
                }
            }
        }
    }

    void emitBlock(HIRBlock block) {
        if (block == null) {
            return;
        }

        trace("block: %d", block.getId());
        printBasicHIRBlockTrace(block);

        LIRInstruction breakInst = null;

        boolean relocatableInstruction = false;
        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (op instanceof MetalLIRStmt.MarkRelocateInstruction) {
                relocatableInstruction = true;
            }

            if (op == null || relocatableInstruction) {
                continue;
            } else if (op instanceof MetalControlFlow.LoopBreakOp) {
                breakInst = op;
                continue;
            } else if ((shouldRemoveLoop() && loops == 0) && isLoopDependencyNode(op)) {
                /**
                 * Apply the Loop Flattening optimization for FPGAs,
                 * which omits the outermost for loop along with every data dependency associated with it.
                 */
                if (op instanceof MetalControlFlow.LoopPostOp) {
                    loops++;
                }
                continue;
            }
            if (Options.PrintLIRWithAssembly.getValue(getOptions())) {
                blockComment(String.format("%d %s", op.id(), op));
            }

            try {
                emitOp(this, op);
            } catch (TornadoInternalError e) {
                throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n");
            }
        }

        /*
         * Because of the way Graal handles Phi nodes, we generate the break instruction
         * before any phi nodes are updated, therefore we need to ensure that the break
         * is emitted as the end of the block.
         */
        if (breakInst != null) {
            try {
                emitOp(this, breakInst);
            } catch (TornadoInternalError e) {
                throw e.addContext("lir instruction", block + "@" + breakInst.id() + " " + breakInst + "\n");
            }
        }

    }

    void printBasicHIRBlockTrace(HIRBlock block) {
        if (isMergeHIRBlock(block)) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < block.getPredecessorCount(); i++) {
                sb.append(block.getPredecessorAt(i).getId()).append(" ");
            }
            sb.append("]");
            ((MetalAssembler) asm).emitLine("// BLOCK %d MERGES %s", block.getId(), sb.toString());
        } else {
            ((MetalAssembler) asm).emitLine("// BLOCK %d", block.getId());
        }

        if (Options.PrintLIRWithAssembly.getValue(getOptions())) {
            blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }
    }

    public TaskDataContext getTaskMetaData() {
        return ((MetalCompilationResult) compilationResult).getMeta();
    }

    public MetalDeviceContextInterface getDeviceContext() {
        return this.deviceContext;
    }

    public void setDeviceContext(MetalDeviceContextInterface deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Deprecated
    private static class DepFinder implements InstructionValueProcedure {

        private final Set<Value> dependencies;

        DepFinder(final Set<Value> dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (value instanceof Variable) {
                dependencies.add(value);
            }

            return value;
        }

        public Set<Value> getDependencies() {
            return dependencies;
        }

    }
}
