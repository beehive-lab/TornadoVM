/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;

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

    private SPIRVGenTool spirvGenTool;
    private SPIRVBuiltinTool spirvBuiltinTool;
    private final int methodIndex;

    public SPIRVLIRGenerator(CodeGenProviders providers, LIRGenerationResult lirGenRes, final int methodIndex) {
        super(new SPIRVLIRKindTool((SPIRVTargetDescription) providers.getCodeCache().getTarget()), new SPIRVArithmeticTool(), new SPIRVMoveFactory(), providers, lirGenRes);
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
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder) {
        return null;
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder) {
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

    }

    @Override
    public void emitUnwind(Value operand) {

    }

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitReturn: input=%s", input);
        AbstractBlockBase<?> currentBlock = getCurrentBlock();
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

    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability) {

    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {
        throw new RuntimeException("Not implemented yet");
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emit TernaryBranch: " + leftVal + " " + cond + right + " ? " + trueValue + " : " + falseValue);
        final Variable resultConditionalMove = newVariable(LIRKind.combine(trueValue, falseValue));
        SPIRVBinary.TernaryCondition ternaryInstruction = new SPIRVBinary.TernaryCondition(LIRKind.combine(trueValue, falseValue), leftVal, cond, right, trueValue, falseValue);
        append(new SPIRVLIRStmt.AssignStmt(resultConditionalMove, ternaryInstruction));
        return resultConditionalMove;
    }

    @Override
    public Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue) {
        return null;
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {

    }

    @Override
    public Variable emitByteSwap(Value operand) {
        return null;
    }

    @Override
    public void emitPause() {
        unimplemented();
    }

    @Override
    public void emitPrefetchAllocate(Value address) {

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

    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitStrategySwitch: strategy=%s key=%s defaultTarget=%s", strategy, key, defaultTarget);
        append(new SPIRVControlFlow.SwitchStatement(key, strategy, keyTargets, defaultTarget));
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
        variable.setName("spirv_" + spirvKind.getTypePrefix() + "_" + variable.index + "F" + methodIndex);
        SPIRVIRGenerationResult res = (SPIRVIRGenerationResult) getResult();
        res.insertVariable(variable);
        return variable;
    }

    public static class ArrayVariable extends Variable {

        private Variable variable;
        private Value length;

        public ArrayVariable(Variable variable, Value length) {
            super(variable.getValueKind(), variable.index);
            this.variable = variable;
            this.length = length;
            this.setName(variable.getName());
        }

        public Value getLength() {
            return length;
        }

        public Variable getVariable() {
            return variable;
        }

        @Override
        public String getName() {
            return variable.getName();
        }
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
    public void emitStringLatin1Inflate(Value src, Value dst, Value len) {
        unimplemented();
    }

    @Override
    public Variable emitStringUTF16Compress(Value src, Value dst, Value len) {
        unimplemented();
        return null;
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return super.getValueKind(javaKind);
    }

    @Override
    public SPIRVArithmeticTool getArithmetic() {
        return (SPIRVArithmeticTool) super.getArithmetic();
    }

    public void emitConditionalBranch(Value condition, LabelRef trueBranch, LabelRef falseBranch) {
        append(new SPIRVControlFlow.BranchConditional(condition, trueBranch, falseBranch));
    }

    public void emitJump(LabelRef label, boolean isLoopEdgeBack) {
        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitJump: label=%s isLoopEdgeBack=%b", label, isLoopEdgeBack);
        append(new SPIRVControlFlow.Branch(label));
    }
}
