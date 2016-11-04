package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.CONST;
import com.oracle.graal.lir.LIRInstruction.Use;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AbstractInstruction;

public class OCLControlFlow {

    public static final class ReturnOp extends AbstractInstruction implements BlockEndOp {

        public static final LIRInstructionClass<ReturnOp> TYPE = LIRInstructionClass
                .create(ReturnOp.class);
        @Use
        protected Value x;

        public ReturnOp(Value x) {
            super(TYPE);
            this.x = x;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            crb.frameContext.leave(crb);
            crb.getAssembler().ret();
        }
    }

    public static final class EndScopeOp extends AbstractInstruction implements BlockEndOp {

        public static final LIRInstructionClass<EndScopeOp> TYPE = LIRInstructionClass
                .create(EndScopeOp.class);

        public EndScopeOp() {
            super(TYPE);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            crb.getAssembler().endScope();
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
        public void emit(OCLCompilationResultBuilder crb) {
            crb.getAssembler().beginScope();
        }
    }

    public static class LoopInitOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopInitOp> TYPE = LIRInstructionClass
                .create(LoopInitOp.class);

        public LoopInitOp() {
            super(TYPE);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.emitSymbol(OCLAssemblerConstants.FOR_LOOP);
            asm.emitSymbol(OCLAssemblerConstants.BRACKET_OPEN);

            asm.indentOff();
            asm.eolOff();
            asm.setDelimiter(OCLAssemblerConstants.EXPR_DELIMITER);
        }
    }

    public static class LoopPostOp extends AbstractInstruction {

        public static final LIRInstructionClass<LoopPostOp> TYPE = LIRInstructionClass
                .create(LoopPostOp.class);

        public LoopPostOp() {
            super(TYPE);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            if (asm.getByte(asm.position() - 2) == ',') {
                asm.emitString(" ", asm.position() - 2);
            }

            asm.emitSymbol(OCLAssemblerConstants.BRACKET_CLOSE);

            asm.setDelimiter(OCLAssemblerConstants.STMT_DELIMITER);
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
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            if (asm.getByte(asm.position() - 1) == ',') {
                asm.emitString(" ", asm.position() - 1);
            }

            asm.setDelimiter(OCLAssemblerConstants.STMT_DELIMITER);
            asm.delimiter();

            asm.value(crb, condition);

            asm.delimiter();
            asm.setDelimiter(OCLAssemblerConstants.EXPR_DELIMITER);
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
        public void emit(OCLCompilationResultBuilder crb) {
            crb.getAssembler().ifStmt(crb, condition);
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
        public void emit(OCLCompilationResultBuilder crb) {
            crb.getAssembler().elseIfStmt(crb, condition);
        }

    }

    public static class ElseBranchOp extends AbstractInstruction {

        public static final LIRInstructionClass<ElseBranchOp> TYPE = LIRInstructionClass
                .create(ElseBranchOp.class);

        public ElseBranchOp() {
            super(TYPE);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
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
        private final JavaConstant[] keyConstants;

        @Use
        private final LabelRef[] keyTargets;
        private final LabelRef defaultTarget;

        public SwitchOp(Variable value, JavaConstant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget) {
            super(TYPE);
            this.value = value;
            this.keyConstants = keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.emitSymbol(OCLAssemblerConstants.SWITCH);
            asm.emitSymbol(OCLAssemblerConstants.BRACKET_OPEN);
            asm.value(crb, value);
            asm.emitSymbol(OCLAssemblerConstants.BRACKET_CLOSE);
        }

        public JavaConstant[] getKeyConstants() {
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
        private final JavaConstant value;

        public CaseOp(JavaConstant keyConstants) {
            super(TYPE);
            this.value = keyConstants;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.emitSymbol(OCLAssemblerConstants.CASE);
            asm.space();
            asm.value(crb, value);
            asm.emitSymbol(OCLAssemblerConstants.COLON);
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
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.emitSymbol(OCLAssemblerConstants.DEFAULT_CASE);
            asm.emitSymbol(OCLAssemblerConstants.COLON);
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
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.emitSymbol(OCLAssemblerConstants.BREAK);
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
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
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
        public void emit(OCLCompilationResultBuilder crb) {
            final OCLAssembler asm = crb.getAssembler();
            asm.indent();
            asm.emit("slots[0] = (ulong) ");
            asm.value(crb, actionAndReason);
            asm.delimiter();

            asm.eol();
            asm.ret();
        }

    }

}
