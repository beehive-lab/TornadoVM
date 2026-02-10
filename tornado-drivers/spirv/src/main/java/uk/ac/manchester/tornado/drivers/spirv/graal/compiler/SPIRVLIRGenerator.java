/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.SwitchStrategy;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.BarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGenerator;

import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVLIRKindTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBinary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBuiltinTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVControlFlow;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVGenTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVNullary;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary;

/**
 * It traverses the SPIR-V HIR and generates SPIR-V LIR from which the backend
 * will emit the SPIR-V code.
 */
public class SPIRVLIRGenerator extends LIRGenerator {

    private final int methodIndex;
    private SPIRVGenTool spirvGenTool;
    private SPIRVBuiltinTool spirvBuiltinTool;

    public SPIRVLIRGenerator(Providers providers, LIRGenerationResult lirGenRes, final int methodIndex) {
        super(new SPIRVLIRKindTool((SPIRVTargetDescription) providers.getCodeCache().getTarget()), new SPIRVArithmeticTool(), new BarrierSetLIRGeneratorTool() {
                }, new SPIRVMoveFactory(), providers,
                lirGenRes);
        spirvGenTool = new SPIRVGenTool(this);
        spirvBuiltinTool = new SPIRVBuiltinTool();
        this.methodIndex = methodIndex;
    }

