/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.graal.phases;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.logic.LogicalNotNode;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.logic.LogicalOrNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoMidTierContext;

public class TornadoIfCanonicalization extends BasePhase<TornadoMidTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoMidTierContext context) {
        graph.getNodes(IfNode.TYPE).forEach(ifNode -> canonicalize(graph, ifNode));
    }

    private boolean isMerge(AbstractBeginNode begin) {
        return (begin.next() instanceof AbstractEndNode && ((AbstractEndNode) begin.next()).merge() instanceof AbstractMergeNode);
    }

    private boolean isIf(EndNode end) {
        return end.predecessor() instanceof BeginNode && end.predecessor().predecessor() instanceof IfNode;
    }

    private boolean isIf(LoopEndNode end) {
        return end.predecessor() instanceof BeginNode && end.predecessor().predecessor() instanceof IfNode;
    }

    private IfNode getIf(EndNode end) {
        return (IfNode) end.predecessor().predecessor();
    }

    private IfNode getIf(LoopEndNode end) {
        return (IfNode) end.predecessor().predecessor();
    }

    private boolean getBranchTaken(IfNode ifNode, EndNode end) {
        return (ifNode.trueSuccessor().next().equals(end));
    }

    private boolean getBranchTaken(IfNode ifNode, LoopEndNode end) {
        return (ifNode.trueSuccessor().next().equals(end));
    }

    private AbstractMergeNode getMerge(AbstractBeginNode begin) {
        return (AbstractMergeNode) ((AbstractEndNode) begin.next()).merge();
    }

    private void canonicalize(StructuredGraph graph, IfNode ifNode) {
        System.out.printf("if-canonicalize: ifNode=%s\n", ifNode);
        if (ifNode.predecessor() instanceof LoopBeginNode) {
            return;
        }
        final AbstractBeginNode trueBranch = ifNode.trueSuccessor();
        final AbstractBeginNode falseBranch = ifNode.falseSuccessor();

        if (isMerge(trueBranch)) {
            tryMergeClauses(graph, ifNode, trueBranch);
        } else if (isMerge(falseBranch)) {
            tryMergeClauses(graph, ifNode, falseBranch);
        }
    }

    private void tryMergeClauses(StructuredGraph graph, IfNode ifNode, AbstractBeginNode branch) {
        System.out.printf("if-canonicalize: trying merge for ifNode=%s\n", ifNode);
        final AbstractMergeNode merge = getMerge(branch);
        System.out.printf("if-canonicalize: merge=%s\n", merge);

        if (merge instanceof LoopBeginNode) {
            final LoopBeginNode loopBegin = (LoopBeginNode) merge;
            final int endCount = loopBegin.loopEnds().count();

            final IfNode[] clauses = new IfNode[endCount];
            final boolean[] branchTaken = new boolean[endCount];

            int i = 0;
            for (LoopEndNode end : loopBegin.orderedLoopEnds()) {
                System.out.printf("if-canonicalize: search end=%s\n", end);
                if (isIf(end)) {
                    clauses[i] = getIf(end);
                    if (i == 0 || !clauses[i].equals(clauses[i - 1])) {
                        branchTaken[i] = getBranchTaken(clauses[i], end);
                        System.out.printf("if-canonicalize: found clause %s on branch %s\n", clauses[i], branchTaken[i]);
                        i++;
                    }
                }
            }

            /*
             * TODO check that all clauses are directly connected here!
             */
            boolean clausesValid = checkClauses(ifNode, clauses, branchTaken);

            if (clausesValid) {
                System.out.printf("check-clauses: passed\n");

            }

        } else {
            final IfNode[] clauses = new IfNode[merge.forwardEndCount()];
            final boolean[] branchTaken = new boolean[merge.forwardEndCount()];

            int i = 0;
            for (EndNode end : merge.forwardEnds()) {
                System.out.printf("if-canonicalize: search end=%s\n", end);
                if (isIf(end)) {
                    clauses[i] = getIf(end);
                    branchTaken[i] = getBranchTaken(clauses[i], end);
                    System.out.printf("if-canonicalize: found clause %s on branch %s\n", clauses[i], branchTaken[i]);
                    i++;
                }
            }

            /*
             * TODO check that all clauses are directly connected here!
             */
            boolean clausesValid = checkClauses(ifNode, clauses, branchTaken);

            if (clausesValid) {
                System.out.printf("check-clauses: passed\n");
                final int lastIndex = clauses.length - 1;
                final LogicNode newCondition = mergeClauses(graph, clauses, branchTaken);

                clauses[lastIndex].setCondition(newCondition);

                cleanupClauses(graph, clauses, branchTaken, merge);

                new DeadCodeEliminationPhase().apply(graph);
            }

        }

    }

    private AbstractBeginNode getNode(IfNode ifNode, boolean branch) {
        return (branch) ? ifNode.trueSuccessor() : ifNode.falseSuccessor();
    }

    private boolean checkClauses(IfNode root, IfNode[] clauses, boolean[] branchTaken) {
        boolean result = true;

        final Set<IfNode> ifNodes = new HashSet<IfNode>();
        final Map<IfNode, Boolean> branches = new HashMap<IfNode, Boolean>();
        for (int i = 0; i < clauses.length; i++) {
            ifNodes.add(clauses[i]);
            branches.put(clauses[i], branchTaken[i]);
        }

        IfNode current = root;
        System.out.printf("check-clauses: start=%s\n", current);
        for (int i = 0; i < clauses.length && result; i++) {
            if (ifNodes.remove(current)) {
                clauses[i] = current;
                branchTaken[i] = branches.get(current);

                if (current.predecessor() instanceof LoopBeginNode) {
                    result = false;
                }
                if (!ifNodes.isEmpty()) {
                    final AbstractBeginNode begin = getNode(current, !branchTaken[i]);
                    System.out.printf("check-clauses: current=%s, branch=%s -> begin=%s\n", current, !branchTaken[i], begin);
                    if (begin.next() instanceof IfNode) {
                        current = (IfNode) begin.next();
                    } else {
                        System.out.printf("check-clauses: next != ifNode (%s)\n", begin.next());
                    }
                }

            } else {
                System.out.printf("check-clauses: ifNode=%s not in set\n", current);
                result = false;
            }
        }

        return result;
    }

    @SuppressWarnings("unlikely-arg-type")
    private void cleanupClauses(final StructuredGraph graph, final IfNode[] clauses, final boolean[] branchTaken, final AbstractMergeNode merge) {

        for (int i = 0; i < clauses.length - 1; i++) {
            cleanupBranch(clauses[i], branchTaken[i]);
            merge.forwardEnds().remove(clauses[i]);
            clauses[i].replaceAndDelete(clauses[i + 1]);
        }

        EndNode validEnd = null;
        for (EndNode e : merge.forwardEnds()) {
            System.out.printf("merge-cleanup: forward end=%s\n", e);
            if (e.isAlive()) {
                validEnd = e;
            }
        }

        for (PhiNode phi : merge.phis()) {
            System.out.printf("merge-cleanup: phi=%s\n", phi);
        }

        TornadoInternalError.guarantee(merge.phis().count() == 0, "phi values exist on merge node that is to be removed");

        FixedNode current = merge.next();
        validEnd.replaceAtPredecessor(current);
    }

    private void cleanupBranch(IfNode ifNode, boolean b) {
        final AbstractBeginNode begin = getNode(ifNode, b);
        begin.next().markDeleted();
        begin.markDeleted();
    }

    private LogicNode createClause(final StructuredGraph graph, final LogicNode left, boolean negateLeft, final LogicNode right, boolean negateRight) {

        final LogicNode lhs = (negateLeft) ? graph.addOrUnique(new LogicalNotNode(left)) : left;
        final LogicNode rhs = (negateRight) ? graph.addOrUnique(new LogicalNotNode(right)) : right;

        return graph.addOrUnique(new LogicalOrNode(lhs, rhs));
    }

    private LogicNode mergeClauses(final StructuredGraph graph, final IfNode[] clauses, final boolean[] branchTaken) {

        LogicNode leftCondition = clauses[0].condition();
        for (int i = 1; i < clauses.length; i++) {
            System.out.printf("i=%d\n", i);
            final LogicNode rightCondition = clauses[i].condition();
            System.out.printf("merge-clauses: left=%s, right=%s\n", leftCondition, rightCondition);
            leftCondition = createClause(graph, leftCondition, false, rightCondition, !branchTaken[i]);
        }
        return leftCondition;
    }

}
