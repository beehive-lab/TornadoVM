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
package tornado.runtime.graph;

import java.util.BitSet;
import tornado.runtime.graph.nodes.*;

public class GraphCompiler {

    public static GraphCompilationResult compile(Graph graph, ExecutionContext context) {

        final BitSet deviceContexts = graph.filter(DeviceNode.class);
//        if (deviceContexts.cardinality() == 1) {
        return compileSingleContext(graph, context);
//        } else {

//        }
//        return null;
    }

    /*
     * Simplest case where all tasks are executed on the same device
     */
    private static GraphCompilationResult compileSingleContext(Graph graph, ExecutionContext context) {

        final GraphCompilationResult result = new GraphCompilationResult();

        final BitSet asyncNodes = graph.filter((AbstractNode n) -> n instanceof FixedDeviceOpNode || n instanceof FloatingDeviceOpNode);
        final BitSet taskNodes = graph.filter(TaskNode.class);
        final BitSet deviceNodes = graph.filter(DeviceNode.class);
        final BitSet parameterNodes = graph.filter(ParameterNode.class);

//        System.out.printf("found: [%s]\n", toString(asyncNodes));
//        final BitSet[] deps = new BitSet[asyncNodes.cardinality()];
//        final BitSet tasks = new BitSet(asyncNodes.cardinality());
//        final int[] nodeIds = new int[asyncNodes.cardinality()];
//        int index = 0;
//        int numDepLists = 0;
//        for (int i = asyncNodes.nextSetBit(0); i != -1 && i < asyncNodes.length(); i = asyncNodes.nextSetBit(i + 1)) {
//            deps[index] = calculateDeps(graph, context, i);
//            nodeIds[index] = i;
//
//            if (graph.getNode(i) instanceof TaskNode) {
//                tasks.set(index);
//                final TaskNode taskNode = (TaskNode) graph.getNode(i);
//                final SchedulableTask task = context.getTask(taskNode.getTaskIndex());
////                System.out.printf("node: %s %s\n", task.getName(), taskNode);
//            } else {
////                System.out.printf("node: %s\n", graph.getNode(i));
//            }
//
//            if (!deps[index].isEmpty()) {
//                numDepLists++;
//            }
//            index++;
//        }
//        printMatrix(graph, nodeIds, deps, tasks);
        result.setup(deviceNodes.cardinality(), taskNodes.cardinality(), asyncNodes.cardinality() + 1);

        schedule(result, graph, context);

        emitPostfetches(result, graph, context, parameterNodes);
        //        peephole(result, numDepLists);
        result.end();

//        result.dump();
        return result;
    }

    private static void emitPostfetches(GraphCompilationResult result, Graph graph, ExecutionContext context, BitSet parameterNodes) {
        for (int i = parameterNodes.nextSetBit(0); i >= 0; i = parameterNodes.nextSetBit(i + 1)) {
            final ParameterNode param = (ParameterNode) graph.getNode(i);
            result.postfetch(param.getIndex());
        }
    }

    private static void peephole(GraphCompilationResult result, int numDepLists) {
//        final byte[] code = result.getCode();
//        final int codeSize = result.getCodeSize();
//
//        if (code[codeSize - 13] == WRITE_HOST) {
//            code[codeSize - 13] = WRITE_HOST_BLOCKING;
//        } else {
//            result.barrier(numDepLists);
//        }
    }

    private static void schedule(GraphCompilationResult result, Graph graph, ExecutionContext context) {
        final BitSet valid = graph.getValid();
        final BitSet toSchedule = new BitSet(valid.size());
        toSchedule.or(valid);

        // prune psuedo-nodes (parameters)
        final BitSet parameterNodes = graph.filter(ParameterNode.class);
        toSchedule.andNot(parameterNodes);

        FixedNode currentNode = graph.getBeginNode();
        while (!toSchedule.isEmpty()) {
            emitNode(result, toSchedule, currentNode);

            if (currentNode instanceof FixedDeviceOpNode) {
                final DeviceNode device = ((FixedDeviceOpNode) currentNode).getDevice();
                if (toSchedule.get(device.getId())) {
                    toSchedule.clear(device.getId());
                }
            }

            currentNode = currentNode.getNext();
        }

    }

    private static void emitNode(GraphCompilationResult result, BitSet toSchedule, AbstractNode node) {
        for (AbstractNode input : node.getInputs()) {
            if (toSchedule.get(input.getId())) {
                emitNode(result, toSchedule, input);
            }
        }
        node.emit(result);
        toSchedule.clear(node.getId());
    }

    private static String toString(BitSet set) {
        if (set.isEmpty()) {
            return "<none>";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = set.nextSetBit(0); i != -1 && i < set.length(); i = set.nextSetBit(i + 1)) {
            sb.append("").append(i).append(" ");
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
            if (input instanceof FixedDeviceOpNode) {
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
