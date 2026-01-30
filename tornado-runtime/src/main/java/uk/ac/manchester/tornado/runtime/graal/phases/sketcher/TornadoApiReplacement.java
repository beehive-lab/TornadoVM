/*
 * Copyright (c) 2018, 2020, 2024 APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.graal.phases.sketcher;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.phases.BasePhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoCompilationException;
import uk.ac.manchester.tornado.runtime.ASMClassVisitorProvider;
import uk.ac.manchester.tornado.runtime.common.ParallelAnnotationProvider;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelOffsetNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelStrideNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoLoopsData;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoSketchTierContext;

public class TornadoApiReplacement extends BasePhase<TornadoSketchTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    /*
     * A singleton is used because we don't need to support all the logic of loading
     * the desired class bytecode and instantiating the helper classes for the ASM
     * library. Therefore, we use the singleton to call
     * ASMClassVisitor::getParallelAnnotations which will handle everything in the
     * right module. We can't have ASMClassVisitor::getParallelAnnotations be a
     * static method because we dynamically load the class and the interface does
     * not allow it.
     */
    private static ASMClassVisitorProvider asmClassVisitorProvider;

    static {
        try {
            String tornadoAnnotationImplementation = System.getProperty("tornado.load.annotation.implementation");
            if (tornadoAnnotationImplementation == null) {
                throw new RuntimeException("[ERROR] Tornado Annotation Implementation class not specified. Did you remember to add @tornado-argfile?");
            }
            Class<?> klass = Class.forName(tornadoAnnotationImplementation);
            Constructor<?> constructor = klass.getConstructor();
            asmClassVisitorProvider = (ASMClassVisitorProvider) constructor.newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException("[ERROR] Tornado Annotation Implementation class not found", e);
        }
    }

    @Override
    protected void run(StructuredGraph graph, TornadoSketchTierContext context) {
        replaceLocalAnnotations(graph, context);
    }

    private void replaceLocalAnnotations(StructuredGraph graph, TornadoSketchTierContext context) throws TornadoCompilationException {
        Map<Node, ParallelAnnotationProvider> parallelNodes = getAnnotatedNodes(graph, context);
        addParallelProcessingNodes(graph, parallelNodes, context.getDevice());
    }

    private Map<Node, ParallelAnnotationProvider> getAnnotatedNodes(StructuredGraph graph, TornadoSketchTierContext context) {
        Map<ResolvedJavaMethod, ParallelAnnotationProvider[]> methodToAnnotations = new HashMap<>();

        methodToAnnotations.put(context.getMethod(), asmClassVisitorProvider.getParallelAnnotations(context.getMethod()));

        for (ResolvedJavaMethod resolvedJavaMethod : graph.getMethods()) {
            ParallelAnnotationProvider[] inlineParallelAnnotations = asmClassVisitorProvider.getParallelAnnotations(resolvedJavaMethod);
            if (inlineParallelAnnotations.length > 0) {
                methodToAnnotations.put(resolvedJavaMethod, inlineParallelAnnotations);
            }
        }
        Map<Node, ParallelAnnotationProvider> parallelNodes = new HashMap<>();

        graph.getNodes().filter(FrameState.class).forEach(frameState -> {
            if (methodToAnnotations.containsKey(frameState.getMethod())) {
                for (ParallelAnnotationProvider annotation : methodToAnnotations.get(frameState.getMethod())) {
                    if (frameState.bci >= annotation.getStart() && frameState.bci < annotation.getStart() + annotation.getLength()) {
                        Node localNode = frameState.localAt(annotation.getIndex());
                        if (!parallelNodes.containsKey(localNode)) {
                            parallelNodes.put(localNode, annotation);
                        }
                    }
                }
            }
        });
        return parallelNodes;
    }

    private void addParallelProcessingNodes(StructuredGraph graph, Map<Node, ParallelAnnotationProvider> parallelNodes, TornadoDevice device) {
        if (graph.hasLoops()) {
            final LoopsData data = new TornadoLoopsData(graph);
            data.detectCountedLoops();
            int loopIndex = 0;
            final List<LoopEx> loops = data.outerFirst();

            // Enable loop interchange - Parallel Loops are processed in the IR reversed order
            // to set the ranges and offset of the corresponding <thread-ids> for each dimension.
            if (device.getDeviceType() != TornadoDeviceType.CPU && TornadoOptions.TORNADO_LOOP_INTERCHANGE) {
                Collections.reverse(loops);
            }

            for (LoopEx loop : loops) {
                for (InductionVariable iv : loop.getInductionVariables().getValues()) {
                    if (!parallelNodes.containsKey(iv.valueNode())) {
                        continue;
                    }
                    List<IntegerLessThanNode> conditions = iv.valueNode().usages().filter(IntegerLessThanNode.class).snapshot();
                    final IntegerLessThanNode lessThan = conditions.getFirst();
                    ValueNode maxIterations = lessThan.getY();
                    parallelizationReplacement(graph, iv, loopIndex, maxIterations, conditions);
                    loopIndex++;
                }
            }
        }
    }

    private void parallelizationReplacement(StructuredGraph graph, InductionVariable inductionVar, int loopIndex, ValueNode maxIterations, List<IntegerLessThanNode> conditions)
            throws TornadoCompilationException {
        if (inductionVar.isConstantInit() && inductionVar.isConstantStride()) {

            final ConstantNode newInit = graph.addWithoutUnique(ConstantNode.forInt((int) inductionVar.constantInit()));

            final ConstantNode newStride = graph.addWithoutUnique(ConstantNode.forInt((int) inductionVar.constantStride()));

            final ParallelOffsetNode offset = graph.addWithoutUnique(new ParallelOffsetNode(loopIndex, newInit));

            final ParallelStrideNode stride = graph.addWithoutUnique(new ParallelStrideNode(loopIndex, newStride));

            final ParallelRangeNode range = graph.addWithoutUnique(new ParallelRangeNode(loopIndex, maxIterations, offset, stride));

            final ValuePhiNode phi = (ValuePhiNode) inductionVar.valueNode();

            final ValueNode oldStride = phi.singleBackValueOrThis();

            if (oldStride.usages().count() > 1) {
                final ValueNode duplicateStride = (ValueNode) oldStride.copyWithInputs(true);
                oldStride.replaceAtMatchingUsages(duplicateStride, usage -> !usage.equals(phi));
            }

            inductionVar.initNode().replaceAtMatchingUsages(offset, node -> node.equals(phi));
            inductionVar.strideNode().replaceAtMatchingUsages(stride, node -> node.equals(oldStride));
            // only replace this node in the loop condition
            maxIterations.replaceAtMatchingUsages(range, node -> node.equals(conditions.getFirst()));

        } else {
            throw new TornadoBailoutRuntimeException("Failed to parallelize because of non-constant loop strides. \nSequential code will run on the device!");
        }
    }
}
