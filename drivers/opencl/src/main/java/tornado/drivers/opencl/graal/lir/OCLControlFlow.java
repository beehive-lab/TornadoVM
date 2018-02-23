/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.lir;

import org.graalvm.compiler.lir.LIRInstruction.Use;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.Variable;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AbstractInstruction;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static tornado.drivers.opencl.graal.asm.OCLAssemblerConstants.*;

public class OCLControlFlow {

    protected static abstract class AbstractBlockEndOp extends AbstractInstruction implements BlockEndOp {

        public AbstractBlockEndOp(LIRInstructionClass<? extends AbstractInstruction> type) {
            super(type);
        }
    }

    public static final class ReturnOp extends AbstractBlockEndOp {

        public static final LIRInstructionClass<ReturnOp> TYPE = LIRInstructionClass
                .create(ReturnOp.class);
        @Use
        protected Value x;

        public ReturnOp(Value x) {
            super(TYPE);
            this.x = x;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            crb.frameContext.leave(crb);
            asm.ret();
        }
    }

    public static final class EndScopeOp extends AbstractBlockEndOp {

        public static final LIRInstructionClass<EndScopeOp> TYPE = LIRInstructionClass
                .create(EndScopeOp.class);

        public EndScopeOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.endScope();
        }
    }

    /**
     * LIR operation that defines the position of a label.
     */
    public static final class BeginScopeOp extends AbstractInstruction {

        public static final LIRInstructionClass<BeginScopeOp> TYPE = LIRInstructionClass
                .create(BeginScopeOp.class);

        public BeginScopeOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.beginScope();
        }
    }

    public static class LoopInitOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopInitOp> TYPE = LIRInstructionClass
                .create(LoopInitOp.class);

        public LoopInitOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emitSymbol(FOR_LOOP);
            asm.emitSymbol(BRACKET_OPEN);

            asm.indentOff();
            asm.eolOff();
            asm.setDelimiter(EXPR_DELIMITER);
        }
    }

    public static class LoopPostOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopPostOp> TYPE = LIRInstructionClass
                .create(LoopPostOp.class);

        public LoopPostOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            if (asm.getByte(asm.position() - 2) == ',') {
                asm.emitString(" ", asm.position() - 2);
            }

            asm.emitSymbol(BRACKET_CLOSE);

            asm.setDelimiter(STMT_DELIMITER);
            asm.indentOn();
            asm.eolOn();
        }
    }

    public static class LoopConditionOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopConditionOp> TYPE = LIRInstructionClass
                .create(LoopConditionOp.class);
        @Use
        private final Value condition;

        public LoopConditionOp(Value condition) {
            super(TYPE);
            this.condition = condition;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            if (asm.getByte(asm.position() - 1) == ',') {
                asm.emitString(" ", asm.position() - 1);
            }

            asm.setDelimiter(STMT_DELIMITER);
            asm.delimiter();

            if (condition instanceof OCLLIROp) {
                ((OCLLIROp) condition).emit(crb, asm);
            } else {
                asm.emitValue(crb, condition);
            }

            if (((OCLKind) condition.getPlatformKind()) == OCLKind.INT) {
                asm.emit(" == 1");
            }
            asm.delimiter();
            asm.setDelimiter(EXPR_DELIMITER);
        }
    }

    public static class ConditionalBranchOp extends AbstractInstruction {

        public static final LIRInstructionClass<ConditionalBranchOp> TYPE = LIRInstructionClass
                .create(ConditionalBranchOp.class);
        @Use
        private final Value condition;

        public ConditionalBranchOp(Value condition) {
            super(TYPE);
            this.condition = condition;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.ifStmt(crb, condition);
        }

    }

    public static class LinkedConditionalBranchOp extends AbstractInstruction {

        public static final LIRInstructionClass<LinkedConditionalBranchOp> TYPE = LIRInstructionClass
                .create(LinkedConditionalBranchOp.class);
        @Use
        private final Value condition;

        public LinkedConditionalBranchOp(Value condition) {
            super(TYPE);
            this.condition = condition;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.elseIfStmt(crb, condition);
        }

    }

    public static class ElseBranchOp extends AbstractInstruction {

        public static final LIRInstructionClass<ElseBranchOp> TYPE = LIRInstructionClass
                .create(ElseBranchOp.class);

        public ElseBranchOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.elseStmt();
            asm.beginScope();
        }

    }

    public static class SwitchOp extends AbstractInstruction {

        public static final LIRInstructionClass<SwitchOp> TYPE = LIRInstructionClass
                .create(SwitchOp.class);

        @Use
        private final Variable value;

        @Use({CONST})
        private final Constant[] keyConstants;

        @Use
        private final LabelRef[] keyTargets;
        private final LabelRef defaultTarget;

        public SwitchOp(Variable value, Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget) {
            super(TYPE);
            this.value = value;
            this.keyConstants = keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emitSymbol(SWITCH);
            asm.emitSymbol(BRACKET_OPEN);
            asm.emitValue(crb, value);
            asm.emitSymbol(BRACKET_CLOSE);
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

    public static class CaseOp extends AbstractInstruction {

        public static final LIRInstructionClass<CaseOp> TYPE = LIRInstructionClass
                .create(CaseOp.class);

        @Use
        private final Constant value;

        public CaseOp(Constant value) {
            super(TYPE);
            this.value = value;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
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

        public static final LIRInstructionClass<DefaultCaseOp> TYPE = LIRInstructionClass
                .create(DefaultCaseOp.class);

        public DefaultCaseOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emitSymbol(DEFAULT_CASE);
            asm.emitSymbol(COLON);
            asm.eol();
            asm.pushIndent();
        }
    }

    public static class CaseBreakOp extends AbstractInstruction {

        public static final LIRInstructionClass<CaseBreakOp> TYPE = LIRInstructionClass
                .create(CaseBreakOp.class);

        public CaseBreakOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emitSymbol(BREAK);
            asm.delimiter();
            asm.eol();
            asm.popIndent();
        }
    }

    public static class LoopBreakOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopBreakOp> TYPE = LIRInstructionClass
                .create(LoopBreakOp.class);

        public LoopBreakOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.loopBreak();
            asm.delimiter();
            asm.eol();
        }

    }

    public static class DeoptOp extends AbstractInstruction {

        public static final LIRInstructionClass<DeoptOp> TYPE = LIRInstructionClass
                .create(DeoptOp.class);
        @Use
        private final Value actionAndReason;

        public DeoptOp(Value actionAndReason) {
            super(TYPE);
            this.actionAndReason = actionAndReason;
        }

        @Override
        public void emitCode(OCLCompilationResultBuilder crb, OCLAssembler asm) {
            asm.indent();
            asm.emit("slots[0] = (ulong) ");
            asm.emitValue(crb, actionAndReason);
            asm.delimiter();

            asm.eol();
            asm.ret();
        }

    }

}
