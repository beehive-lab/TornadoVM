/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, 2023 APT Group, Department of Computer Science,
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

import java.util.BitSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
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

    private void resize() {
        TornadoInternalError.unimplemented("Tornado Graph resize not implemented yet.");
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
