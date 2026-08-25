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
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import tornado.graal.compiler.core.common.cfg.Loop;
import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.graph.iterators.NodeIterable;
import tornado.graal.compiler.nodes.IfNode;
import tornado.graal.compiler.nodes.cfg.ControlFlowGraph;
import tornado.graal.compiler.nodes.cfg.HIRBlock;
import tornado.graal.compiler.nodes.extended.IntegerSwitchNode;

import jdk.vm.ci.meta.JavaConstant;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants;

/**
 * Structured control-flow recovery for the CUDADriver backend.
 *
 * <p>
 * Instead of placing braces with local pattern-matching heuristics during a
 * dominator-tree walk, code generation works in two phases:
 *
 * <ol>
 * <li><b>Structure</b>: build an explicit, properly-nested control-flow AST
 * ({@link Region}) from Graal's {@link ControlFlowGraph}, using the dominator
 * tree and the loop tree. Loops form their own scope so that
 * {@code break}/{@code continue}/early-{@code return} edges terminate a
 * sub-region cleanly instead of being special-cased.</li>
 * <li><b>Emit</b>: a stateless recursive walk of the AST. Opening and closing
 * braces match <em>by construction</em> because they come from a tree.</li>
 * </ol>
 *
 * <p>
 * The straight-line content of each block (assignments, loads, the
 * {@code if (cond)} / {@code for(;cond;)} / {@code switch(x)} headers) is still
 * produced by the existing LIR ops through
 * {@link CUDACompilationResultBuilder#emitBlock}. Only the structural scaffolding
 * (branch braces, {@code else}, {@code case} labels and every closing brace) is
 * produced here.
 *
 * <p>
 * Join/follow nodes are computed from the dominator tree rather than from
 * {@link HIRBlock#getPostdominator()}, because Graal frequently leaves
 * post-dominators unset for blocks inside loops or near merges.
 *
 * <p>
 * Set {@code -Dtornado.opencl.structuredcfg.debug=true} to dump the recovered
 * CFG facts (dominators, loop membership, successors) to {@code stderr}.
 */
public final class CUDAStructuredControlFlow {

    // ---------------------------------------------------------------------
    // Control-flow AST
    // ---------------------------------------------------------------------

    sealed interface Region permits Seq, Block, IfElse, LoopRegion, SwitchRegion {
    }

    /** An ordered sequence of nested regions. */
    record Seq(List<Region> items) implements Region {
    }

    /** A leaf: emit a single basic block's LIR statements. */
    record Block(HIRBlock block) implements Region {
    }

    /** {@code head} ends in an {@link IfNode}; {@code elseRegion} may be null. */
    record IfElse(HIRBlock head, Region thenRegion, Region elseRegion) implements Region {
    }

    /** {@code header} is a loop header; {@code body} is the loop body. */
    record LoopRegion(HIRBlock header, Region body) implements Region {
    }

    /** {@code head} ends in an {@link IntegerSwitchNode}. */
    record SwitchRegion(HIRBlock head, List<SwitchCase> cases) implements Region {
    }

    record SwitchCase(HIRBlock entry, Region body) {
    }

    // ---------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------

    private final CUDACompilationResultBuilder builder;
    private final CUDAAssembler asm;
    private HIRBlock[] allBlocks;

    public CUDAStructuredControlFlow(CUDACompilationResultBuilder builder) {
        this.builder = builder;
        this.asm = builder.getAssembler();
    }

    public void emit(ControlFlowGraph cfg) {
        if (Boolean.getBoolean("tornado.opencl.structuredcfg.debug")) {
            dump(cfg);
        }
        this.allBlocks = cfg.getBlocks();
        Region root = structure(cfg.getStartBlock(), null, new HashSet<>(), new HashSet<>());
        emit(root);
    }

