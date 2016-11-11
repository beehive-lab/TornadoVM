package tornado.graal.phases.lir;

import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.phases.AllocationPhase;
import jdk.vm.ci.code.TargetDescription;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class ControlFlowOptimization extends AllocationPhase {

    @Override
    protected void run(TargetDescription td, LIRGenerationResult lirgr, AllocationContext c) {
        unimplemented();
    }

}
