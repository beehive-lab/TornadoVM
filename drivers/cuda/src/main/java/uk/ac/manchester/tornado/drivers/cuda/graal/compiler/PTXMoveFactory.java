package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXMoveFactory implements LIRGeneratorTool.MoveFactory {
    @Override
    public boolean canInlineConstant(Constant constant) {
        return true;
    }

    @Override
    public boolean allowConstantToStackMove(Constant constant) {
        unimplemented();
        return false;
    }

    @Override
    public LIRInstruction createMove(AllocatableValue result, Value input) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createLoad(AllocatableValue result, Constant input) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createStackLoad(AllocatableValue result, Constant input) {
        unimplemented();
        return null;
    }
}