    @Override
    public LIRKind getLIRKind(Stamp stamp) {
        if (stamp instanceof SPIRVStamp) {
            return LIRKind.value(((SPIRVStamp) stamp).getSPIRVKind());
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
        throw new RuntimeException("Unimplemented");
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
    public boolean isReservedRegister(Register r) {
        return false;
    }

    @Override
    public Value emitReadReturnAddress(Stamp wordStamp, int returnAddressSize) {
        unimplemented();
        return null;
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
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

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitReturn: input=%s", input);
        BasicBlock<?> currentBlock = getCurrentBlock();
        if (input != null) {
            LIRKind lirKind = LIRKind.value(input.getPlatformKind());
            append(new SPIRVLIRStmt.ExprStmt(new SPIRVUnary.ReturnWithValue(lirKind, input, currentBlock)));
        } else {
            append(new SPIRVLIRStmt.ExprStmt(new SPIRVNullary.ReturnNoOperands(LIRKind.Illegal, currentBlock)));
        }
    }

    @Override
    public void emitJump(LabelRef label) {
        throw new RuntimeException("Not implemented yet");
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
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public void emitOpMaskTestBranch(Value left, boolean negateLeft, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {

    }

    @Override
    public void emitOpMaskOrTestBranch(Value left, Value right, boolean allZeros, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {

    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emit TernaryBranch: " + leftVal + " " + cond + right + " ? " + trueValue + " : " + falseValue);
        final Variable resultConditionalMove = newVariable(LIRKind.combine(trueValue, falseValue));
        SPIRVBinary.TernaryCondition ternaryInstruction = new SPIRVBinary.TernaryCondition(LIRKind.combine(trueValue, falseValue), leftVal, cond, right, trueValue, falseValue);
        append(new SPIRVLIRStmt.AssignStmt(resultConditionalMove, ternaryInstruction));
        return resultConditionalMove;
    }

    /**
     * It generates an IntegerTestMove operation, which moves a value to a parameter
     * based on a bitwise and operation between two values.
     *
     * @param leftVal    the left value of a condition
     * @param right      the right value of a condition
     * @param trueValue  the true value to move in the result
     * @param falseValue the false value to move in the result
     * @return Variable: reference to the variable that contains the result
     */
    @Override
    public Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitIntegerTestMove: " + leftVal + " " + "&" + right + " ? " + trueValue + " : " + falseValue);
        assert leftVal.getPlatformKind() == right.getPlatformKind() && ((SPIRVKind) leftVal.getPlatformKind()).isInteger();

        assert trueValue.getPlatformKind() == falseValue.getPlatformKind();

        LIRKind kind = LIRKind.combine(trueValue, falseValue);
        final Variable result = newVariable(kind);

        SPIRVBinary.IntegerTestNode integerTestNode = new SPIRVBinary.IntegerTestNode(SPIRVAssembler.SPIRVBinaryOp.BITWISE_AND, kind, leftVal, right);
        SPIRVBinary.IntegerTestMoveNode moveNode = new SPIRVBinary.IntegerTestMoveNode(integerTestNode, result, kind, trueValue, falseValue);
        append(new SPIRVLIRStmt.AssignStmt(result, moveNode));

        return result;
    }

    @Override
    public Variable emitOpMaskTestMove(Value leftVal, boolean negateLeft, Value right, Value trueValue, Value falseValue) {
        return null;
    }

    @Override
    public Variable emitOpMaskOrTestMove(Value leftVal, Value right, boolean allZeros, Value trueValue, Value falseValue) {
        return null;
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
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitStrategySwitch: strategy=%s key=%s defaultTarget=%s", strategy, key, defaultTarget);
        append(new SPIRVControlFlow.SwitchStatement(key, strategy, keyTargets, defaultTarget));
    }

    @Override
    protected void emitRangeTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, SwitchStrategy remainingStrategy, LabelRef[] remainingTargets, AllocatableValue key) {

    }

    @Override
    protected void emitHashTableSwitch(JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value, Value hash) {
        unimplemented();
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
    public void emitSpeculationFence() {
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
    public Variable newVariable(ValueKind<?> valueKind) {
        PlatformKind pk = valueKind.getPlatformKind();
        ValueKind<?> actualLIRKind = valueKind;
        SPIRVKind spirvKind = SPIRVKind.ILLEGAL;
        if (pk instanceof SPIRVKind) {
            spirvKind = (SPIRVKind) pk;
        } else {
            shouldNotReachHere();
        }

        // Create a new variable
        final Variable variable = super.newVariable(actualLIRKind);
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "[SPIR-V] newVariable: %s <- %s (%s)", variable.toString(), actualLIRKind.toString(), actualLIRKind.getClass().getName());

        // Format of the variable "<type>_<number>"
        // variable.setName("spirv_" + spirvKind.getTypePrefix() + "_" + variable.index
        // + "F" + methodIndex);
        SPIRVLIRGenerationResult res = (SPIRVLIRGenerationResult) getResult();
        res.insertVariable(variable);
        return variable;
    }

    public Variable newArrayVariable(Variable variable, Value length) {
        return new ArrayVariable(variable, length);
    }

    @Override
    public SPIRVLIRKindTool getLIRKindTool() {
        return (SPIRVLIRKindTool) super.getLIRKindTool();
    }

    public SPIRVGenTool getSPIRVGenTool() {
        return spirvGenTool;
    }

    public SPIRVBuiltinTool getSpirvBuiltinTool() {
        return spirvBuiltinTool;
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return super.getValueKind(javaKind);
    }

    @Override
    public SPIRVArithmeticTool getArithmetic() {
        return (SPIRVArithmeticTool) super.getArithmetic();
    }

    public void emitConditionalBranch(Value condition, LabelRef trueBranch, LabelRef falseBranch, int unrollFactor) {
        append(new SPIRVControlFlow.BranchConditional(condition, trueBranch, falseBranch, unrollFactor));
    }

    public void emitJump(LabelRef label, boolean isLoopEdgeBack) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitJump: label=%s isLoopEdgeBack=%b", label, isLoopEdgeBack);
        append(new SPIRVControlFlow.Branch(label));
    }

    public static class ArrayVariable extends Variable {

        private Variable variable;
        private Value length;

        public ArrayVariable(Variable variable, Value length) {
            super(variable.getValueKind(), variable.index);
            this.variable = variable;
            this.length = length;
        }

        public Value getLength() {
            return length;
        }

        public Variable getVariable() {
            return variable;
        }

    }
}
