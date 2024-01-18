/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023 APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.runtime.graph;

import java.util.Arrays;
import java.util.BitSet;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.ContextOpNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.DependentReadNode;
import uk.ac.manchester.tornado.runtime.graph.nodes.TaskNode;

/**
 * It represents an intermediate graph used during the dependency analysis of a
 * {@link TornadoGraph}. This class provides methods to analyze the intermediate
 * graph and retrieve information about the graph's dependencies .
 */
public class IntermediateTornadoGraph {
    private final TornadoGraph graph;
    private final BitSet asyncNodes;
    private final BitSet tasks;
    private BitSet[] dependencies;
    private int[] nodeIds;
    private int index;
    private int numberOfDependencies;

    /**
     * Constructs an IntermediateTornadoGraph with the specified asyncNodes BitSet.
     *
     * @param asyncNodes
     *     The {@link BitSet} representing the asynchronous nodes in the
     *     graph.
     *
     * @param graph
     *     The {@link TornadoGraph} to analyze.
     */
    public IntermediateTornadoGraph(BitSet asyncNodes, TornadoGraph graph) {
        this.graph = graph;
        this.asyncNodes = asyncNodes;
        this.dependencies = new BitSet[asyncNodes.cardinality()];
        this.tasks = new BitSet(asyncNodes.cardinality());
        this.nodeIds = new int[asyncNodes.cardinality()];
        this.index = 0;
        this.numberOfDependencies = 0;
    }

    public BitSet[] getDependencies() {
        return dependencies;
    }

    public BitSet getTasks() {
        return tasks;
    }

    public int[] getNodeIds() {
        return nodeIds;
    }

    public int getNumberOfDependencies() {
        return numberOfDependencies;
    }

    public void analyzeDependencies() {
        for (int i = asyncNodes.nextSetBit(0); i != -1 && i < asyncNodes.length(); i = asyncNodes.nextSetBit(i + 1)) {
            dependencies[index] = calculateDependencies(graph, i);
            nodeIds[index] = i;
            if (graph.getNode(i) instanceof TaskNode) {
                tasks.set(index);
            }
            if (!dependencies[index].isEmpty()) {
                numberOfDependencies++;
            }
            index++;
        }
    }

    private BitSet calculateDependencies(TornadoGraph graph, int i) {
        final BitSet dependencies = new BitSet(graph.getValid().length());
        final AbstractNode node = graph.getNode(i);
        for (AbstractNode input : node.getInputs()) {
            if (input instanceof ContextOpNode) {
                if (input instanceof DependentReadNode dependentReadNode) {
                    dependencies.set((dependentReadNode).getDependent().getId());
                } else {
                    dependencies.set(input.getId());
                }
            }
        }
        return dependencies;
    }

    public void printDependencyMatrix() {
        StringBuilder output = new StringBuilder();
        output.append("TornadoGraph dependency matrix...\n");

        int maxNodeId = Arrays.stream(nodeIds).max().getAsInt();
        int maxLabelLength = Integer.toString(maxNodeId).length();

        output.append("+" + "-".repeat(maxLabelLength + 2) + "+");
        output.append("-".repeat(15) + "+");
        output.append("\n");

        for (int i = 0; i < nodeIds.length; i++) {
            final int nodeId = nodeIds[i];
            final String label = String.format("%" + maxLabelLength + "d", nodeId);
            final String type = (tasks.get(i)) ? "task" : "data";

            output.append("| ").append(String.format("%-" + maxLabelLength + "s", label));
            output.append(" [").append(type).append("]| ").append(toString(dependencies[i])).append("\n");

            output.append("|").append("-".repeat(maxLabelLength + 2)).append("+");
            output.append("-".repeat(15)).append("+");
            output.append("\n");
        }

        System.out.println(output);
    }

    private String toString(BitSet set) {
        return set.isEmpty() ? "<none>" : set.stream().mapToObj(String::valueOf).collect(Collectors.joining(" "));
    }

}
