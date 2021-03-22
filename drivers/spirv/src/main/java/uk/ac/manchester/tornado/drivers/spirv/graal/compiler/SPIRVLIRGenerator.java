package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.vm.ci.code.*;
import jdk.vm.ci.meta.*;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.lir.*;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.phases.util.Providers;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLLIRKindTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLMoveFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVLIRKindTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;

/**
 * It traverses the SPIRV HIR and generates SPIRV LIR.
 */
public class SPIRVLIRGenerator extends LIRGenerator {

    public SPIRVLIRGenerator(CodeGenProviders providers, LIRGenerationResult lirGenRes) {
        super(new SPIRVLIRKindTool((SPIRVTargetDescription) providers.getCodeCache().getTarget()), new SPIRVArithmeticTool(), new SPIRVMoveFactory(), providers, lirGenRes);
    }

    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        return null;
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {

    }

    @Override
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        return null;
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue) {
        return null;
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {

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

    }

    @Override
    public void emitJump(LabelRef label) {

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

    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        return null;
    }

    @Override
    public Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue) {
        return null;
    }

    @Override
    public Variable emitByteSwap(Value operand) {
        return null;
    }

    @Override
    public void emitPause() {

    }

    @Override
    public void emitPrefetchAllocate(Value address) {

    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        return null;
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        return null;
    }

    @Override
    public void emitSpeculationFence() {

    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {

    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {

    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {

    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        return null;
    }

    @Override
    public StandardOp.ZapRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        return null;
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        return null;
    }
}
