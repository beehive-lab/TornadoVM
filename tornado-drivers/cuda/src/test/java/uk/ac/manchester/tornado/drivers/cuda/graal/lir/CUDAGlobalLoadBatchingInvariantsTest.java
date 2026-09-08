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
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import tornado.graal.compiler.lir.LIRInstruction;
import tornado.graal.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDAGlobalLoadBatching;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.AssignStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.LoadStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt.StoreStmt;

/**
 * Property-based test for {@link CUDAGlobalLoadBatching}.
 *
 * <p>
 * {@link CUDAGlobalLoadBatchingTest} pins down the cases a reader can reason about. This
 * one generates thousands of random instruction sequences from the same alphabet and
 * checks five invariants that together say "the pass changed the schedule and nothing
 * else". The generator draws its variables from a deliberately small pool so that
 * clobbering candidates — the one hazard the address-space argument does not cover —
 * occur constantly rather than by luck.
 *
 * <p>
 * The seed is fixed, so a failure is reproducible and prints the program that broke.
 */
public class CUDAGlobalLoadBatchingInvariantsTest {

    private static final int PROGRAMS = 4000;
    private static final int MAX_LENGTH = 24;
    private static final int VARIABLE_POOL = 6;
    private static final long SEED = 0x5CA1AB1EL;

    @Test
    public void randomProgramsKeepEveryInvariant() {
        Random random = new Random(SEED);
        int reordered = 0;
        for (int i = 0; i < PROGRAMS; i++) {
            List<LIRInstruction> in = randomProgram(random);
            List<LIRInstruction> out = CUDAGlobalLoadBatching.reorder(copy(in));
            if (!identical(in, out)) {
                reordered++;
            }
            check(in, out);
        }
        // A generator that never triggers the pass would make every invariant vacuous.
        assertTrue("generator never produced a batchable program", reordered > PROGRAMS / 20);
    }

    /** Runs every invariant, reporting the offending program when one fails. */
    private void check(List<LIRInstruction> in, List<LIRInstruction> out) {
        String where = "\nin:  " + render(in) + "\nout: " + render(out);

        // 1. Nothing is lost, added or duplicated.
        assertEquals("instruction multiset changed" + where, identityCounts(in), identityCounts(out));

        // 2. Everything that is not a sinkable store keeps its order.
        assertEquals("non-store order changed" + where, filter(in, false), filter(out, false));

        // 3. The sinkable stores keep their order among themselves.
        assertEquals("store order changed" + where, filter(in, true), filter(out, true));

        // 4. Nothing crosses an instruction the pass does not model: for every such
        // instruction, the set of instructions ahead of it is unchanged. This is what
        // makes barriers, calls and global stores safe without special-casing them.
        for (LIRInstruction op : in) {
            if (isEligible(op)) {
                continue;
            }
            assertEquals("an instruction crossed " + op + where, identityCounts(prefixBefore(in, op)), identityCounts(prefixBefore(out, op)));
        }

        // 5. No instruction that jumped ahead of a store defines a value that store reads.
        Map<LIRInstruction, Integer> inPos = positions(in);
        Map<LIRInstruction, Integer> outPos = positions(out);
        for (LIRInstruction op : in) {
            if (!(op instanceof StoreStmt)) {
                continue;
            }
            StoreStmt store = (StoreStmt) op;
            for (LIRInstruction other : in) {
                boolean jumpedAhead = inPos.get(other) > inPos.get(op) && outPos.get(other) < outPos.get(op);
                if (jumpedAhead) {
                    Value defined = definedValue(other);
                    assertTrue("hoisting " + other + " clobbers an operand of " + store + where, defined == null || !usedValues(store).contains(defined));
                }
            }
        }
    }

