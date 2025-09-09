/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
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

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLLIRKindTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLNullaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBinary;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLGenTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.ExprStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLNullary;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLTernary;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * It traverses the OCL HIR and generates OCL LIR.
 */
public class OCLLIRGenerator extends LIRGenerator {

    private final OCLBuiltinTool oclBuiltinTool;
    private final OCLGenTool oclGenTool;

    public OCLLIRGenerator(CodeGenProviders providers, LIRGenerationResult res) {
        super(new OCLLIRKindTool((OCLTargetDescription) providers.getCodeCache().getTarget()), new OCLArithmeticTool(), new OCLBarrierSetLIRGenerator() {

        }, new OCLMoveFactory(), providers, res);
        this.oclBuiltinTool = new OCLBuiltinTool();
        this.oclGenTool = new OCLGenTool(this);
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
    }

    @Override
    public void emitSpeculationFence() {
        unimplemented();
    }

    @Override
    public LIRKind getLIRKind(Stamp stamp) {
        return (stamp instanceof OCLStamp) ? LIRKind.value(((OCLStamp) stamp).getOCLKind()) : super.getLIRKind(stamp);
    }

    @Override
    public Variable newVariable(ValueKind<?> valueKind) {
        PlatformKind pk = valueKind.getPlatformKind();
        if (!(pk instanceof OCLKind)) {
            shouldNotReachHere();
        }

        final Variable variable = super.newVariable(valueKind);
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "newVariable: %s <- %s (%s)", variable.toString(), valueKind.toString(), valueKind.getClass().getName());

        OCLLIRGenerationResult res = (OCLLIRGenerationResult) getResult();
        res.insertVariable(variable);

        return variable;
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

    @Override
    public int getArrayLengthOffset() {
        return 0;
    }

    @Override
    public Register getHeapBaseRegister() {
        return null;
    }

    public OCLGenTool getOCLGenTool() {
        return oclGenTool;
    }

    @Override
    public LIRInstruction createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitAddress(AllocatableValue allocatableValue) {
        unimplemented();
        return null;
    }

    @Override
    public void emitCompareBranch(PlatformKind pk, Value value, Value value1, Condition cndtn, boolean bln, LabelRef lr, LabelRef lr1, double d) {
        unimplemented();
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitConditionalMove: (%s %s %s) ? %s : %s, unorderedIsTrue:%s", left, cond, right, trueValue, falseValue, unorderedIsTrue);

        final OCLBinaryOp condOp = getConditionalOp(cond);
        final OCLBinary.Expr condExpr = new OCLBinary.Expr(condOp, LIRKind.value(cmpKind), left, right);
        final OCLTernary.Select selectExpr = new OCLTernary.Select(LIRKind.combine(trueValue, falseValue), condExpr, trueValue, falseValue);

        final Variable variable = newVariable(LIRKind.combine(trueValue, falseValue));
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
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitDeoptimize: id=%d, reason=%s, action=%s", debugId, reason, action);
        append(new OCLControlFlow.DeoptOp(actionAndReason));
    }

    @Override
    public void emitIntegerTestBranch(Value value, Value value1, LabelRef lr, LabelRef lr1, double d) {
        unimplemented();
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitIntegerTestMove: " + left + " " + "&" + right + " ? " + trueValue + " : " + falseValue);
        assert left.getPlatformKind() == right.getPlatformKind() && ((OCLKind) left.getPlatformKind()).isInteger();

        assert trueValue.getPlatformKind() == falseValue.getPlatformKind();

        final OCLBinary.Expr condExpr = new OCLBinary.Expr(OCLBinaryOp.BITWISE_AND, null, left, right);
        final OCLTernary.Select selectExpr = new OCLTernary.Select(LIRKind.combine(trueValue, falseValue), condExpr, falseValue, trueValue);

        final Variable variable = newVariable(LIRKind.combine(trueValue, falseValue));
        final AssignStmt assignStmt = new AssignStmt(variable, selectExpr);
        append(assignStmt);

        return variable;
    }

    @Override
    public Variable emitReverseBytes(Value operand) {
        return null;
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {

    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, AllocatableValue key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitStrategySwitch: key=%s", key);
        append(new OCLControlFlow.SwitchOp(key, strategy.getKeyConstants(), keyTargets, defaultTarget));
    }

    @Override
    protected void emitRangeTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue key) {

    }

    @Override
    protected void emitHashTableSwitch(JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value, Value hash) {

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
        if (!TornadoOptions.IGNORE_NULL_CHECKS) {
            unimplemented();
        }
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
        Logger.traceBuildLIR(Logger.BACKEND.OpenCL, "emitReturn: input=%s", input);
        if (input != null) {
            LIRKind lirKind = LIRKind.value(input.getPlatformKind());
            ExprStmt stmt = new ExprStmt(new OCLUnary.Expr(OCLUnaryOp.RETURN, lirKind, input));
            append(stmt);
        } else {
            append(new ExprStmt(new OCLNullary.Expr(OCLNullaryOp.RETURN, LIRKind.Illegal)));
        }
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
        return super.getValueKind(javaKind);
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
    protected JavaConstant zapValueForKind(PlatformKind pk) {
        unimplemented();
        return null;
    }

    public OCLGenTool getOclGenTool() {
        return oclGenTool;
    }

}
