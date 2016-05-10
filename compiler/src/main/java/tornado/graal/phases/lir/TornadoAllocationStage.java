package tornado.graal.phases.lir;

import com.oracle.graal.lir.phases.AllocationPhase.AllocationContext;
import com.oracle.graal.lir.phases.LIRPhaseSuite;

public class TornadoAllocationStage extends LIRPhaseSuite<AllocationContext> {

	public TornadoAllocationStage() {
		//appendPhase(new PhiValueAssociationPhase());
		//appendPhase(new ControlFlowOptimization());
		//appendPhase(new VirtualRegisterAllocationPhase());
	}
}
