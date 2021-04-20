package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.graal.compiler.TornadoCodeGenerator.trace;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
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
import uk.ac.manchester.tornado.drivers.spirv.SPIRVTargetDescription;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVLIRKindTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBuiltinTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVGenTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

/**
 * It traverses the SPIRV HIR and generates SPIRV LIR.
 */
public class SPIRVLIRGenerator extends LIRGenerator {

    private SPIRVGenTool spirvGenTool;
    private SPIRVBuiltinTool spirvBuiltinTool;

    public SPIRVLIRGenerator(CodeGenProviders providers, LIRGenerationResult lirGenRes) {
        super(new SPIRVLIRKindTool((SPIRVTargetDescription) providers.getCodeCache().getTarget()), new SPIRVArithmeticTool(), new SPIRVMoveFactory(), providers, lirGenRes);
        spirvGenTool = new SPIRVGenTool(this);
        spirvBuiltinTool = new SPIRVBuiltinTool();
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
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {

    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length, boolean directPointers) {
        unimplemented();
        return null;
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
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
        return null;
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        unimplemented();
        return null;
    }

    @Override
    public Variable newVariable(ValueKind<?> lirKind) {
        PlatformKind pk = lirKind.getPlatformKind();
        ValueKind<?> actualLIRKind = lirKind;
        SPIRVKind spirvKind = SPIRVKind.ILLEGAL;
        if (pk instanceof SPIRVKind) {
            spirvKind = (SPIRVKind) pk;
        } else {
            shouldNotReachHere();
        }

        // Create a new variable
        final Variable var = super.newVariable(actualLIRKind);
        trace("newVariable: %s <- %s (%s)", var.toString(), actualLIRKind.toString(), actualLIRKind.getClass().getName());

        // Format of the variable "%<type>_<number>"
        var.setName("%" + spirvKind.getTypePrefix() + "_" + var.index);
        SPIRVIRGenerationResult res = (SPIRVIRGenerationResult) getResult();
        res.insertVariable(var);
        return var;
    }
}
