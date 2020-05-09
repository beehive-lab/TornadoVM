package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.*;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.lir.*;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.phases.util.Providers;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXLIRKindTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXNullaryOp;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.*;

import java.util.HashMap;
import java.util.Map;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt.ExprStmt;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

public class PTXLIRGenerator extends LIRGenerator {
    private PTXGenTool ptxGenTool;
    private PTXBuiltinTool ptxBuiltinTool;

    private final Map<String, Variable> parameterAllocations;

    public PTXLIRGenerator(Providers providers, LIRGenerationResult lirGenRes) {
        super(
                new PTXLIRKindTool((CUDATargetDescription) providers.getCodeCache().getTarget()),
                new PTXArithmeticTool(),
                new PTXMoveFactory(),
                providers,
                lirGenRes
        );

        ptxGenTool = new PTXGenTool(this);
        parameterAllocations = new HashMap<>();
        ((PTXLIRGenerationResult)lirGenRes).setParameterAllocations(parameterAllocations);
        ptxBuiltinTool = new PTXBuiltinTool();
    }

    @Override
    public PTXLIRKindTool getLIRKindTool() {
        return (PTXLIRKindTool) super.getLIRKindTool();
    }

    @Override
    public CUDATargetDescription target() {
        return (CUDATargetDescription) super.target();
    }

    @Override
    public PTXArithmeticTool getArithmetic() {
        return (PTXArithmeticTool) super.getArithmetic();
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        unimplemented();
        return null;
    }

    @Override
    public void emitConvertNullToZero(AllocatableValue result, Value input) {
        unimplemented();
    }

    @Override
    public void emitConvertZeroToNull(AllocatableValue result, Value input) {
        unimplemented();
    }

    @Override
    public VirtualStackSlot allocateStackSlots(int slots) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitReadCallerStackPointer(Stamp wordStamp) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitReadReturnAddress(Stamp wordStamp, int returnAddressSize) {
        unimplemented();
        return null;
    }

    @Override
    public void emitZeroMemory(Value address, Value length, boolean isAligned) {
        unimplemented();
    }

