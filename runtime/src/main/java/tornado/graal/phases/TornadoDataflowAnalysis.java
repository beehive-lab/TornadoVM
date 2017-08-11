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

import java.util.ArrayDeque;
import java.util.Queue;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.BasePhase;
import tornado.api.meta.TaskMetaData;
import tornado.common.enums.Access;

import static tornado.common.Tornado.debug;

public class TornadoDataflowAnalysis extends BasePhase<TornadoSketchTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        if (!context.hasMeta()) {
            return;
        }

        TaskMetaData meta = context.getMeta();
        Access[] accesses = meta.getArgumentsAccess();

        for (int i = 0; i < accesses.length; i++) {
            accesses[i] = Access.NONE;
            ParameterNode param = graph.getParameter(i);

            // Only interested in objects
            if (param != null && param.stamp() instanceof ObjectStamp) {
                accesses[i] = processUseages(param, context.getMetaAccess());
            }

            debug("access: parameter %d -> %s\n", i, accesses[i]);
        }

    }

    private Access processUseages(Node parameter, MetaAccessProvider metaAccess) {
//        NodeBitMap nodes = graph.createNodeBitMap();
//        nodes.clearAll();

        boolean isRead = false;
        boolean isWritten = false;

        Queue<Node> nf = new ArrayDeque<>();
        parameter.usages().forEach(nf::add);

        while (!nf.isEmpty()) {
            Node currentNode = nf.remove();
            if (currentNode instanceof LoadIndexedNode) {
                isRead = true;
                if (((ValueNode) currentNode).stamp().javaType(metaAccess).isArray()) {
                    nf.addAll(currentNode.usages().snapshot());
                }
            } else if (currentNode instanceof StoreIndexedNode) {
                isWritten = true;
            } else if (currentNode instanceof LoadFieldNode) {
                LoadFieldNode loadField = (LoadFieldNode) currentNode;
                if (loadField.stamp() instanceof ObjectStamp) {
                    loadField.usages().forEach(nf::add);
                }
                isRead = true;
            } else if (currentNode instanceof StoreFieldNode) {
                isWritten = true;
            } else if (currentNode instanceof PiNode) {
                currentNode.usages().forEach(nf::add);
            }
        }

        Access result = Access.NONE;
        if (isRead && isWritten) {
            result = Access.READ_WRITE;
        } else if (isRead) {
            result = Access.READ;
        } else if (isWritten) {
            result = Access.WRITE;
        }

        return result;
    }
}
