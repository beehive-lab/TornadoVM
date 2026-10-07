/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.drivers.common.compiler.phases.loops;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jdk.vm.ci.meta.JavaKind;
import tornado.graal.compiler.core.common.type.Stamp;
import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.AbstractEndNode;
import tornado.graal.compiler.nodes.ConstantNode;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.GraphState;
import tornado.graal.compiler.nodes.LoopBeginNode;
import tornado.graal.compiler.nodes.LoopExitNode;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.ParameterNode;
import tornado.graal.compiler.nodes.PhiNode;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.ValuePhiNode;
import tornado.graal.compiler.nodes.ValueProxyNode;
import tornado.graal.compiler.nodes.calc.BinaryNode;
import tornado.graal.compiler.nodes.extended.JavaReadNode;
import tornado.graal.compiler.nodes.java.LoadIndexedNode;
import tornado.graal.compiler.nodes.util.GraphUtil;
import tornado.graal.compiler.phases.BasePhase;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.analyzer.ReductionLoops;
import uk.ac.manchester.tornado.runtime.graal.nodes.StoreAtomicIndexedNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceAddNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.TornadoReduceMulNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.WriteAtomicNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkIntrinsicsNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

/**
 * Moves the work-group reduction of a parallel reduction out of its loop, so that every thread
 * takes part in it, including threads that have no iteration.
 *
 * <p>
 * A reduction {@code for (@Parallel int i = s; i < n; i++) r[0] = op(r[0], f(i))} is compiled to a
 * loop whose body ends with the work-group reduction (local-memory store, barriers, tree
 * reduction). A thread with no iteration never enters the loop, so it does not write its slot of
 * local memory and skips the barriers. The runtime therefore had to launch exactly one thread per
 * iteration, splitting any non-power-of-two count between the device and the host.
 * </p>
 *
 * <p>
 * For a task marked by the runtime ({@code getReductionPaddedThreads() > 0}, launched on the next
 * power of two), this phase rewrites the loop to accumulate in a register: {@code acc} starts at the
 * operation's neutral element, each iteration computes {@code acc = op(acc, f(i))} instead of reading
 * {@code r[0]}, and the reduction store, with {@code acc} as its value, is placed after the loop
 * exit, where every thread reaches it. Threads with no iteration contribute the neutral element.
 * </p>
 */
