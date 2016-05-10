package tornado.graal.phases.lir;

import java.util.List;

import tornado.common.Tornado;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.AllocationPhase;

public class VirtualRegisterAllocationPhase extends AllocationPhase {

	@Override
	protected <B extends AbstractBlockBase<B>> void run(TargetDescription target,
			LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory) {
		
		Tornado.warn("virtual reg allocation: to implement!!!!");
		
		for(B block : linearScanOrder){
			Tornado.info("virt: block=%d",block.getId());
			for(LIRInstruction insn : lirGenRes.getLIR().getLIRforBlock(block)){
				Tornado.info("virt: %s",insn.toString());
			
//				if(insn instanceof OCLStmt){
//					OCLStmt stmt = (OCLStmt) insn;
//					if(stmt.getResult() instanceof AllocatableValue){
//						AllocatableValue value = (AllocatableValue) stmt.getResult();
//						
//					}
//				}
			
			}
		}
		
	}

}
