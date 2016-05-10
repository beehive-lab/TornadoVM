package tornado.graal.phases.lir;

import java.util.List;

import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.AllocationPhase;

public class ControlFlowOptimization extends AllocationPhase{

	@Override
	protected <B extends AbstractBlockBase<B>> void run(TargetDescription target,
			LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
			SpillMoveFactory spillMoveFactory) {
		return;

	}

}
