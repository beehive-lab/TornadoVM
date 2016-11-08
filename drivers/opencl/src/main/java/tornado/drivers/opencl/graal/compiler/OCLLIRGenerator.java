package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.spi.CodeGenProviders;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGenerator;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.*;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.ExprStmt;
import tornado.drivers.opencl.graal.lir.*;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;

public class OCLLIRGenerator extends LIRGenerator {

    protected OCLBuiltinTool oclBuiltinTool;
    protected OCLGenTool oclGenTool;

    public OCLLIRGenerator(CodeGenProviders providers, LIRGenerationResult res) {
        super(new OCLLIRKindTool(), new OCLArithmeticTool(), new OCLMoveFactory(), providers, res);
        this.oclBuiltinTool = new OCLBuiltinTool();
        this.oclGenTool = new OCLGenTool(this);
    }

    public OCLGenTool getOCLGenTool() {
        return oclGenTool;
    }

    @Override
    public SaveRegistersOp createZapRegisters(Register[] rgstrs, JavaConstant[] jcs) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitAddress(AllocatableValue allocatableValue) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitArrayEquals(JavaKind jk, Value value, Value value1, Value value2) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitByteSwap(Value value) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitCompareAndSwap(Value value, Value value1, Value value2, Value value3, Value value4) {
        unimplemented();
        return null;
    }

    @Override
    public void emitCompareBranch(PlatformKind pk, Value value, Value value1, Condition cndtn, boolean bln, LabelRef lr, LabelRef lr1, double d) {
        unimplemented();
    }

    public static OCLBinaryOp getConditionalOp(Condition condition) {
        switch (condition) {
            case AE:
            case GE:
                return OCLBinaryOp.RELATIONAL_GTE;
            case AT:
            case GT:
                return OCLBinaryOp.RELATIONAL_GT;

            case EQ:
                return OCLBinaryOp.RELATIONAL_EQ;

            case BE:
            case LE:
                return OCLBinaryOp.RELATIONAL_LTE;

            case BT:
            case LT:
                return OCLBinaryOp.RELATIONAL_LT;
            case NE:
                return OCLBinaryOp.RELATIONAL_NE;
            default:
                shouldNotReachHere();
                break;

        }
        return null;
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        trace("emitConditionalMove?");

        final OCLBinaryOp condOp = getConditionalOp(cond);
        final OCLBinary.Expr condExpr = new OCLBinary.Expr(condOp, LIRKind.value(cmpKind), left,
                right);
        final OCLTernary.Select selectExpr = new OCLTernary.Select(
                LIRKind.combine(
                        trueValue, falseValue), condExpr, trueValue, falseValue);

        final Variable variable = newVariable(LIRKind.combine(
                trueValue, falseValue));
        final AssignStmt assignStmt = new AssignStmt(variable, selectExpr);
        append(assignStmt);

        return variable;
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
        DeoptimizationReason reason = getMetaAccess().decodeDeoptReason((JavaConstant) actionAndReason);
        DeoptimizationAction action = getMetaAccess().decodeDeoptAction((JavaConstant) actionAndReason);
        int debugId = getMetaAccess().decodeDebugId((JavaConstant) actionAndReason);
        trace("emitDeoptimize: id=%d, reason=%s, action=%s", debugId, reason, action);
        append(new OCLControlFlow.DeoptOp(actionAndReason));
    }

    @Override
    public void emitIntegerTestBranch(Value value, Value value1, LabelRef lr, LabelRef lr1, double d) {
        unimplemented();
    }

    @Override
    public Variable emitIntegerTestMove(Value value, Value value1, Value value2, Value value3) {
        unimplemented();
        return null;
    }

    @Override
    public void emitJump(LabelRef lr) {
        unimplemented();
    }

    @Override
    public void emitMembar(int i) {
        unimplemented();
    }

    @Override
    public void emitNullCheck(Value value, LIRFrameState lirfs) {
        unimplemented();
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef lr, LabelRef lr1, LIRKind lirk, double d) {
        unimplemented();
    }

    @Override
    public void emitPause() {
        unimplemented();
    }

    @Override
    public void emitPrefetchAllocate(Value value) {
        unimplemented();
    }

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        trace("emitReturn: input=%s", input);
        if (input != null) {
            LIRKind lirKind = LIRKind.value(input.getPlatformKind());
            ExprStmt stmt = new ExprStmt(new OCLUnary.Expr(OCLUnaryOp.RETURN, lirKind, input));
            append(stmt);
        } else {
            append(new ExprStmt(new OCLNullary.Expr(OCLNullaryOp.RETURN, LIRKind.Illegal)));
        }
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy ss, Variable vrbl, LabelRef[] lrs, LabelRef lr) {
        unimplemented();
    }

    @Override
    public void emitUnwind(Value value) {
        unimplemented();
    }

    public OCLBuiltinTool getOCLBuiltinTool() {
        return oclBuiltinTool;
    }

    public OCLTargetDescription target() {
        return (OCLTargetDescription) super.target();
    }

    @Override
    public OCLArithmeticTool getArithmetic() {
        return (OCLArithmeticTool) super.getArithmetic();
    }

    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K k) {
        unimplemented();
        return null;
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage fcl, Value value, Value[] values, Value[] values1, LIRFrameState lirfs) {
        unimplemented();
    }

    @Override
    protected void emitTableSwitch(int i, LabelRef lr, LabelRef[] lrs, Value value) {
        unimplemented();
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind pk) {
        unimplemented();
        return null;
    }

}