    @Override
    public void emitSpeculationFence() {
        unimplemented();
    }

    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        return kind;
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        unimplemented();
    }

    @Override
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue,
                                            Value trueValue, Value falseValue) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue) {
        unimplemented(); return null;
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
        JavaConstant constant = ((ConstantValue) actionAndReason).getJavaConstant();
        DeoptimizationReason reason = getMetaAccess().decodeDeoptReason(constant);
        DeoptimizationAction action = getMetaAccess().decodeDeoptAction(constant);
        int debugId = getMetaAccess().decodeDebugId(constant);
        trace("emitDeoptimize: id=%d, reason=%s, action=%s", debugId, reason, action);
        append(new PTXControlFlow.DeoptOp(actionAndReason));
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        unimplemented(); return null;
    }

    @Override
    public void emitMembar(int barriers) {
        unimplemented();
    }

    @Override
    public void emitUnwind(Value operand) {
        unimplemented();
    }

    public PTXBuiltinTool getPtxBuiltinTool() {
        return ptxBuiltinTool;
    }

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        trace("emitReturn: input=%s", input);
        if (input != null) {
            unimplemented("Returning values from CUDA-PTX kernels is not implemented yet");
        } else {
            append(new ExprStmt(new PTXNullary.Expr(PTXNullaryOp.RETURN, LIRKind.Illegal)));
        }
    }

    @Override
    public void emitJump(LabelRef label) {
        unimplemented();
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        unimplemented();
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind,
                                        double overflowProbability) {
        unimplemented();
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination,
                                      double trueSuccessorProbability) {
        unimplemented();
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind,
                                        Value left,
                                        Value right,
                                        Condition cond,
                                        boolean unorderedIsTrue,
                                        Value trueValue,
                                        Value falseValue) {
        trace("emitConditionalMove?");

        LIRKind kind = LIRKind.combine(trueValue, falseValue);
        Variable predicate = newVariable(LIRKind.value(PTXKind.PRED));
        Variable result = newVariable(kind);

        append(new PTXLIRStmt.AssignStmt(predicate, new PTXBinary.Expr(getConditionalOp(cond), kind, left, right)));
        append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(PTXAssembler.PTXTernaryOp.SELP, kind,trueValue, falseValue, predicate)));

        return result;
    }

    public static PTXBinaryOp getConditionalOp(Condition condition) {
        switch (condition) {
            case AE:
            case GE:
                return PTXBinaryOp.SETP_GE;
            case AT:
            case GT:
                return PTXBinaryOp.SETP_GT;

            case EQ:
                return PTXBinaryOp.SETP_EQ;

            case BE:
            case LE:
                return PTXBinaryOp.SETP_LE;

            case BT:
            case LT:
                return PTXBinaryOp.SETP_LT;
            case NE:
                return PTXBinaryOp.SETP_NE;
            default:
                shouldNotReachHere();
                break;

        }
        return null;
    }

    @Override
    public Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue) {
        unimplemented(); return null;
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps,
                                     LIRFrameState info) {
        unimplemented();
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy,
                                   Variable key,
                                   LabelRef[] keyTargets,
                                   LabelRef defaultTarget) {
        LIRKind kind = LIRKind.value(PTXKind.PRED);
        Variable predicate = newVariable(kind);
        Constant[] constants = strategy.getKeyConstants();
        for (int i = 0; i < keyTargets.length; i++) {
            append(new PTXLIRStmt.AssignStmt(predicate, new PTXBinary.Expr(
                    PTXBinaryOp.SETP_EQ,
                    kind,
                    key,
                    new ConstantValue(LIRKind.value(PTXKind.S32), constants[i])
            )));
            emitConditionalBranch(keyTargets[i], predicate, false);
        }
        append(new PTXControlFlow.Branch(defaultTarget, false));
    }

    @Override
    public Variable emitByteSwap(Value operand) {
        unimplemented(); return null;
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length, boolean directPointers) {
        unimplemented(); return null;
    }

    @Override
    public void emitPause() {
        unimplemented();
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        unimplemented();
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        unimplemented();
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        unimplemented(); return null;
    }

    @Override
    public StandardOp.ZapRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        unimplemented(); return null;
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        unimplemented(); return null;
    }

    @Override
    public Variable newVariable(ValueKind<?> lirKind) {
        PlatformKind pk = lirKind.getPlatformKind();
        ValueKind<?> actualLIRKind = lirKind;
        PTXKind kind = PTXKind.ILLEGAL;
        if (pk instanceof PTXKind) {
            kind = (PTXKind) pk;
        } else {
            shouldNotReachHere();
        }

        final Variable var = super.newVariable(actualLIRKind);
        trace("newVariable: %s <- %s (%s)", var.toString(), actualLIRKind.toString(), actualLIRKind.getClass().getName());

        PTXLIRGenerationResult res = (PTXLIRGenerationResult) getResult();
        int indexForType = res.insertVariableAndGetIndex(var);

        var.setName(kind.getRegisterTypeString() + indexForType);

        return var;
    }

    public PTXGenTool getPTXGenTool() {
        return ptxGenTool;
    }

    public void emitParameterAlloc() {
//        Variable heapPointer = newVariable(LIRKind.value(PTXArchitecture.HEAP_POINTER.ptxKind));
//        parameterAllocations.put(PTXArchitecture.HEAP_POINTER.getName(), heapPointer);
//        append(new PTXLIRStmt.LoadStmt(
//                new PTXUnary.MemoryAccess(PTXAssemblerConstants.HEAP_PTR_NAME),
//                heapPointer
//        ));

        Variable stackPointer = newVariable(LIRKind.value(PTXArchitecture.STACK_POINTER.ptxKind));
        parameterAllocations.put(PTXArchitecture.STACK_POINTER.getName(), stackPointer);
        append(new PTXLIRStmt.LoadStmt(
                new PTXUnary.MemoryAccess(PTXAssemblerConstants.STACK_PTR_NAME),
                stackPointer,
                PTXNullaryOp.LD
        ));
    }

    public void emitConditionalBranch(LabelRef ref, Variable predicate, boolean isNegated) {
        append(new PTXLIRStmt.ConditionalStatement(new PTXControlFlow.Branch(ref), predicate, isNegated));
    }

    public Variable getParameterAllocation(PTXArchitecture.PTXParam param) {
        return parameterAllocations.get(param.getName());
    }
}
