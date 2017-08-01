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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.phases.BasePhase;
import java.util.HashMap;
import java.util.Map;
import jdk.vm.ci.meta.ResolvedJavaField;
import tornado.api.Vector;

import static tornado.graal.compiler.TornadoCodeGenerator.debug;

public class TornadoValueTypeReplacement extends BasePhase<TornadoHighTierContext> {

    private static final NodePredicate valueTypeFilter = new NodePredicate() {

        @Override
        public boolean apply(Node node) {
            return ((NewInstanceNode) node)
                    .instanceClass()
                    .getAnnotation(Vector.class) != null;
        }

    };

    private void simplify(NewInstanceNode newInstance) {
        debug("simplify: node=%s", newInstance.toString());

        /*
         * make dict
         */
        final Map<ResolvedJavaField, ValueNode> fieldToValue = new HashMap<>();
        newInstance.usages().filter(StoreFieldNode.class).forEach((store) -> {
            fieldToValue.put(store.field(), store.value());
            store.clearInputs();
            // store.clearSuccessors();
            // store.markDeleted();

            // store.replaceAtPredecessor(store.next());
            store.graph().removeFixed(store);
        });

        if (fieldToValue.isEmpty()) {
            return;
        }

        // print dict
        for (ResolvedJavaField field : fieldToValue.keySet()) {
            debug("simplify: field=%s -> value=%s", field.getName(), fieldToValue
                    .get(field).toString());
        }

        /*
         * contract fields
         */
        newInstance
                .graph()
                .getNodes()
                .filter(LoadFieldNode.class)
                .forEach(
                        (load) -> {
                            if (load.object() == newInstance) {
                                debug("simplify: load field=%s", load.field().getName());

                                debug("simplify: load field=%s -> value=%s", load.field()
                                        .getName(), fieldToValue.get(load.field()).toString());

                                load.replaceAtUsages(fieldToValue.get(load.field()));
                                // load.clearInputs();
                                // load.markDeleted();
                                // load.replaceAtPredecessor(load.next());
                                load.graph().removeFixed(load);

                            }
                        });
    }

    public void execute(StructuredGraph graph, TornadoHighTierContext context) {
        run(graph, context);
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        graph.getNodes().filter(NewInstanceNode.class).filter(valueTypeFilter)
                .forEach(this::simplify);
        graph.maybeCompress();
        // graph.getNodes().filter(VirtualArrayNode.class).filter(vectorizationFilter).forEach(this::vectorize);

    }

}
