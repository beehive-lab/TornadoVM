/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;
import static uk.ac.manchester.tornado.runtime.graph.GraphAssembler.STREAM_OUT;
import static uk.ac.manchester.tornado.runtime.graph.GraphAssembler.STREAM_OUT_BLOCKING;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.BasicInductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.StructuredGraph;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.common.SchedulableTask;
import uk.ac.manchester.tornado.common.TornadoDevice;
import uk.ac.manchester.tornado.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.api.CompilableTask;
import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextOpNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DependentReadNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.TaskNode;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;

public class GraphCompiler {

	/**
	 * Generate Tornado bytecode from a Torando Task Graph.
	 * 
	 * @param graph
	 * @param context
	 * @return {@link GraphCompilationResult}}
	 */
    public static GraphCompilationResult compile(Graph graph, ExecutionContext context) {
        final BitSet deviceContexts = graph.filter(ContextNode.class);
        if (deviceContexts.cardinality() == 1) {
            final ContextNode contextNode = (ContextNode) graph.getNode(deviceContexts.nextSetBit(0));
            int deviceIndex = contextNode.getDeviceIndex();
            return compileSingleContext(graph, context, context.getDevice(deviceIndex));
        } else {
        	throw new RuntimeException("Multiple context not supported");
        }
    }

    /*
     * Simplest case where all tasks are executed on the same device
     */
    private static GraphCompilationResult compileSingleContext(Graph graph, ExecutionContext context,
            TornadoDevice device) {

        final GraphCompilationResult result = new GraphCompilationResult();

        final BitSet asyncNodes = graph.filter((AbstractNode n) -> n instanceof ContextOpNode);

//        System.out.printf("found: [%s]\n", toString(asyncNodes));
        final BitSet[] deps = new BitSet[asyncNodes.cardinality()];
        final BitSet tasks = new BitSet(asyncNodes.cardinality());
        final int[] nodeIds = new int[asyncNodes.cardinality()];
        int index = 0;
        int numDepLists = 0;
        for (int i = asyncNodes.nextSetBit(0); i != -1 && i < asyncNodes.length(); i = asyncNodes.nextSetBit(i + 1)) {
            deps[index] = calculateDeps(graph, context, i);
            nodeIds[index] = i;

            if (graph.getNode(i) instanceof TaskNode) {
                tasks.set(index);
                final TaskNode taskNode = (TaskNode) graph.getNode(i);
                final SchedulableTask task = context.getTask(taskNode.getTaskIndex());
//                System.out.printf("node: %s %s\n", task.getName(), taskNode);
            } else {
//                System.out.printf("node: %s\n", graph.getNode(i));
            }

            if (!deps[index].isEmpty()) {
                numDepLists++;
            }
            index++;
        }

        result.begin(1, tasks.cardinality(), numDepLists + 1);

        schedule(result, graph, context, nodeIds, deps, tasks);
//        optimise(result, graph, context, nodeIds, deps, tasks);
        peephole(result, numDepLists);

        result.end();

//        result.dump();
        return result;
    }

    private static void peephole(GraphCompilationResult result, int numDepLists) {
        final byte[] code = result.getCode();
        final int codeSize = result.getCodeSize();

        if (code[codeSize - 13] == STREAM_OUT) {
            code[codeSize - 13] = STREAM_OUT_BLOCKING;
        } else {
            result.barrier(numDepLists);
        }
    }

    @SuppressWarnings("unused")
	private static void optimise(GraphCompilationResult result, Graph graph, ExecutionContext context,
            int[] nodeIds, BitSet[] deps, BitSet tasks) {
        printMatrix(graph, nodeIds, deps, tasks);
        for (int i = tasks.nextSetBit(0); i >= 0; i = tasks.nextSetBit(i + 1)) {
            BitSet dependents = new BitSet(deps[i].length());
            for (int j = deps[i].nextSetBit(0); j >= 0; j = deps[i].nextSetBit(j + 1)) {
                if (graph.getNode(j) instanceof TaskNode) {
                    dependents.set(j);

                }
            }
            if (!dependents.isEmpty()) {
                TaskNode dependentTask = (TaskNode) graph.getNode(nodeIds[i]);
                int firstDepId = dependents.nextSetBit(0);
                TaskNode firstTask = (TaskNode) graph.getNode(firstDepId);
                int[] argMerges = new int[dependentTask.getNumArgs()];
                Arrays.fill(argMerges, -1);
                for (int arg = 0; arg < dependentTask.getInputs().size(); arg++) {
                    AbstractNode n = dependentTask.getInputs().get(arg);
                    if (n instanceof DependentReadNode && ((DependentReadNode) n).getDependent() == firstTask) {
                        argMerges[arg] = 1;
                    }
                }

                CompilableTask t1 = (CompilableTask) context.getTask(firstTask.getTaskIndex());
                CompilableTask t2 = (CompilableTask) context.getTask(dependentTask.getTaskIndex());
                ResolvedJavaMethod rm1 = getTornadoRuntime().getMetaAccess().lookupJavaMethod(t1.getMethod());
                ResolvedJavaMethod rm2 = getTornadoRuntime().getMetaAccess().lookupJavaMethod(t1.getMethod());
                Sketch sketch1 = TornadoSketcher.lookup(rm1);
                Sketch sketch2 = TornadoSketcher.lookup(rm2);
                StructuredGraph g1 = (StructuredGraph) sketch1.getGraph().getReadonlyCopy();
                StructuredGraph g2 = (StructuredGraph) sketch2.getGraph().getReadonlyCopy();
                System.out.printf("dependent task: %s on %s merges = %d\n", t1.getId(), t2.getId(), Arrays.toString(argMerges));
                printIvs(g1);
                printIvs(g2);
                StructuredGraph g3 = TornadoTaskUtil.merge(t1, t2, g1, g2, argMerges);
            }
        }
    }

