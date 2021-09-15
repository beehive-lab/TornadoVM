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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.lir.ConstantValue;
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
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
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

public class OCLLIRGenerator extends LIRGenerator {

    private OCLBuiltinTool oclBuiltinTool;
    private OCLGenTool oclGenTool;

    public OCLLIRGenerator(CodeGenProviders providers, LIRGenerationResult res) {
        super(new OCLLIRKindTool((OCLTargetDescription) providers.getCodeCache().getTarget()), new OCLArithmeticTool(), new OCLMoveFactory(), providers, res);
        this.oclBuiltinTool = new OCLBuiltinTool();
        this.oclGenTool = new OCLGenTool(this);
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
            oclKind = (OCLKind) pk;
        } else {
            shouldNotReachHere();
        }

        final Variable var = super.newVariable(actualLIRKind);
        trace("newVariable: %s <- %s (%s)", var.toString(), actualLIRKind.toString(), actualLIRKind.getClass().getName());

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
    public StandardOp.ZapRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitAddress(AllocatableValue allocatableValue) {
        unimplemented();
        return null;
    }

    @Override
    public Variable emitByteSwap(Value value) {
        unimplemented();
        return null;
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
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {

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
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder) {
        return null;
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder) {
        return null;
    }

    @Override
    public Value emitAtomicReadAndAdd(Value address, ValueKind<?> valueKind, Value delta) {
        unimplemented();
        return null;
    }

    @Override
    public Value emitAtomicReadAndWrite(Value address, ValueKind<?> valueKind, Value newValue) {
        unimplemented();
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
        return super.getValueKind(javaKind); // To change body of generated
                                             // methods, choose Tools |
                                             // Templates.
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
    protected void emitTableSwitch(int i, LabelRef lr, LabelRef[] lrs, Value value) {
        unimplemented();
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind pk) {
        unimplemented();
        return null;
    }

}