public class TornadoReductionAccumulation extends BasePhase<TornadoHighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        if (context.getMeta() == null || context.getMeta().getReductionPaddedThreads() <= 0) {
            return;
        }
        List<FixedWithNextNode> stores = graph.getNodes().filter(n -> n instanceof StoreAtomicIndexedNode || n instanceof WriteAtomicNode).snapshot().stream().map(n -> (FixedWithNextNode) n).toList();
        if (stores.isEmpty()) {
            throw unsupported(graph, "no reduction store found");
        }
        for (FixedWithNextNode store : stores) {
            accumulateInRegister(graph, store);
        }
    }

    private static TornadoRuntimeException unsupported(StructuredGraph graph, String reason) {
        return new TornadoRuntimeException("[ERROR] Reduction in " + graph.method().format("%H.%n") + " cannot be launched on a padded grid: " + reason);
    }

    private static void accumulateInRegister(StructuredGraph graph, FixedWithNextNode store) {
        final boolean indexed = store instanceof StoreAtomicIndexedNode;
        final ValueNode value = indexed ? ((StoreAtomicIndexedNode) store).value() : ((WriteAtomicNode) store).value();
        final JavaKind kind = indexed ? ((StoreAtomicIndexedNode) store).elementKind() : ((WriteAtomicNode) store).getElementKind();

        final LoopBeginNode loop = enclosingLoop(graph, store);
        if (loop.loopExits().count() != 1) {
            throw unsupported(graph, "the reduction loop has " + loop.loopExits().count() + " exits");
        }
        final LoopExitNode exit = loop.loopExits().first();

        if (!(value instanceof BinaryNode operation)) {
            throw unsupported(graph, "unsupported reduction value " + value);
        }
        final ValueNode neutral = neutralElement(graph, operation, kind);
        final ValueNode accumulatorRead = readOfReductionLocation(store, operation);
        if (accumulatorRead == null) {
            throw unsupported(graph, "the read of the reduction variable was not found in " + value);
        }

        // acc = phi(neutral, op(acc, f(i))): the operation now combines the register, not r[0].
        final Stamp stamp = value.stamp(NodeView.DEFAULT).unrestricted();
        final ValueNode[] phiValues = new ValueNode[loop.phiPredecessorCount()];
        for (AbstractEndNode end : loop.forwardEnds()) {
            phiValues[loop.phiPredecessorIndex(end)] = neutral;
        }
        for (AbstractEndNode end : loop.loopEnds()) {
            phiValues[loop.phiPredecessorIndex(end)] = value;
        }
        final ValuePhiNode accumulator = graph.addWithoutUnique(new ValuePhiNode(stamp, loop, phiValues));
        // The read may sit anywhere in the expression; other users of it are the store itself,
        // which is rewired below.
        for (Node usage : accumulatorRead.usages().snapshot()) {
            if (usage != store) {
                usage.replaceAllInputs(accumulatorRead, accumulator);
            }
        }

        // After the loop: the store reduces acc. Its value keeps the operation's node type, which
        // selects the snippet, and the carried value is acc.
        final ValueProxyNode accumulatorAtExit = graph.addOrUnique(new ValueProxyNode(accumulator, exit));
        final BinaryNode operationType = (BinaryNode) operation.copyWithInputs(true);
        operationType.setX(accumulatorAtExit);
        operationType.setY(neutral);

        store.replaceFirstInput(value, operationType);
        final ValueNode oldAccumulatorInput = indexed ? ((StoreAtomicIndexedNode) store).getAccumulator() : ((WriteAtomicNode) store).getAccumulator();
        store.replaceFirstInput(oldAccumulatorInput, accumulatorAtExit);
        if (indexed) {
            ((StoreAtomicIndexedNode) store).setOptionalOperation(accumulatorAtExit);
        } else {
            ((WriteAtomicNode) store).setOptionalOperation(accumulatorAtExit);
        }

        GraphUtil.unlinkFixedNode(store);
        graph.addAfterFixed(exit, store);

        if (accumulatorRead.hasNoUsages() && accumulatorRead instanceof FixedWithNextNode fixedRead) {
            graph.removeFixed(fixedRead);
        }
    }

    /** The loop in which {@code store} runs on every iteration (see {@link ReductionLoops}). */
    private static LoopBeginNode enclosingLoop(StructuredGraph graph, FixedWithNextNode store) {
        LoopBeginNode loop = ReductionLoops.loopRunningOnEveryIteration(graph, store);
        if (loop == null) {
            throw unsupported(graph, "the reduction store does not run on every iteration of a single loop");
        }
        return loop;
    }

    /**
     * The read of the location the reduction store writes, anywhere in the expression that computes
     * the stored value (e.g. {@code r[0] + a[i] + f(i)} is {@code (r[0] + a[i]) + f(i)}). Replacing it
     * with the accumulator keeps the result, since the supported operations are associative. Returns
     * null unless there is exactly one such read.
     */
    private static ValueNode readOfReductionLocation(FixedWithNextNode store, BinaryNode operation) {
        ValueNode found = null;
        ArrayDeque<ValueNode> pending = new ArrayDeque<>();
        Set<Node> visited = new HashSet<>();
        pending.push(operation);
        while (!pending.isEmpty()) {
            ValueNode node = pending.pop();
            if (!visited.add(node) || node instanceof PhiNode || node instanceof ParameterNode || node instanceof ConstantNode) {
                continue;
            }
            if (readsReductionLocation(store, node)) {
                if (found != null) {
                    return null;
                }
                found = node;
                continue;
            }
            for (Node input : node.inputs()) {
                if (input instanceof ValueNode value) {
                    pending.push(value);
                }
            }
        }
        return found;
    }

    private static boolean readsReductionLocation(FixedWithNextNode store, ValueNode node) {
        if (store instanceof StoreAtomicIndexedNode indexed && node instanceof LoadIndexedNode load) {
            return load.array() == indexed.array() && load.index() == indexed.index();
        }
        return store instanceof WriteAtomicNode write && node instanceof JavaReadNode read && read.getAddress() == write.getAddress();
    }

    private static ValueNode neutralElement(StructuredGraph graph, BinaryNode operation, JavaKind kind) {
        final String op;
        if (operation instanceof TornadoReduceAddNode) {
            op = "ADD";
        } else if (operation instanceof TornadoReduceMulNode) {
            op = "MUL";
        } else if (operation instanceof MarkIntrinsicsNode intrinsic) {
            op = intrinsic.getOperation();
        } else {
            throw unsupported(graph, "unsupported reduction operation " + operation);
        }
        return switch (op) {
            case "ADD" -> constant(graph, kind, 0, 0);
            case "MUL" -> constant(graph, kind, 1, 1);
            case "MAX", "FMAX" -> constant(graph, kind, kind == JavaKind.Int ? Integer.MIN_VALUE : Long.MIN_VALUE, Double.NEGATIVE_INFINITY);
            case "MIN", "FMIN" -> constant(graph, kind, kind == JavaKind.Int ? Integer.MAX_VALUE : Long.MAX_VALUE, Double.POSITIVE_INFINITY);
            default -> throw unsupported(graph, "unsupported reduction operation " + op);
        };
    }

    private static ValueNode constant(StructuredGraph graph, JavaKind kind, long integral, double floating) {
        return switch (kind) {
            case Int -> ConstantNode.forInt((int) integral, graph);
            case Long -> ConstantNode.forLong(integral, graph);
            case Float -> ConstantNode.forFloat((float) floating, graph);
            case Double -> ConstantNode.forDouble(floating, graph);
            default -> throw new TornadoRuntimeException("[ERROR] Unsupported reduction kind " + kind);
        };
    }
}
