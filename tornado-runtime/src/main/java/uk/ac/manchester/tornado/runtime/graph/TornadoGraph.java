/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023 APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

import uk.ac.manchester.tornado.runtime.graph.nodes.AbstractNode;

/**
 * The TornadoGraph class represents a graph data structure that stores nodes
 * based on Task-graphs relationships. It provides methods to add, retrieve,
 * delete, and filter nodes based on various criteria.
 */
public class TornadoGraph {

    private static final int INITIAL_SIZE = 1024;

    private AbstractNode[] nodes;
    private BitSet valid;
    private int nextNode;

    TornadoGraph() {
        nodes = new AbstractNode[INITIAL_SIZE];
        valid = new BitSet(INITIAL_SIZE);
        nextNode = 0;
    }

    public AbstractNode getNode(int index) {
        return nodes[index];
    }

    public void add(AbstractNode node) {
        if (nextNode >= nodes.length) {
            resize();
        }

        node.setId(nextNode);
        nodes[nextNode] = node;
        valid.set(nextNode);
        nextNode++;
    }

    @SuppressWarnings("unchecked")
    <T extends AbstractNode> T addUnique(T node) {
        for (int i = valid.nextSetBit(0); i != -1 && i < nodes.length; i = valid.nextSetBit(i + 1)) {
            if (nodes[i].compareTo(node) == 0) {
                return (T) nodes[i];
            }
        }
        add(node);
        return node;
    }

    public void delete(AbstractNode node) {
        valid.clear(node.getId());
        node.setId(-node.getId());
    }

    /** Doubles the node storage. The {@code valid} bit set grows on its own as bits are set. */
    private void resize() {
        nodes = Arrays.copyOf(nodes, 2 * nodes.length);
    }

    public <T extends AbstractNode> BitSet filter(Class<T> type) {
        final BitSet nodes = new BitSet(valid.length());
        apply((AbstractNode n) -> {
            if (n.getClass().equals(type)) {
                nodes.set(n.getId());
            }
        });
        return nodes;
    }

    public BitSet filter(Predicate<AbstractNode> test) {
        final BitSet nodes = new BitSet(valid.length());
        apply((AbstractNode n) -> {
            if (test.test(n)) {
                nodes.set(n.getId());
            }
        });
        return nodes;
    }

    private void pApply(BitSet predicates, Consumer<AbstractNode> consumer) {
        for (int i = predicates.nextSetBit(0); i != -1 && i < predicates.length(); i = predicates.nextSetBit(i + 1)) {
            consumer.accept(nodes[i]);
        }
    }

    public void apply(Consumer<AbstractNode> consumer) {
        pApply(valid, consumer);
    }

    public void dumpTornadoGraph() {
        final String ansiCyan = "\u001B[36m";
        final String ansiReset = "\u001B[0m";
        System.out.println("-----------------------------------");
        System.out.println(ansiCyan + "TaskGraph:" + ansiReset);
        apply(System.out::println);
        System.out.println("-----------------------------------");
    }

    public BitSet getValid() {
        return valid;
    }
}
