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

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.IntegerBelowNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.extended.GuardedNode;
import com.oracle.graal.phases.BasePhase;

public class ExceptionCheckingElimination extends BasePhase<TornadoMidTierContext> {

    /**
     * Removes all exception checking - loop bounds and null checks
     */
    @Override
    protected void run(StructuredGraph graph, TornadoMidTierContext context) {

        graph.getNodes().filter(n -> n instanceof GuardedNode).snapshot().forEach((node) -> {
            GuardedNode guardedNode = (GuardedNode) node;
            if (guardedNode.getGuard() instanceof GuardNode) {
                GuardNode guard = (GuardNode) guardedNode.getGuard();

                LogicNode condition = guard.getCondition();

                if (condition instanceof IsNullNode) {
                    Node input = condition.inputs().first();

                    if (guard.isNegated()) {
                        condition.replaceFirstInput(input, LogicConstantNode.contradiction(graph));
                    } else {
                        condition.replaceFirstInput(input, LogicConstantNode.tautology(graph));
                    }

                } else if (condition instanceof IntegerBelowNode) {

                    ValueNode x = ((IntegerBelowNode) condition).getX();
                    condition.replaceFirstInput(x, graph.addOrUnique(ConstantNode.forInt(Integer.MAX_VALUE)));
                }
            }

        });

    }
}
