/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.BREAK;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.CASE;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.CLOSE_PARENTHESIS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.COLON;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.DEFAULT_CASE;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.FOR_LOOP;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.IF_STMT;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.NOT;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.OPEN_PARENTHESIS;
import static uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants.SWITCH;

import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AbstractInstruction;

/**
 * Metal Code Generation for all control-flow constructs.
 */
public class MetalControlFlow {

    protected abstract static class AbstractBlockEndOp extends AbstractInstruction implements BlockEndOp {

        protected AbstractBlockEndOp(LIRInstructionClass<? extends AbstractInstruction> type) {
            super(type);
        }
    }

    public static final class ReturnOp extends AbstractBlockEndOp {

        public static final LIRInstructionClass<ReturnOp> TYPE = LIRInstructionClass.create(ReturnOp.class);
        @Use
        private Value x;

        public ReturnOp(Value x) {
            super(TYPE);
            this.x = x;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            crb.frameContext.leave(crb);
            asm.ret();
        }
    }

    public static final class EndScopeOp extends AbstractBlockEndOp {

        public static final LIRInstructionClass<EndScopeOp> TYPE = LIRInstructionClass.create(EndScopeOp.class);

        public EndScopeOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.endScope("End");
        }
    }

    /**
     * LIR operation that defines the position of a label.
     */
    public static final class BeginScopeOp extends AbstractInstruction {

        public static final LIRInstructionClass<BeginScopeOp> TYPE = LIRInstructionClass.create(BeginScopeOp.class);

        public BeginScopeOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.beginScope();
        }
    }

    public static class LoopInitOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopInitOp> TYPE = LIRInstructionClass.create(LoopInitOp.class);

        public LoopInitOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitSymbol(FOR_LOOP);
            asm.emitSymbol(OPEN_PARENTHESIS);
            asm.delimiter();
            asm.indentOff();
            asm.eolOff();
        }
    }

    public static class LoopPostOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopPostOp> TYPE = LIRInstructionClass.create(LoopPostOp.class);

        public LoopPostOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {

            asm.delimiter();
            asm.emitSymbol(CLOSE_PARENTHESIS);

            asm.indentOn();
            asm.eolOn();

            asm.eol();
            asm.beginScope();
        }
    }

    /**
     * This instruction can generate different code depending on whether or not
     * there are additional {@link org.graalvm.compiler.lir.LIRInstruction}s between
     * the loop condition and the {@link LoopPostOp}, respectively the
     * {@link LoopInitOp}.
     */
    public static class LoopConditionOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopConditionOp> TYPE = LIRInstructionClass.create(LoopConditionOp.class);
        @Use
        private final Value condition;

        private boolean generateIfBreakStatement = true;

        public LoopConditionOp(Value condition) {
            super(TYPE);
            this.condition = condition;
        }

        public void setGenerateIfBreakStatement(boolean value) {
            this.generateIfBreakStatement = value;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            if (generateIfBreakStatement) {
                asm.indent();

                asm.emitSymbol(IF_STMT);
                asm.emitSymbol(OPEN_PARENTHESIS);
                asm.emitSymbol(NOT);
                asm.emitSymbol(OPEN_PARENTHESIS);
            }

            if (condition instanceof MetalLIROp) {
                ((MetalLIROp) condition).emit(crb, asm);
            } else {
                asm.emitValue(crb, condition);
            }

            if (condition.getPlatformKind() == MetalKind.INT) {
                asm.emit(" == 1");
            }

            if (generateIfBreakStatement) {
                asm.emitSymbol(CLOSE_PARENTHESIS);
                asm.emitSymbol(CLOSE_PARENTHESIS);
                asm.eol();

                asm.beginScope();
                asm.indent();
                asm.emitSymbol(BREAK);
                asm.delimiter();
                asm.eol();
                asm.endScope();
            }
        }
    }

    public static class ConditionalBranchOp extends AbstractInstruction {

        public static final LIRInstructionClass<ConditionalBranchOp> TYPE = LIRInstructionClass.create(ConditionalBranchOp.class);
        @Use
        private final Value condition;

        public ConditionalBranchOp(Value condition) {
            super(TYPE);
            this.condition = condition;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.ifStmt(crb, condition);
        }

    }

    public static class LinkedConditionalBranchOp extends AbstractInstruction {

        public static final LIRInstructionClass<LinkedConditionalBranchOp> TYPE = LIRInstructionClass.create(LinkedConditionalBranchOp.class);
        @Use
        private final Value condition;

        public LinkedConditionalBranchOp(Value condition) {
            super(TYPE);
            this.condition = condition;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.elseIfStmt(crb, condition);
        }

    }

    public static class ElseBranchOp extends AbstractInstruction {

        public static final LIRInstructionClass<ElseBranchOp> TYPE = LIRInstructionClass.create(ElseBranchOp.class);

        public ElseBranchOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.elseStmt();
            asm.beginScope();
        }

    }

    public static class SwitchOp extends AbstractInstruction {

        public static final LIRInstructionClass<SwitchOp> TYPE = LIRInstructionClass.create(SwitchOp.class);

        @Use
        private final AllocatableValue value;

        @Use({ CONST })
        private final Constant[] keyConstants;

        @Use
        private final LabelRef[] keyTargets;
        private final LabelRef defaultTarget;

        public SwitchOp(AllocatableValue value, Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget) {
            super(TYPE);
            this.value = value;
            this.keyConstants = keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitSymbol(SWITCH);
            asm.emitSymbol(OPEN_PARENTHESIS);
            asm.emitValue(crb, value);
            asm.emitSymbol(CLOSE_PARENTHESIS);
            asm.emit(" ");
            asm.beginScope();
        }

        public Constant[] getKeyConstants() {
            return keyConstants;
        }

        public LabelRef[] getKeyTargets() {
            return keyTargets;
        }

        public LabelRef getDefaultTarget() {
            return defaultTarget;
        }
    }

    // FIXME: Remove this unused code
    public static class CaseOp extends AbstractInstruction {

        public static final LIRInstructionClass<CaseOp> TYPE = LIRInstructionClass.create(CaseOp.class);

        @Use
        private final Constant value;

        public CaseOp(Constant value) {
            super(TYPE);
            this.value = value;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitSymbol(CASE);
            asm.space();
            asm.emitConstant(value);
            asm.emitSymbol(COLON);
            asm.eol();
            asm.pushIndent();
        }
    }

    public static class DefaultCaseOp extends AbstractInstruction {

        public static final LIRInstructionClass<DefaultCaseOp> TYPE = LIRInstructionClass.create(DefaultCaseOp.class);

        public DefaultCaseOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitSymbol(DEFAULT_CASE);
            asm.emitSymbol(COLON);
            asm.eol();
            asm.pushIndent();
        }
    }

    public static class CaseBreakOp extends AbstractInstruction {

        public static final LIRInstructionClass<CaseBreakOp> TYPE = LIRInstructionClass.create(CaseBreakOp.class);

        public CaseBreakOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emitSymbol(BREAK);
            asm.delimiter();
            asm.eol();
            asm.popIndent();
        }
    }

    public static class LoopBreakOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopBreakOp> TYPE = LIRInstructionClass.create(LoopBreakOp.class);

        public LoopBreakOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.loopBreak();
            asm.delimiter();
            asm.eol();
        }

    }

    public static class DeoptOp extends AbstractInstruction {

        public static final LIRInstructionClass<DeoptOp> TYPE = LIRInstructionClass.create(DeoptOp.class);
        @Use
        private final Value actionAndReason;

        public DeoptOp(Value actionAndReason) {
            super(TYPE);
            this.actionAndReason = actionAndReason;
        }

        @Override
        public void emitCode(MetalCompilationResultBuilder crb, MetalAssembler asm) {
            asm.indent();
            asm.emit("slots[0] = (ulong) ");
            asm.emitValue(crb, actionAndReason);
            asm.delimiter();

            asm.eol();
            asm.ret();
        }

    }

}
