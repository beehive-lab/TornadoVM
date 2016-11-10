package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLMoveFactory implements MoveFactory {

    @Override
    public boolean canInlineConstant(JavaConstant jc) {
        return true;
    }

    @Override
    public boolean allowConstantToStackMove(Constant cnstnt) {
        unimplemented();
        return false;
    }

    @Override
    public LIRInstruction createMove(AllocatableValue av, Value value) {
        AssignStmt assign = new AssignStmt(av, value);
        return assign;
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue av, AllocatableValue av1) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createLoad(AllocatableValue av, Constant cnstnt) {
        unimplemented();
        return null;
    }

}
