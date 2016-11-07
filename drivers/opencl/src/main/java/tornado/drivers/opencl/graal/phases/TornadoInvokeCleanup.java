package tornado.drivers.opencl.graal.phases;

import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.BasePhase;
import tornado.graal.phases.TornadoHighTierContext;

@Deprecated
public class TornadoInvokeCleanup extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        graph.getNodes().filter(InvokeWithExceptionNode.class).forEach(invoke -> {
            //System.out.printf("cleaning: %s\n",invoke);
//            final List<GuardingPiNode> guardingPis = invoke.usages().filter(GuardingPiNode.class).snapshot();
//
//            if (invoke.exceptionEdge() != null) {
//                invoke.killExceptionEdge();
//            }
//
//            AbstractBeginNode begin = invoke.next();
//            if (begin instanceof KillingBeginNode) {
//                AbstractBeginNode newBegin = new BeginNode();
//                graph.addAfterFixed(begin, graph.add(newBegin));
//                begin.replaceAtUsages(newBegin);
//                graph.removeFixed(begin);
//            }
        });
    }

}
