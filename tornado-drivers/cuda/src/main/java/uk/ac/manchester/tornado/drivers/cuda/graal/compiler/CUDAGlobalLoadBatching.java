/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import java.util.ArrayList;
import java.util.List;

import tornado.graal.compiler.lir.LIRInstruction;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIROp;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.LoadStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.StoreStmt;

/**
 * Peephole that batches global loads inside a shared-memory staging sequence.
 *
 * <h2>The problem</h2>
 *
 * TornadoVM emits a tile-staging loop as an alternating chain: every global load
 * is consumed by the shared-memory store on the very next line.
 *
 * <pre>
 * ul_107 = ul_0 + l_106;
 * f_108  = *(( float *) ul_107);           // global load
 * adf_5[i_97] = f_108;                     // shared store, consumes it at once
 * i_109  = i_92 + i_102;
 * l_110  = (long long) i_109;
 * l_112  = (l_110 + 4L) &lt;&lt; 2;
 * ul_113 = ul_0 + l_112;
 * f_114  = *(( float *) ul_113);           // next global load, cannot start
 * adf_5[i_88] = f_114;                     //   until the previous one landed
 * </pre>
 *
 * ptxas will not batch the loads itself, and the reason is visible in the cast
 * above: the emitted C carries no address space, because
 * {@code CUDAAssemblerConstants.GLOBAL_MEM_MODIFIER} is the empty string. ptxas
 * therefore lowers these to a <b>generic</b> {@code LD}, which may target shared
 * memory, so it may not be reordered against a shared store. Hand-written CUDA
 * loads through a {@code const float *} parameter, lowers to {@code LDG} — proven
 * global, and so provably not aliasing the {@code STS} beside it — and ptxas
 * batches those on its own. The SASS staging schedules on an Ada tile kernel,
 * with {@code L} a global load and {@code S} a shared store:
 *
 * <pre>
 * before this pass    L S L S L S L S L S L S L S L S     generic LD
 * after this pass     L L L L L L L L S S S S S S S S     generic LD
 * hand-written CUDA   L L L L L L L L S S S S S S S S     LDG
 * </pre>
 *
 * The cost of the serialised form is a {@code long_scoreboard} stall of 12.10
 * cycles per issue against 1.45 for the equivalent hand-written CUDA, and an issue
 * rate of 20.8% against 50.9%, with identical FFMA counts, identical occupancy and
 * zero spills on both sides.
 *
 * <p>
 * Giving ptxas the address space directly — emitting {@code __ldg()} or a
 * global-qualified pointer — would fix the schedule at its source and make this
 * pass unnecessary for the cases ptxas can then handle. That is a change to
 * address lowering and is not attempted here.
 *
 * <h2>The transformation</h2>
 *
 * Within a straight-line <em>run</em> of LIR whose every instruction is one of
 *
 * <ul>
 * <li>a <b>global</b> {@link LoadStmt} (not local, not private),</li>
 * <li>an {@link AssignStmt} — pure register arithmetic; no CUDA LIR operator
 * dereferences memory, only Load/Store statements do,</li>
 * <li>a <b>local or private</b> {@link StoreStmt},</li>
 * </ul>
 *
 * the stores are sunk to the end of the run. Everything else keeps its original
 * relative order, and the stores keep theirs. The run stops at the first
 * instruction that is none of the three, so barriers, calls, control flow,
 * global stores, shared loads, atomics and MMA statements all end it.
 *
 * <h2>Why it is sound</h2>
 *
 * <ol>
 * <li><b>No memory dependence is broken.</b> The only writes in the run go to
 * local/shared memory and the only reads come from global memory. The two
 * address spaces are disjoint in CUDA, so no load in the run can observe a store
 * in the run.</li>
 * <li><b>No shared read is skipped over.</b> A shared or private
 * {@code LoadStmt} is not an eligible instruction, so a run can never contain a
 * reader of the memory the sunk stores write.</li>
 * <li><b>Store order is preserved</b>, so two stores to the same shared address
 * still land in the same order.</li>
 * <li><b>True data dependences hold.</b> A store only ever moves later, so every
 * value it reads is still defined before it. Non-store instructions do not move
 * relative to each other at all.</li>
 * <li><b>No value is clobbered.</b> {@link #canSinkPast} checks explicitly that
 * no instruction jumping ahead of a store defines a value that store reads. That
 * is the one case the address-space argument does not cover — it would matter if
 * the LIR ever reused a variable — and it is tested rather than assumed.</li>
 * <li><b>Visibility to other threads is unchanged.</b> The run ends before any
 * barrier, so the sunk stores still complete before the same
 * {@code __syncthreads()} they did before.</li>
 * </ol>
 *
 * If any single store fails the check the whole run is left exactly as it was.
 *
 * <h2>Effect</h2>
 *
 * The staging sequence becomes all loads, then all stores, so the loads are
 * independent and the hardware keeps several in flight:
 *
 * <pre>
 * ul_107 = ul_0 + l_106;
 * f_108  = *(( float *) ul_107);
 * i_109  = i_92 + i_102;
 * ...
 * ul_113 = ul_0 + l_112;
 * f_114  = *(( float *) ul_113);
 * adf_5[i_97] = f_108;
 * adf_5[i_88] = f_114;
 * </pre>
 *
 * Disable with {@code -Dtornado.cuda.batchGlobalLoads=False}.
 */