    private static void dump(ControlFlowGraph cfg) {
        for (HIRBlock b : cfg.getBlocks()) {
            StringBuilder succ = new StringBuilder();
            for (int i = 0; i < b.getSuccessorCount(); i++) {
                succ.append(b.getSuccessorAt(i).getId()).append(",");
            }
            System.err.printf("[CFG] B%d end=%s loopHeader=%s loop=%s dom=%s postdom=%s succ=[%s]%n", b.getId(), b.getEndNode().getClass().getSimpleName(), b.isLoopHeader(),
                    b.getLoop() == null ? "-" : "H" + b.getLoop().getHeader().getId(), b.getDominator() == null ? "-" : b.getDominator().getId(),
                    b.getPostdominator() == null ? "-" : b.getPostdominator().getId(), succ);
        }
    }

    /**
     * Builds the region covering the linear chain of blocks starting at
     * {@code entry}, within loop scope {@code loop} (null at the top level),
     * stopping before any block in {@code stops} (a "follow" boundary) or one
     * already emitted.
     *
     * <p>
     * Within a loop, the first block reached that does not belong to the loop is
     * a {@code break}/exit landing pad: it is emitted (its LIR contains the
     * {@code break;}) and terminates the chain, so post-loop code is never
     * pulled inside the branch.
     *
     * <p>
     * {@code visited} is path-local (copied when descending into a branch). This
     * guarantees termination (a block is never re-entered on a single path) while
     * still allowing a block that two sibling branches reach to be emitted in
     * each of them. That is <em>tail duplication</em>, required when a partial
     * merge sits between a split and its true join — e.g. the shared
     * {@code else}-target of a short-circuit {@code &&}/{@code ||} condition.
     */
    private Region structure(HIRBlock entry, Loop<HIRBlock> loop, Set<HIRBlock> stops, Set<HIRBlock> visited) {
        List<Region> items = new ArrayList<>();
        HIRBlock b = entry;
        while (b != null && !stops.contains(b) && !visited.contains(b)) {
            if (loop != null && !loop.getBlocks().contains(b)) {
                // break / loop-exit landing pad: emit it, then stop.
                visited.add(b);
                items.add(new Block(b));
                break;
            }
            visited.add(b);
            if (b.isLoopHeader()) {
                HIRBlock cont = loopContinuation(b);
                items.add(buildLoop(b, stops, visited));
                b = cont;
            } else if (isUserIf(b)) {
                HIRBlock follow = computeFollow(b, loop);
                items.add(buildIf(b, follow, loop, stops, visited));
                b = follow;
            } else if (isSwitch(b)) {
                HIRBlock follow = computeFollow(b, loop);
                items.add(buildSwitch(b, follow, loop, stops, visited));
                b = follow;
            } else {
                items.add(new Block(b));
                b = sequentialSuccessor(b);
            }
        }
        return new Seq(items);
    }

    private Region buildLoop(HIRBlock header, Set<HIRBlock> stops, Set<HIRBlock> visited) {
        Loop<HIRBlock> loop = header.getLoop();
        Set<HIRBlock> bodyStops = new HashSet<>(stops);
        bodyStops.add(header);
        HIRBlock bodyEntry = loopBodyEntry(header, loop);
        Region body = bodyEntry == null ? new Seq(List.of()) : structure(bodyEntry, loop, bodyStops, new HashSet<>(visited));
        return new LoopRegion(header, body);
    }

    private Region buildIf(HIRBlock head, HIRBlock follow, Loop<HIRBlock> loop, Set<HIRBlock> stops, Set<HIRBlock> visited) {
        Set<HIRBlock> branchStops = new HashSet<>(stops);
        if (follow != null) {
            branchStops.add(follow);
        }
        IfNode ifNode = (IfNode) head.getEndNode();
        HIRBlock trueBlock = successorOf(head, ifNode.trueSuccessor());
        HIRBlock falseBlock = successorOf(head, ifNode.falseSuccessor());

        // Each branch gets its own copy of the visited set so that a partial merge
        // reached from both branches is tail-duplicated into each of them.
        Region thenRegion = (trueBlock == null || trueBlock == follow) ? new Seq(List.of()) : structure(trueBlock, loop, branchStops, new HashSet<>(visited));
        Region elseRegion = (falseBlock == null || falseBlock == follow) ? null : structure(falseBlock, loop, branchStops, new HashSet<>(visited));
        return new IfElse(head, thenRegion, elseRegion);
    }

