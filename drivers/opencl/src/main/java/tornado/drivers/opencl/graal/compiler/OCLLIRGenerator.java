package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.spi.CodeGenProviders;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGenerator;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.*;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.OCLLIRKindTool;
import tornado.drivers.opencl.graal.OCLStamp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.ExprStmt;
import tornado.drivers.opencl.graal.lir.*;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;

public class OCLLIRGenerator extends LIRGenerator {

    protected OCLBuiltinTool oclBuiltinTool;
    protected OCLGenTool oclGenTool;

    public OCLLIRGenerator(CodeGenProviders providers, LIRGenerationResult res) {
        super(new OCLLIRKindTool((OCLTargetDescription) providers.getCodeCache().getTarget()), new OCLArithmeticTool(), new OCLMoveFactory(), providers, res);
        this.oclBuiltinTool = new OCLBuiltinTool();
        this.oclGenTool = new OCLGenTool(this);
    }

    @Override
    public LIRKind getLIRKind(Stamp stamp) {
        if (stamp instanceof OCLStamp) {
            return LIRKind.value(((OCLStamp) stamp).getOCLKind());
        } else {
            return super.getLIRKind(stamp);
        }
    }

    @Override
    public Variable newVariable(ValueKind<?> lirKind) {
        PlatformKind pk = lirKind.getPlatformKind();
        ValueKind<?> actualLIRKind = lirKind;
        OCLKind oclKind = OCLKind.ILLEGAL;
        if (pk instanceof OCLKind) {
//        if (pk instanceof Kind) {
//            OCLTargetDescription target = (OCLTargetDescription) getCodeCache().getTarget();
//            oclKind = target.getOCLKind((Kind) pk);
//            actualLIRKind = LIRKind.value(oclKind);
//        } else if (pk instanceof OCLKind) {
            oclKind = (OCLKind) pk;
        } else {
            shouldNotReachHere();
        }

        final Variable var = super.newVariable(actualLIRKind);
        trace("newVariable: %s <- %s (%s)", var.toString(),
                actualLIRKind.toString(), actualLIRKind.getClass().getName());

        var.setName(oclKind.getTypePrefix() + "_" + var.index);
        OCLLIRGenerationResult res = (OCLLIRGenerationResult) getResult();
        res.insertVariable(var);

        return var;
    }

    @Override
    public OCLLIRKindTool getLIRKindTool() {
        return (OCLLIRKindTool) super.getLIRKindTool();
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] sss, JavaConstant[] jcs) {
        unimplemented();
        return null;
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
        JavaConstant constant = ((ConstantValue) actionAndReason).getJavaConstant();
        DeoptimizationReason reason = getMetaAccess().decodeDeoptReason(constant);
        DeoptimizationAction action = getMetaAccess().decodeDeoptAction(constant);
        int debugId = getMetaAccess().decodeDebugId(constant);
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
    public void emitStrategySwitch(SwitchStrategy ss, Variable value, LabelRef[] keyTargets, LabelRef defaultTarget) {
//        @Override
//    public void emitStrategySwitch(JavaConstant[] keyConstants, double[] keyProbabilities,
//            LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
        trace("emitStrategySwitch: key=%s", value);
        append(new OCLControlFlow.SwitchOp(value, ss.getKeyConstants(), keyTargets, defaultTarget));
    }

    @Override
    public void emitUnwind(Value value) {
        unimplemented();
    }

    public OCLBuiltinTool getOCLBuiltinTool() {
        return oclBuiltinTool;
    }

    @Override
    public OCLTargetDescription target() {
        return (OCLTargetDescription) super.target();
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return super.getValueKind(javaKind); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OCLArithmeticTool getArithmetic() {
        return (OCLArithmeticTool) super.getArithmetic();
    }

    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        return kind;
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