public final class CUDAGlobalLoadBatching {

    private CUDAGlobalLoadBatching() {
    }

    /**
     * Returns a reordered copy of {@code ops}, or {@code ops} itself when nothing
     * is eligible. The input list is never mutated.
     */
    public static List<LIRInstruction> reorder(List<LIRInstruction> ops) {
        int firstRun = findRunStart(ops, 0);
        if (firstRun < 0) {
            return ops;
        }

        List<LIRInstruction> out = new ArrayList<>(ops.size());
        int i = 0;
        boolean changed = false;
        while (i < ops.size()) {
            int runEnd = runEndFrom(ops, i);
            if (runEnd - i < 2) {
                out.add(ops.get(i));
                i++;
                continue;
            }
            List<LIRInstruction> run = ops.subList(i, runEnd);
            List<LIRInstruction> batched = batchRun(run);
            out.addAll(batched);
            changed |= batched != run;
            i = runEnd;
        }
        return changed ? out : ops;
    }

    private static int findRunStart(List<LIRInstruction> ops, int from) {
        for (int i = from; i < ops.size(); i++) {
            if (isEligible(ops.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Index one past the last eligible instruction of the run starting at {@code start}. */
    private static int runEndFrom(List<LIRInstruction> ops, int start) {
        int i = start;
        while (i < ops.size() && isEligible(ops.get(i))) {
            i++;
        }
        return i;
    }

    private static boolean isEligible(LIRInstruction op) {
        return isGlobalLoad(op) || isSinkableStore(op) || op instanceof AssignStmt;
    }

    private static boolean isGlobalLoad(LIRInstruction op) {
        return op instanceof LoadStmt && !((LoadStmt) op).isLocalOrPrivateLoad();
    }

    private static boolean isSinkableStore(LIRInstruction op) {
        return op instanceof StoreStmt && ((StoreStmt) op).isLocalOrPrivateStore();
    }

    /**
     * Stable-partitions one run into "everything that is not a sinkable store" followed
     * by "the sinkable stores". Returns {@code run} unchanged when the reorder would
     * buy nothing or when any store fails the clobber check.
     */
    private static List<LIRInstruction> batchRun(List<LIRInstruction> run) {
        // Worth doing only if some store is followed by a global load: that is the
        // dependence the batching removes.
        boolean worthwhile = false;
        boolean sawStore = false;
        for (LIRInstruction op : run) {
            if (isSinkableStore(op)) {
                sawStore = true;
            } else if (sawStore && isGlobalLoad(op)) {
                worthwhile = true;
                break;
            }
        }
        if (!worthwhile) {
            return run;
        }

        for (int s = 0; s < run.size(); s++) {
            if (!isSinkableStore(run.get(s))) {
                continue;
            }
            StoreStmt store = (StoreStmt) run.get(s);
            for (int m = s + 1; m < run.size(); m++) {
                LIRInstruction mover = run.get(m);
                if (isSinkableStore(mover)) {
                    continue; // stores keep their order, they do not jump the store
                }
                if (!canSinkPast(store, mover)) {
                    return run;
                }
            }
        }

        List<LIRInstruction> reordered = new ArrayList<>(run.size());
        List<LIRInstruction> stores = new ArrayList<>();
        for (LIRInstruction op : run) {
            if (isSinkableStore(op)) {
                stores.add(op);
            } else {
                reordered.add(op);
            }
        }
        reordered.addAll(stores);
        return reordered;
    }

    /**
     * True when {@code mover} can be emitted before {@code store}: the value it defines
     * must not be one the store reads.
     */
    private static boolean canSinkPast(StoreStmt store, LIRInstruction mover) {
        Value defined;
        if (mover instanceof LoadStmt) {
            defined = ((LoadStmt) mover).getResult();
        } else if (mover instanceof AssignStmt) {
            defined = ((AssignStmt) mover).getResult();
        } else {
            return false; // unreachable for an eligible run; refuse rather than guess
        }
        if (defined == null) {
            return false;
        }
        for (Value used : usedValues(store)) {
            if (used == null) {
                return false; // could not read an operand: refuse
            }
            if (defined.equals(used)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every value a shared/local store reads. {@code cast} is deliberately not
     * consulted: {@code CUDAAddressCast} is always built with a {@code null} operand and
     * emits only a type name.
     *
     * <p>
     * A {@code null} entry means "operand not understood" and makes
     * {@link #canSinkPast} refuse, which leaves the run untouched. That is why an
     * operand that is itself a {@link CUDALIROp} expression tree is reported as
     * {@code null} rather than walked.
     */
    private static List<Value> usedValues(StoreStmt store) {
        List<Value> used = new ArrayList<>(4);
        used.add(plainOrNull(store.getRhs()));
        used.add(plainOrNull(store.getIndex()));
        if (store.getAddress() == null) {
            used.add(null);
        } else {
            used.add(plainOrNull(store.getAddress().getValue()));
            used.add(plainOrNull(store.getAddress().getIndex()));
        }
        return used;
    }

    /**
     * Passes through a plain value, drops an absent one, and maps a nested LIR operator
     * onto {@code null} so the caller bails out.
     */
    private static Value plainOrNull(Value v) {
        if (v == null) {
            return Value.ILLEGAL; // absent operand: nothing to conflict with
        }
        return v instanceof CUDALIROp ? null : v;
    }
}