    private Region buildSwitch(HIRBlock head, HIRBlock follow, Loop<HIRBlock> loop, Set<HIRBlock> stops, Set<HIRBlock> visited) {
        Set<HIRBlock> caseStops = new HashSet<>(stops);
        if (follow != null) {
            caseStops.add(follow);
        }
        IntegerSwitchNode switchNode = (IntegerSwitchNode) head.getEndNode();
        List<SwitchCase> cases = new ArrayList<>();
        Set<HIRBlock> seenEntries = new HashSet<>();
        for (int i = 0; i < head.getSuccessorCount(); i++) {
            HIRBlock caseEntry = head.getSuccessorAt(i);
            if (caseEntry == follow || !seenEntries.add(caseEntry)) {
                continue;
            }
            Region body = structure(caseEntry, loop, caseStops, new HashSet<>(visited));
            cases.add(new SwitchCase(caseEntry, body));
        }
        return new SwitchRegion(head, cases);
    }

    /**
     * Computes the join/follow block of a 2-way (if) or n-way (switch) split
     * {@code head}: the block at which the branches re-merge and ordinary
     * sequential code resumes. Returns {@code null} when the branches do not
     * re-merge inside the current scope (both break/return), in which case there
     * is no code after the split at this level.
     *
     * <p>
     * The true join is the immediate post-dominator. When Graal provides it
     * ({@link HIRBlock#getPostdominator()} is non-null) we use it directly — this
     * is what distinguishes the real join from a <em>partial</em> merge that only
     * some branch paths reach (e.g. the shared {@code else}-target of a
     * short-circuit {@code &&}). Graal, however, frequently leaves post-dominators
     * unset for blocks inside loops or near merges; in that case we fall back to
     * the nearest dominator-tree merge (a block immediately dominated by
     * {@code head} with more than one predecessor).
     *
     * <p>
     * A follow outside the current loop is clamped to {@code null}: the branches
     * leave the loop, which is handled by the break/exit rule in
     * {@link #structure}.
     */
    private HIRBlock computeFollow(HIRBlock head, Loop<HIRBlock> loop) {
        HIRBlock follow = head.getPostdominator();
        if (follow == null) {
            for (HIRBlock candidate : allBlocks) {
                if (candidate.getDominator() == head && candidate.getPredecessorCount() > 1) {
                    if (follow == null || candidate.getDominatorDepth() < follow.getDominatorDepth()) {
                        follow = candidate;
                    }
                }
            }
        }
        if (follow != null && loop != null && !loop.getBlocks().contains(follow)) {
            return null;
        }
        return follow;
    }

    // ---------------------------------------------------------------------
    // Builder helpers
    // ---------------------------------------------------------------------

    private static boolean isUserIf(HIRBlock block) {
        return !block.isLoopHeader() && block.getEndNode() instanceof IfNode;
    }

    private static boolean isSwitch(HIRBlock block) {
        return block.getEndNode() instanceof IntegerSwitchNode;
    }

    private static HIRBlock sequentialSuccessor(HIRBlock block) {
        return block.getSuccessorCount() >= 1 ? block.getSuccessorAt(0) : null;
    }

    private static HIRBlock successorOf(HIRBlock block, Node beginNode) {
        for (int i = 0; i < block.getSuccessorCount(); i++) {
            HIRBlock s = block.getSuccessorAt(i);
            if (s.getBeginNode() == beginNode) {
                return s;
            }
        }
        return null;
    }

    /**
     * The body entry of a loop is the header's successor that stays inside the
     * loop.
     */
    private static HIRBlock loopBodyEntry(HIRBlock header, Loop<HIRBlock> loop) {
        for (int i = 0; i < header.getSuccessorCount(); i++) {
            HIRBlock s = header.getSuccessorAt(i);
            if (loop.getBlocks().contains(s)) {
                return s;
            }
        }
        return null;
    }

