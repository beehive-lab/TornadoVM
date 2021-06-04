/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
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
        super(new PTXLIRKindTool((PTXTargetDescription) providers.getCodeCache().getTarget()), new PTXArithmeticTool(), new PTXMoveFactory(), providers, lirGenRes);

        ptxGenTool = new PTXGenTool(this);
        parameterAllocations = new HashMap<>();
        ptxBuiltinTool = new PTXBuiltinTool();
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
    public LIRKind getLIRKind(Stamp stamp) {
        if (stamp instanceof PTXStamp) {
            return LIRKind.value(((PTXStamp) stamp).getPTXKind());
        } else {
            return super.getLIRKind(stamp);
        }
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
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder) {
        unimplemented();
        return null;
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
        trace("emitReturn: input=%s", input);
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
        trace("emitJump: label=%s", label);
        append(new PTXControlFlow.Branch(label, false, false));
    }

    public void emitJump(LabelRef label, boolean isLoopEdgeBack) {
        trace("emitJump: label=%s isLoopEdgeBack=%b", label, isLoopEdgeBack);
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
        trace("emitConditionalMove?");

        LIRKind kind = LIRKind.combine(trueValue, falseValue);
        Variable predicate = newVariable(LIRKind.value(PTXKind.PRED));
        Variable result = newVariable(kind);

        append(new PTXLIRStmt.AssignStmt(predicate, new PTXBinary.Expr(getConditionalOp(cond), kind, left, right)));
        append(new PTXLIRStmt.AssignStmt(result, new PTXTernary.Expr(PTXAssembler.PTXTernaryOp.SELP, kind, trueValue, falseValue, predicate)));

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
        unimplemented();
        return null;
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        unimplemented();
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        trace("emitStrategySwitch: strategy=%s key=%s defaultTarget=%s", strategy, key, defaultTarget);
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
    public Variable emitByteSwap(Value operand) {
        unimplemented();
        return null;
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
        unimplemented();
        return null;
    }

    @Override
    public StandardOp.ZapRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        unimplemented();
        return null;
    }

    public Variable newReturnVariable(ValueKind<?> lirKind) {
        final Variable var = super.newVariable(lirKind);
        trace("newReturnVariable: %s <- %s (%s)", var.toString(), lirKind.toString(), lirKind.getClass().getName());

        PTXLIRGenerationResult res = (PTXLIRGenerationResult) getResult();
        res.setReturnVariable(var);

        if (!(var.getPlatformKind() instanceof PTXKind)) {
            shouldNotReachHere();
        }

        var.setName("retVar");
        return var;
    }

    public Variable newVariable(ValueKind<?> lirKind, boolean isArray) {
        final Variable var = super.newVariable(lirKind);
        trace("newVariable: %s <- %s (%s)", var.toString(), lirKind.toString(), lirKind.getClass().getName());

        PTXLIRGenerationResult res = (PTXLIRGenerationResult) getResult();
        int indexForType = res.insertVariableAndGetIndex(var, isArray);

        PTXKind kind = null;
        if (var.getPlatformKind() instanceof PTXKind) {
            kind = (PTXKind) var.getPlatformKind();
        } else {
            shouldNotReachHere();
        }

        if (isArray) {
            var.setName(kind.getRegisterTypeString() + "Arr" + indexForType);
        } else if (kind.isVector()) {
            var.setName(kind.getRegisterTypeString() + kind.getVectorLength() + "Vec" + indexForType);
        } else {
            var.setName(kind.getRegisterTypeString() + indexForType);
        }

        return var;
    }

    @Override
    public Variable newVariable(ValueKind<?> lirKind) {
        return newVariable(lirKind, false);
    }

    public PTXGenTool getPTXGenTool() {
        return ptxGenTool;
    }

    public void emitParameterAlloc() {
        trace("emitParameterAlloc");
        Variable stackPointer = newVariable(LIRKind.value(PTXArchitecture.STACK_POINTER.ptxKind));
        parameterAllocations.put(PTXArchitecture.STACK_POINTER.getName(), stackPointer);
        append(new PTXLIRStmt.LoadStmt(new PTXUnary.MemoryAccess(PTXAssemblerConstants.STACK_PTR_NAME), stackPointer, PTXNullaryOp.LD));
    }

    public void emitConditionalBranch(LabelRef ref, Variable predicate, boolean isNegated, boolean isLoopEdgeBack) {
        append(new PTXLIRStmt.ConditionalStatement(new PTXControlFlow.Branch(ref, true, isLoopEdgeBack), predicate, isNegated));
    }

    public Variable getParameterAllocation(PTXArchitecture.PTXParam param) {
        return parameterAllocations.get(param.getName());
    }
}
