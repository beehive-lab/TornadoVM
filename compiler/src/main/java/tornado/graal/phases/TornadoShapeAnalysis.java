package tornado.graal.phases;

import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.phases.BasePhase;
import java.util.Collections;
import java.util.List;
import tornado.common.Tornado;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.meta.domain.DomainTree;
import tornado.meta.domain.IntDomain;

public class TornadoShapeAnalysis extends BasePhase<TornadoHighTierContext> {

	private static final int resolveInt(ValueNode value){
		if(value instanceof ConstantNode){
			return ((ConstantNode) value).asJavaConstant().asInt();
		} else {
			//TornadoInternalError.shouldNotReachHere();
			return Integer.MIN_VALUE;
		}
	}
	
	@Override
	protected void run(StructuredGraph graph, TornadoHighTierContext context) {
		
		if(!context.hasMeta()) return;
		
		final List<ParallelRangeNode> ranges = graph.getNodes().filter(ParallelRangeNode.class).snapshot();
		
		Collections.sort(ranges);
		
		final DomainTree domainTree = new DomainTree(ranges.size());
		
		int lastIndex = -1;
		boolean valid = true;
		for(int i=0;i<ranges.size();i++){
			final ParallelRangeNode range = ranges.get(i);
			final int index = range.index();
			if(index != lastIndex &&resolveInt(range.offset().value()) != Integer.MIN_VALUE &&resolveInt(range.stride().value()) != Integer.MIN_VALUE && resolveInt(range.value()) != Integer.MIN_VALUE ){
				domainTree.set(index, new IntDomain(resolveInt(range.offset().value()),resolveInt(range.stride().value()),resolveInt(range.value())));
			} else {
				valid = false;
				Tornado.info("unsupported multiple parallel loops");
				break;
			}
			lastIndex = index;
		}
		
		if(valid){
			Tornado.trace("loop nest depth = %d", domainTree.getDepth());

			Tornado.debug("discovered parallel domain: %s", domainTree);

			context.getMeta().setDomain(domainTree);
		}
		
	}

}
