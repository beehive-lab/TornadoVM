package tornado.graal.phases;

import com.oracle.graal.nodes.GuardNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.extended.GuardedNode;
import com.oracle.graal.nodes.extended.ValueAnchorNode;
import com.oracle.graal.phases.BasePhase;
import java.util.HashSet;
import java.util.Set;

public class ExceptionSuppression extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        Set<LogicNode> conditions = new HashSet<>();
        Set<GuardNode> guards = new HashSet<>();

        graph.getNodes().filter(n -> n instanceof GuardedNode).forEach((node) -> {
            GuardedNode guardedNode = (GuardedNode) node;
            if (guardedNode.getGuard() instanceof GuardNode) {
                GuardNode guard = (GuardNode) guardedNode.getGuard();
                LogicNode condition = guard.getCondition();

                conditions.add(condition);
                guards.add(guard);
                guardedNode.setGuard(null);

            }
        });

        graph.getNodes().filter(ValueAnchorNode.class).forEach(anchor -> {
            if (anchor.getAnchoredNode() instanceof GuardNode) {
                final GuardNode guard = (GuardNode) anchor.getAnchoredNode();
                guards.add(guard);
                conditions.add(guard.getCondition());
                anchor.removeAnchoredNode();
            }
        });

        guards.forEach(guard -> {
            guard.clearInputs();
            guard.safeDelete();
//            graph.removeFloating(guard);
        });

        conditions.forEach(condition -> {
            condition.clearInputs();
            condition.safeDelete();
//            graph.removeFloating(condition);
        });

    }

}