    /**
     * The block where execution resumes after the loop: the header's successor
     * that leaves the loop (the natural {@link LoopExitNode} landing pad).
     */
    private static HIRBlock loopContinuation(HIRBlock header) {
        Loop<HIRBlock> loop = header.getLoop();
        for (int i = 0; i < header.getSuccessorCount(); i++) {
            HIRBlock s = header.getSuccessorAt(i);
            if (!loop.getBlocks().contains(s)) {
                return s;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Emitter
    // ---------------------------------------------------------------------

    private void emit(Region region) {
        if (region instanceof Seq seq) {
            for (Region child : seq.items()) {
                emit(child);
            }
        } else if (region instanceof Block block) {
            builder.emitBlock(block.block());
            builder.emitRelocatedInstructions(block.block());
        } else if (region instanceof LoopRegion loop) {
            emitLoop(loop);
        } else if (region instanceof IfElse ifElse) {
            emitIf(ifElse);
        } else if (region instanceof SwitchRegion switchRegion) {
            emitSwitch(switchRegion);
        }
    }

    private void emitLoop(LoopRegion loop) {
        builder.emitLoopBlock(loop.header()); // emits "init; for(;cond;) {"  (open brace from LoopPostOp)
        emit(loop.body());
        asm.endScope(loop.header().toString());
        builder.emitRelocatedInstructions(loop.header());
    }

    private void emitIf(IfElse ifElse) {
        builder.emitBlock(ifElse.head()); // emits the "if (cond)" via ConditionalBranchOp
        asm.beginScope();
        asm.eolOn();
        emit(ifElse.thenRegion());
        asm.endScope(ifElse.head().toString());
        if (ifElse.elseRegion() != null) {
            asm.indent();
            asm.elseStmt();
            asm.eol();
            asm.beginScope();
            asm.eolOn();
            emit(ifElse.elseRegion());
            asm.endScope(ifElse.head().toString());
        }
        builder.emitRelocatedInstructions(ifElse.head());
    }

    private void emitSwitch(SwitchRegion switchRegion) {
        builder.emitBlock(switchRegion.head()); // emits "switch (x) {" (open brace from SwitchOp)
        IntegerSwitchNode switchNode = (IntegerSwitchNode) switchRegion.head().getEndNode();
        for (SwitchCase c : switchRegion.cases()) {
            emitCaseLabel(switchNode, c.entry());
            emit(c.body());
            asm.emitLine(CUDAAssemblerConstants.BREAK + CUDAAssemblerConstants.STMT_DELIMITER);
        }
        asm.endScope(switchRegion.head().toString());
        builder.emitRelocatedInstructions(switchRegion.head());
    }

    private void emitCaseLabel(IntegerSwitchNode switchNode, HIRBlock caseEntry) {
        asm.indent();
        Node beginNode = caseEntry.getBeginNode();
        NodeIterable<Node> successors = switchNode.successors();
        int defaultSuccessorIndex = switchNode.defaultSuccessorIndex();

        int caseIndex = -1;
        Iterator<Node> iterator = successors.iterator();
        while (iterator.hasNext()) {
            Node n = iterator.next();
            caseIndex++;
            if (n.equals(beginNode)) {
                break;
            }
        }

        if (defaultSuccessorIndex == caseIndex) {
            asm.emit(CUDAAssemblerConstants.DEFAULT_CASE + CUDAAssemblerConstants.COLON);
            asm.emitLine("");
            asm.indent();
        } else {
            for (int i = 0; i <= defaultSuccessorIndex; i++) {
                if (caseIndex == switchNode.keySuccessorIndex(i)) {
                    asm.emit(CUDAAssemblerConstants.CASE + " ");
                    JavaConstant keyAt = switchNode.keyAt(i);
                    asm.emit(keyAt.toValueString());
                    asm.emit(CUDAAssemblerConstants.COLON);
                    asm.emitLine("");
                    asm.indent();
                }
            }
        }
    }
}
