/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.runtime.graph;

import java.util.BitSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import tornado.runtime.graph.nodes.AbstractNode;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class Graph {

    private final static int INITIAL_SIZE = 256;

    private AbstractNode[] nodes;
    private BitSet valid;
    private int nextNode;

    public Graph() {
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
    public <T extends AbstractNode> T addUnique(T node) {
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
        unimplemented();
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

    public void papply(BitSet predicates, Consumer<AbstractNode> consumer) {
        for (int i = predicates.nextSetBit(0); i != -1 && i < predicates.length(); i = predicates.nextSetBit(i + 1)) {
            consumer.accept(nodes[i]);
        }
    }

    public void apply(Consumer<AbstractNode> consumer) {
        papply(valid, consumer);
    }

    public void print() {
        System.out.println("graph:");
        apply(System.out::println);
    }

    public BitSet getValid() {
        return valid;
    }
}
