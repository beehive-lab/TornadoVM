package tornado.graal.phases;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.common.inlining.info.InlineInfo;
import com.oracle.graal.phases.common.inlining.policy.InliningPolicy;
import com.oracle.graal.phases.common.inlining.walker.MethodInvocation;

public class TornadoInliningPolicy implements InliningPolicy {

	@Override
	public boolean continueInlining(StructuredGraph graph) {
		if (graph.getNodeCount() >= MaximumDesiredSize.getValue()) {
            InliningUtil.logInliningDecision("inlining is cut off by MaximumDesiredSize");
            return false;
        }
        return true;
	}

	@Override
	public boolean isWorthInlining(Replacements replacements, MethodInvocation invocation,
			int inliningDepth, boolean fullyProcessed) {
		boolean doInline = true;
		
		final InlineInfo info = invocation.callee();
	    final double probability = invocation.probability();
	    final double relevance = invocation.relevance();

		int nodes = info.determineNodeCount();
		int methodCount = info.numberOfMethods();
		
		
		if(nodes > MaximumInliningSize.getValue() && !invocation.isRoot())
			doInline = false;
		
	//	System.out.printf("inliner: %s (%s) -> nodes=%d, count=%d\n",info.toString(),doInline, nodes,methodCount);
		
		
		return doInline;
	}

}
