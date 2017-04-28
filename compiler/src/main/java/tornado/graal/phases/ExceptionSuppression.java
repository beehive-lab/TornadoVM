/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
