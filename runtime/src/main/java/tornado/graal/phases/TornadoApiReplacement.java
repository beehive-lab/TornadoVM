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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.vm.ci.meta.LocalAnnotation;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.InductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.BasePhase;
import tornado.api.Atomic;
import tornado.common.Tornado;
import tornado.graal.nodes.AtomicAccessNode;
import tornado.graal.nodes.ParallelOffsetNode;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.graal.nodes.ParallelStrideNode;

import static tornado.common.Tornado.TORNADO_LOOPS_REVERSE;

public class TornadoApiReplacement extends BasePhase<TornadoSketchTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        replaceParameterAnnotations(graph, context);
        replaceLocalAnnotations(graph, context);
    }

    private void replaceParameterAnnotations(StructuredGraph graph, TornadoSketchTierContext context) {
        final Annotation[][] parameterAnnotations = graph.method().getParameterAnnotations();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation an : parameterAnnotations[i]) {
//			sSystem.out.printf("annotation: param[%d]: %s\n",i,an);
                if (an instanceof Atomic) {

                    final ParameterNode param = graph.getParameter(i);
                    final AtomicAccessNode atomicAccess = graph.addOrUnique(new AtomicAccessNode(param));
                    param.replaceAtMatchingUsages(atomicAccess, usage -> usage instanceof StoreIndexedNode);
                }
            }
        }

    }

    private void replaceLocalAnnotations(StructuredGraph graph, TornadoSketchTierContext context) {

        // build node -> annotation mapping
        Map<ResolvedJavaMethod, LocalAnnotation[]> methodToAnnotations = new HashMap<>();

        methodToAnnotations.put(context.getMethod(), context.getMethod().getLocalAnnotations());

        for (ResolvedJavaMethod inlinee : graph.getMethods()) {

            if (inlinee.getLocalAnnotations().length > 0) {
                methodToAnnotations.put(inlinee,
                        inlinee.getLocalAnnotations());
            }
        }

        Map<Node, LocalAnnotation> parallelNodes = new HashMap<>();

        graph.getNodes().filter(FrameState.class).forEach((fs) -> {
            // Tornado.trace("framestate: method=%s,",fs.method().getName());
            if (methodToAnnotations.containsKey(fs.getMethod())) {
                for (LocalAnnotation an : methodToAnnotations.get(fs.getMethod())) {
                    if (fs.bci >= an.getStart() && fs.bci < an.getStart() + an.getLength()) {
                        Node localNode = fs.localAt(an.getIndex());

                        if (!parallelNodes.containsKey(localNode)) {
                            // Tornado.info("found parallel node: %s",localNode);
                            parallelNodes.put(localNode, an);
                        }
                    }
                }
            }
        });

        if (graph.hasLoops()) {

            final LoopsData data = new LoopsData(graph);
            data.detectedCountedLoops();

            int loopIndex = 0;
            final List<LoopEx> loops = data.outerFirst();
            if (TORNADO_LOOPS_REVERSE) {
                Collections.reverse(loops);
            }
//            final List<LoopEx> loops = (TORNADO_LOOPS_REVERSE) ? data.innerFirst() : data.outerFirst() Collections.reverse(loops);
            for (LoopEx loop : loops) {

                for (InductionVariable iv : loop.getInductionVariables().getValues()) {
                    if (!parallelNodes.containsKey(iv.valueNode())) {
                        continue;
                    }

                    ValueNode maxIterations = null;
                    List<IntegerLessThanNode> conditions = iv.valueNode().usages()
                            .filter(IntegerLessThanNode.class).snapshot();
                    if (conditions.size() == 1) {
                        final IntegerLessThanNode lessThan = conditions.get(0);
                        maxIterations = lessThan.getY();
                    } else {
                        Tornado.debug("Unable to parallelise: multiple uses of iv");
                        continue;
                    }

                    if (iv.isConstantInit() && iv.isConstantStride()) {

                        final ConstantNode newInit = graph.addWithoutUnique(ConstantNode
                                .forInt((int) iv.constantInit()));
                        final ConstantNode newStride = graph.addWithoutUnique(ConstantNode
                                .forInt((int) iv.constantStride()));

                        final ParallelOffsetNode offset = graph
                                .addWithoutUnique(new ParallelOffsetNode(loopIndex, newInit));

                        final ParallelStrideNode stride = graph
                                .addWithoutUnique(new ParallelStrideNode(loopIndex, newStride));

                        final ParallelRangeNode range = graph
                                .addWithoutUnique(new ParallelRangeNode(loopIndex, maxIterations,
                                        offset, stride));

                        final ValuePhiNode phi = (ValuePhiNode) iv.valueNode();
                        final ValueNode oldStride = phi.singleBackValueOrThis(); // was singleBackValue()

                        //System.out.printf("oldStride: %s\n",oldStride.toString());
                        if (oldStride.usages().count() > 1) {
                            final ValueNode duplicateStride = (ValueNode) oldStride.copyWithInputs(true);

                            oldStride.replaceAtMatchingUsages(duplicateStride, usage -> !usage.equals(phi));

                            //duplicateStride.removeUsage(phi);
                            //oldStride.removeUsage(node)
                        }

                        iv.initNode().replaceAtMatchingUsages(offset, node -> node.equals(phi));
                        iv.strideNode().replaceAtMatchingUsages(stride,
                                node -> node.equals(oldStride));

                        // only replace this node in the loop condition
                        maxIterations.replaceAtMatchingUsages(range, node -> node.equals(conditions.get(0)));

                    } else {
                        Tornado.debug("Unable to parallelise: non-constant stride or offset");
                        continue;
                    }
                    loopIndex++;
                }

            }
        }
    }
}
