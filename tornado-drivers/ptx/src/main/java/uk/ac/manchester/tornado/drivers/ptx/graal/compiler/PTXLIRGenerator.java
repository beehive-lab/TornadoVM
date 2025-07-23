/*
 * Copyright (c) 2020, 2022, 2025, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt.ExprStmt;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.PTXTargetDescription;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXLIRKindTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXStamp;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryOp;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXNullaryOp;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXArithmeticTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBuiltinTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXControlFlow;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXGenTool;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXNullary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXTernary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;

public class PTXLIRGenerator extends LIRGenerator {
    private final PTXGenTool ptxGenTool;
    private final PTXBuiltinTool ptxBuiltinTool;

    private final Map<String, Variable> parameterAllocations;

    public PTXLIRGenerator(Providers providers, LIRGenerationResult lirGenRes) {
        super(new PTXLIRKindTool((PTXTargetDescription) providers.getCodeCache().getTarget()), new PTXArithmeticTool(), new PTXBarrierSetLIRGenerator(), new PTXMoveFactory(), providers, lirGenRes);
        ptxGenTool = new PTXGenTool(this);
        parameterAllocations = new HashMap<>();
        ptxBuiltinTool = new PTXBuiltinTool();
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
    public PTXLIRKindTool getLIRKindTool() {
        return (PTXLIRKindTool) super.getLIRKindTool();
    }

    @Override
    public PTXTargetDescription target() {
        return (PTXTargetDescription) super.target();
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
    public void emitCacheWriteback(Value address) {
        unimplemented();
    }

    @Override
    public void emitCacheWritebackSync(boolean isPreSync) {
        unimplemented();
    }

    @Override
    public void emitSpeculationFence() {
        unimplemented();
    }

    @Override
    public LIRKind getLIRKind(Stamp stamp) {
        return (stamp instanceof PTXStamp) ? LIRKind.value(((PTXStamp) stamp).getPTXKind()) : super.getLIRKind(stamp);
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
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder,
            BarrierType barrierType) {
        return null;
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder, BarrierType barrierType) {
        return null;
    }

    @Override
    public Value emitAtomicReadAndAdd(LIRKind accessKind, Value address, Value delta) {
        return null;
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRKind accessKind, Value address, Value newValue, BarrierType barrierType) {
        return null;
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
        JavaConstant constant = ((ConstantValue) actionAndReason).getJavaConstant();
        DeoptimizationReason reason = getMetaAccess().decodeDeoptReason(constant);
        DeoptimizationAction action = getMetaAccess().decodeDeoptAction(constant);
        int debugId = getMetaAccess().decodeDebugId(constant);
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitDeoptimize: id=%d, reason=%s, action=%s", debugId, reason, action);
        append(new PTXControlFlow.DeoptOp(actionAndReason));
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        unimplemented();
        return null;
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
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitReturn: input=%s", input);
        if (input != null) {
            PTXKind returnKind = (PTXKind) input.getPlatformKind();
            LIRKind lirKind = LIRKind.value(returnKind);
            Variable returnVar = newReturnVariable(lirKind);
            if (returnKind.isVector()) {
                append(new PTXLIRStmt.VectorStoreStmt((Variable) input, new PTXUnary.MemoryAccess(PTXArchitecture.paramSpace, returnVar, null)));
            } else {
                append(new PTXLIRStmt.AssignStmt(returnVar, input));
            }
        }
        append(new ExprStmt(new PTXNullary.Expr(PTXNullaryOp.RETURN, LIRKind.Illegal)));

    }

    @Override
    public void emitJump(LabelRef label) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitJump: label=%s", label);
        append(new PTXControlFlow.Branch(label, false, false));
    }

    public void emitJump(LabelRef label, boolean isLoopEdgeBack) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitJump: label=%s isLoopEdgeBack=%b", label, isLoopEdgeBack);
        append(new PTXControlFlow.Branch(label, false, isLoopEdgeBack));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
            double trueDestinationProbability) {
        unimplemented();
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability) {
        unimplemented();
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {
        unimplemented();
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        if (left.getValueKind().equals(LIRKind.value(PTXKind.PRED))) {
            return emitConditionalMovePred(left, unorderedIsTrue, trueValue, falseValue);
        } else {
            return emitConditionalMoveValue(left, right, cond, unorderedIsTrue, trueValue, falseValue);
        }
    }

    public Variable emitConditionalMovePred(Value left, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitConditionalMovePred: (%s) ? %s : %s, unorderedIsTrue:%s", left, trueValue, falseValue, unorderedIsTrue);

        LIRKind kind = LIRKind.combine(trueValue, falseValue);
        Variable result = newVariable(kind);

        append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(PTXAssembler.PTXTernaryOp.SELP, kind, trueValue, falseValue, left)));

        return result;
    }

    public Variable emitConditionalMoveValue(Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitConditionalMoveValue: (%s %s %s) ? %s : %s, unorderedIsTrue:%s", left, cond.operator, right, trueValue, falseValue, unorderedIsTrue);

        LIRKind kind = LIRKind.combine(trueValue, falseValue);
        Variable predicate = newVariable(LIRKind.value(PTXKind.PRED));
        Variable result = newVariable(kind);

        append(new PTXLIRStmt.AssignStmt(predicate, new PTXBinary.Expr(getConditionalOp(cond), kind, left, right)));
        append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(PTXAssembler.PTXTernaryOp.SELP, kind, trueValue, falseValue, predicate)));

        return result;
    }

    /**
     * It generates an IntegerTestMove operation, which moves a value to a parameter
     * based on a bitwise and operation between two values.
     *
     * @param leftVal
     *     the left value of a condition
     * @param right
     *     the right value of a condition
     * @param trueValue
     *     the true value to move in the result
     * @param falseValue
     *     the false value to move in the result
     * @return Variable: reference to the variable that contains the result
     */
    @Override
    public Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitIntegerTestMove: " + leftVal + " " + "&" + right + " ? " + trueValue + " : " + falseValue);
        assert leftVal.getPlatformKind() == right.getPlatformKind() && ((PTXKind) leftVal.getPlatformKind()).isInteger();

        assert trueValue.getPlatformKind() == falseValue.getPlatformKind();

        LIRKind kind = LIRKind.combine(trueValue, falseValue);
        final Variable andResult = newVariable(LIRKind.combine(leftVal, right));
        final ConstantValue zeroConstant = new ConstantValue(andResult.getValueKind(), JavaConstant.forInt(0));
        final Variable predicate = newVariable(LIRKind.value(PTXKind.PRED));
        final Variable result = newVariable(kind);

        append(new PTXLIRStmt.AssignStmt(andResult, new PTXBinary.Expr(PTXBinaryOp.BITWISE_AND, kind, leftVal, right)));
        append(new PTXLIRStmt.AssignStmt(predicate, new PTXBinary.Expr(PTXBinaryOp.SETP_EQ, kind, andResult, zeroConstant)));
        append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(PTXAssembler.PTXTernaryOp.SELP, kind, trueValue, falseValue, predicate)));

        return result;
    }

    @Override
    public Variable emitReverseBytes(Value operand) {
        return null;
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        unimplemented();
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, AllocatableValue key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitStrategySwitch: strategy=%s key=%s defaultTarget=%s", strategy, key, defaultTarget);
        LIRKind kind = LIRKind.value(PTXKind.PRED);
        Variable predicate = newVariable(kind);
        Constant[] constants = strategy.getKeyConstants();
        for (int i = 0; i < keyTargets.length; i++) {
            append(new PTXLIRStmt.AssignStmt(predicate, new PTXBinary.Expr(PTXBinaryOp.SETP_EQ, kind, key, new ConstantValue(LIRKind.value(PTXKind.S32), constants[i]))));
            emitConditionalBranch(keyTargets[i], predicate, false, false);
        }
        append(new PTXControlFlow.Branch(defaultTarget, false, false));
    }

    @Override
    protected void emitRangeTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue key) {

    }

    @Override
    protected void emitHashTableSwitch(JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value, Value hash) {

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
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        unimplemented();
        return null;
    }

    @Override
    public int getArrayLengthOffset() {
        return 0;
    }

    @Override
    public Register getHeapBaseRegister() {
        return null;
    }

    public Variable newReturnVariable(ValueKind<?> lirKind) {
        final Variable variable = super.newVariable(lirKind);
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "newReturnVariable: %s <- %s (%s)", variable.toString(), lirKind.toString(), lirKind.getClass().getName());

        PTXLIRGenerationResult res = (PTXLIRGenerationResult) getResult();
        res.setReturnVariable(variable);

        if (!(variable.getPlatformKind() instanceof PTXKind)) {
            shouldNotReachHere();
        }

        return variable;
    }

    public Variable newVariable(ValueKind<?> lirKind, boolean isArray) {
        final Variable variable = super.newVariable(lirKind);
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "newVariable: %s <- %s (%s)", variable.toString(), lirKind.toString(), lirKind.getClass().getName());

        PTXLIRGenerationResult res = (PTXLIRGenerationResult) getResult();
        res.insertVariableAndGetIndex(variable, isArray);

        return variable;
    }

    @Override
    public Variable newVariable(ValueKind<?> lirKind) {
        return newVariable(lirKind, false);
    }

    public PTXGenTool getPTXGenTool() {
        return ptxGenTool;
    }

    public void emitParameterAlloc() {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitParameterAlloc");
        Variable kernelContextPointer = newVariable(LIRKind.value(PTXArchitecture.KERNEL_CONTEXT.getLirKind()));
        parameterAllocations.put(PTXArchitecture.KERNEL_CONTEXT.getName(), kernelContextPointer);
        append(new PTXLIRStmt.LoadStmt(new PTXUnary.MemoryAccess(PTXAssemblerConstants.KERNEL_CONTEXT_NAME), kernelContextPointer, PTXNullaryOp.LD));
    }

    public void emitConditionalBranch(LabelRef ref, Variable predicate, boolean isNegated, boolean isLoopEdgeBack) {
        append(new PTXLIRStmt.ConditionalStatement(new PTXControlFlow.Branch(ref, true, isLoopEdgeBack), predicate, isNegated));
    }

    public Variable getParameterAllocation(PTXArchitecture.PTXParam param) {
        return parameterAllocations.get(param.getName());
    }

}
