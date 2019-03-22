/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
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

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.BasicInductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.StructuredGraph;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.graal.nodes.ParallelRangeNode;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraphAssembler.TornadoVMBytecodes;
import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextOpNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DependentReadNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.TaskNode;
import uk.ac.manchester.tornado.runtime.sketcher.Sketch;
import uk.ac.manchester.tornado.runtime.sketcher.TornadoSketcher;
import uk.ac.manchester.tornado.runtime.tasks.CompilableTask;

public class TornadoVMGraphCompiler {

    /**
     * Generate TornadoVM byte-code from a Tornado Task Graph.
     * 
     * @param graph
     * @param context
     * @return {@link TornadoVMGraphCompilationResult}
     */
    public static TornadoVMGraphCompilationResult compile(TornadoGraph graph, TornadoExecutionContext context, long batchSize) {
        final BitSet deviceContexts = graph.filter(ContextNode.class);
        if (deviceContexts.cardinality() == 1) {
            final ContextNode contextNode = (ContextNode) graph.getNode(deviceContexts.nextSetBit(0));
            int deviceIndex = contextNode.getDeviceIndex();
            return compileSingleContext(graph, context, context.getDevice(deviceIndex), batchSize);
        } else {
            throw new RuntimeException("Multiple-Contexts are not currently supported");
        }
    }

    /*
     * Simplest case where all tasks are executed on the same device
     */
    private static TornadoVMGraphCompilationResult compileSingleContext(TornadoGraph graph, TornadoExecutionContext context, TornadoAcceleratorDevice device, long batchSize) {

        final TornadoVMGraphCompilationResult result = new TornadoVMGraphCompilationResult();

        final BitSet asyncNodes = graph.filter((AbstractNode n) -> n instanceof ContextOpNode);

        final BitSet[] dependencies = new BitSet[asyncNodes.cardinality()];
        final BitSet tasks = new BitSet(asyncNodes.cardinality());
        final int[] nodeIds = new int[asyncNodes.cardinality()];
        int index = 0;
        int numDepLists = 0;
        for (int i = asyncNodes.nextSetBit(0); i != -1 && i < asyncNodes.length(); i = asyncNodes.nextSetBit(i + 1)) {
            dependencies[index] = calculateDeps(graph, context, i);
            nodeIds[index] = i;

            if (graph.getNode(i) instanceof TaskNode) {
                tasks.set(index);
            }

            if (!dependencies[index].isEmpty()) {
                numDepLists++;
            }
            index++;
        }

        // Generate begin
        result.begin(1, tasks.cardinality(), numDepLists + 1);

        // Generate all byte-codes for the input tasks
        scheduleAndEmitTornadoVMBytecodes(result, graph, nodeIds, dependencies);

        if (batchSize != -1) {
            // Process in batches
            // how many of these? => ( inputSize / batchSize )
            scheduleAndEmitTornadoVMBytecodes(result, graph, nodeIds, dependencies);
        }

        // Last operation -> perform synchronisation
        synchronizeOperationLastByteCode(result, numDepLists);

        // End
        result.end();

        return result;
    }

    /**
     * It replaces the last STREAM_OUT for STREAM_OUT_BLOCKING byte-code.
     * Otherwise, it adds a barrier
     * 
     * @param result
     * @param numDepLists
     */
    private static void synchronizeOperationLastByteCode(TornadoVMGraphCompilationResult result, int numDepLists) {
        final byte[] code = result.getCode();
        final int codeSize = result.getCodeSize();
        if (code[codeSize - 13] == TornadoVMBytecodes.STREAM_OUT.index()) {
            code[codeSize - 13] = TornadoVMBytecodes.STREAM_OUT_BLOCKING.index();
        } else {
            result.barrier(numDepLists);
        }
    }

    @SuppressWarnings("unused")
    private static void optimise(TornadoVMGraphCompilationResult result, TornadoGraph graph, TornadoExecutionContext context, int[] nodeIds, BitSet[] deps, BitSet tasks) {
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
                ResolvedJavaMethod rm1 = TornadoCoreRuntime.getTornadoRuntime().getMetaAccess().lookupJavaMethod(t1.getMethod());
                ResolvedJavaMethod rm2 = TornadoCoreRuntime.getTornadoRuntime().getMetaAccess().lookupJavaMethod(t1.getMethod());
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
                        System.out.printf("[%d] parallel loop: %s -> init=%s, cond=%s, stride=%s, op=%s\n", parRange.index(), loop.loopBegin(), parRange.offset().value(), parRange.value(),
                                parRange.stride(), iv.getOp());
                    }
                }
            }
        }
    }

    private static void scheduleAndEmitTornadoVMBytecodes(TornadoVMGraphCompilationResult result, TornadoGraph graph, int[] nodeIds, BitSet[] deps) {

        final BitSet scheduled = new BitSet(deps.length);
        scheduled.clear();
        final BitSet nodes = new BitSet(graph.getValid().length());
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
            for (int i = 0; i < deps.length; i++) {
                if (!scheduled.get(i)) {
                    final BitSet outstandingDeps = new BitSet(nodes.length());
                    outstandingDeps.or(deps[i]);
                    outstandingDeps.andNot(nodes);

                    if (outstandingDeps.isEmpty()) {
                        final ContextOpNode asyncNode = (ContextOpNode) graph.getNode(nodeIds[i]);

                        result.emitAsyncNode(asyncNode, asyncNode.getContext().getDeviceIndex(), (deps[i].isEmpty()) ? -1 : depLists[i]);

                        for (int j = 0; j < deps.length; j++) {
                            if (j == i) {
                                continue;
                            }
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

    private static void printMatrix(TornadoGraph graph, int[] nodeIds, BitSet[] deps, BitSet tasks) {
        System.out.println("dependency matrix...");
        for (int i = 0; i < nodeIds.length; i++) {
            final int nodeId = nodeIds[i];
            System.out.printf("%d [%s]| %s\n", nodeId, (tasks.get(i)) ? "task" : "data", toString(deps[i]));
        }
    }

    private static BitSet calculateDeps(TornadoGraph graph, TornadoExecutionContext context, int i) {
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
