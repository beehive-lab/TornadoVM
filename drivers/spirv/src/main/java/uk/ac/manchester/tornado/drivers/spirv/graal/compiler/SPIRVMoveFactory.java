package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

/**
 * Factory class for creating moves
 */
public class SPIRVMoveFactory implements LIRGeneratorTool.MoveFactory {

    @Override
    public boolean canInlineConstant(Constant constant) {
        return false;
    }

    @Override
    public boolean allowConstantToStackMove(Constant constant) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public LIRInstruction createMove(AllocatableValue result, Value input) {
        throw new RuntimeException("PENDING Unimplemented");
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public LIRInstruction createLoad(AllocatableValue result, Constant input) {
        throw new RuntimeException("Unimplemented");
    }

    @Override
    public LIRInstruction createStackLoad(AllocatableValue result, Constant input) {
        throw new RuntimeException("Unimplemented");
    }
}