    private static void printIvs(StructuredGraph graph) {

        final LoopsData data = new LoopsData(graph);
        data.detectedCountedLoops();

        final List<LoopEx> loops = data.outerFirst();

        List<ParallelRangeNode> parRanges = graph.getNodes().filter(ParallelRangeNode.class).snapshot();
        for (LoopEx loop : loops) {
            for (ParallelRangeNode parRange : parRanges) {
                for (Node n : parRange.offset().usages()) {
                    if (loop.getInductionVariables().containsKey(n)) {
                        BasicInductionVariable iv = (BasicInductionVariable) loop.getInductionVariables().get(n);
                        System.out.printf("[%d] parallel loop: %s -> init=%s, cond=%s, stride=%s, op=%s\n", parRange.index(), loop.loopBegin(), parRange.offset().value(), parRange.value(), parRange.stride(), iv.getOp());
                    }
                }
            }
        }
    }

    private static void schedule(GraphCompilationResult result, Graph graph, ExecutionContext context,
            int[] nodeIds, BitSet[] deps, BitSet tasks) {

        final BitSet scheduled = new BitSet(deps.length);
        scheduled.clear();
        final BitSet nodes = new BitSet(graph.getValid().length());

//        System.out.println("----- event lists ------");
        final int[] depLists = new int[deps.length];
        Arrays.fill(depLists, -1);

        int index = 0;
        for (int i = 0; i < deps.length; i++) {
            if (!deps[i].isEmpty()) {

                final AbstractNode current = graph.getNode(nodeIds[i]);
                if (current instanceof DependentReadNode) {
                    continue;
                }

                depLists[i] = index;
                index++;
            }
        }

        while (scheduled.cardinality() < deps.length) {
//            System.out.printf("nodes: %s\n", toString(nodes));
//            System.out.printf("scheduled: %s\n", toString(scheduled));
            for (int i = 0; i < deps.length; i++) {
                if (!scheduled.get(i)) {

                    final BitSet outstandingDeps = new BitSet(nodes.length());
                    outstandingDeps.or(deps[i]);
                    outstandingDeps.andNot(nodes);

//                    System.out.printf("trying: %d - %s\n",nodeIds[i],toString(outstandingDeps));
                    if (outstandingDeps.isEmpty()) {
                        final ContextOpNode asyncNode = (ContextOpNode) graph.getNode(nodeIds[i]);

                        result.emitAsyncNode(
                                graph,
                                context,
                                asyncNode,
                                asyncNode.getContext().getDeviceIndex(),
                                (deps[i].isEmpty()) ? -1 : depLists[i]);

                        for (int j = 0; j < deps.length; j++) {
                            if (j == i) {
                                continue;
                            }
//						System.out.printf("checking: %d - %s\n",nodeIds[j],toString(deps[j]));
                            if (deps[j].get(nodeIds[i]) && depLists[j] != -1) {
                                result.emitAddDep(depLists[j]);
                            }
                        }
                        scheduled.set(i);
                        nodes.set(nodeIds[i]);
                    }
                }
            }
        }

    }

    private static String toString(BitSet set) {
        if (set.isEmpty()) {
            return "<none>";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = set.nextSetBit(0); i != -1 && i < set.length(); i = set.nextSetBit(i + 1)) {
            sb.append("" + i + " ");
        }
        return sb.toString();
    }

    private static void printMatrix(Graph graph, int[] nodeIds, BitSet[] deps,
            BitSet tasks) {

        System.out.println("dependency matrix...");
        for (int i = 0; i < nodeIds.length; i++) {
            final int nodeId = nodeIds[i];
            System.out.printf("%d [%s]| %s\n", nodeId, (tasks.get(i)) ? "task" : "data", toString(deps[i]));
        }

    }

    private static BitSet calculateDeps(Graph graph, ExecutionContext context, int i) {
        final BitSet deps = new BitSet(graph.getValid().length());

        final AbstractNode node = graph.getNode(i);
        for (AbstractNode input : node.getInputs()) {
            if (input instanceof ContextOpNode) {
                if (input instanceof DependentReadNode) {
                    deps.set(((DependentReadNode) input).getDependent().getId());
                } else {
                    deps.set(input.getId());
                }
            }
        }

        return deps;
    }

}