    private List<LIRInstruction> randomProgram(Random random) {
        CUDALIRTestPrograms p = new CUDALIRTestPrograms();
        List<Variable> pool = new ArrayList<>();
        for (int i = 0; i < VARIABLE_POOL; i++) {
            pool.add(p.variable(i));
        }
        List<LIRInstruction> program = new ArrayList<>();
        int length = 1 + random.nextInt(MAX_LENGTH);
        for (int i = 0; i < length; i++) {
            Variable a = pool.get(random.nextInt(pool.size()));
            Variable b = pool.get(random.nextInt(pool.size()));
            switch (random.nextInt(10)) {
                case 0, 1, 2 -> program.add(p.globalLoad(a, b));
                case 3, 4, 5 -> program.add(p.sharedStore(a, b));
                case 6 -> program.add(p.assign(a, b));
                case 7 -> program.add(p.privateStore(a, b));
                case 8 -> program.add(random.nextBoolean() ? p.barrier() : p.globalStore(a, b));
                default -> program.add(random.nextBoolean() ? p.sharedLoad(a, b) : p.opaque(a, b));
            }
        }
        return program;
    }

    private static boolean isEligible(LIRInstruction op) {
        if (op instanceof AssignStmt) {
            return true;
        }
        if (op instanceof LoadStmt) {
            return !((LoadStmt) op).isLocalOrPrivateLoad();
        }
        if (op instanceof StoreStmt) {
            return ((StoreStmt) op).isLocalOrPrivateStore();
        }
        return false;
    }

    private static boolean isSinkableStore(LIRInstruction op) {
        return op instanceof StoreStmt && ((StoreStmt) op).isLocalOrPrivateStore();
    }

    private static Value definedValue(LIRInstruction op) {
        if (op instanceof LoadStmt) {
            return ((LoadStmt) op).getResult();
        }
        if (op instanceof AssignStmt) {
            return ((AssignStmt) op).getResult();
        }
        return null;
    }

    private static List<Value> usedValues(StoreStmt store) {
        List<Value> used = new ArrayList<>();
        add(used, store.getRhs());
        add(used, store.getIndex());
        if (store.getAddress() != null) {
            add(used, store.getAddress().getValue());
            add(used, store.getAddress().getIndex());
        }
        return used;
    }

    private static void add(List<Value> to, Value v) {
        if (v != null) {
            to.add(v);
        }
    }

    private static List<LIRInstruction> filter(List<LIRInstruction> ops, boolean stores) {
        List<LIRInstruction> out = new ArrayList<>();
        for (LIRInstruction op : ops) {
            if (isSinkableStore(op) == stores) {
                out.add(op);
            }
        }
        return out;
    }

    private static List<LIRInstruction> prefixBefore(List<LIRInstruction> ops, LIRInstruction marker) {
        List<LIRInstruction> out = new ArrayList<>();
        for (LIRInstruction op : ops) {
            if (op == marker) {
                break;
            }
            out.add(op);
        }
        return out;
    }

    private static Map<LIRInstruction, Integer> identityCounts(List<LIRInstruction> ops) {
        Map<LIRInstruction, Integer> counts = new IdentityHashMap<>();
        for (LIRInstruction op : ops) {
            counts.merge(op, 1, Integer::sum);
        }
        return counts;
    }

    private static Map<LIRInstruction, Integer> positions(List<LIRInstruction> ops) {
        Map<LIRInstruction, Integer> pos = new IdentityHashMap<>();
        for (int i = 0; i < ops.size(); i++) {
            pos.put(ops.get(i), i);
        }
        return pos;
    }

    private static boolean identical(List<LIRInstruction> a, List<LIRInstruction> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static List<LIRInstruction> copy(List<LIRInstruction> ops) {
        return new ArrayList<>(ops);
    }

    private static String render(List<LIRInstruction> ops) {
        StringBuilder sb = new StringBuilder();
        for (LIRInstruction op : ops) {
            sb.append(op.getClass().getSimpleName()).append('#').append(System.identityHashCode(op) % 1000).append(' ');
        }
        return sb.toString();
    }
}
