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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRTestPrograms.list;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import tornado.graal.compiler.lir.LIRInstruction;
import tornado.graal.compiler.lir.Variable;

import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDAGlobalLoadBatching;

/**
 * Behavioural tests for {@link CUDAGlobalLoadBatching}, the peephole that batches the
 * global loads of a shared-memory staging sequence.
 *
 * <p>
 * Each test states one rule the pass must obey. The rules that say "left untouched" carry
 * the weight: a code generator that reorders slightly too much produces silently wrong
 * results, which no timing test would catch.
 *
 * <p>
 * These need no GPU and no CUDA driver — they run the pass over hand-built LIR. Run them
 * with {@code make test-cuda-codegen}.
 */
public class CUDAGlobalLoadBatchingTest {

    private CUDALIRTestPrograms p;

    @Before
    public void setUp() {
        p = new CUDALIRTestPrograms();
    }

    /**
     * The motivating case. Two staging pairs, each "compute address, load global, store
     * shared", become "both addresses, both loads, both stores".
     */
    @Test
    public void batchesTwoStagingPairs() {
        Variable addr0 = p.variable();
        Variable val0 = p.variable();
        Variable addr1 = p.variable();
        Variable val1 = p.variable();
        Variable base = p.variable();
        Variable idx0 = p.variable();
        Variable idx1 = p.variable();

        LIRInstruction a0 = p.assign(addr0, base);
        LIRInstruction l0 = p.globalLoad(val0, addr0);
        LIRInstruction s0 = p.sharedStore(idx0, val0);
        LIRInstruction a1 = p.assign(addr1, base);
        LIRInstruction l1 = p.globalLoad(val1, addr1);
        LIRInstruction s1 = p.sharedStore(idx1, val1);

        List<LIRInstruction> in = list(a0, l0, s0, a1, l1, s1);
        assertEquals(list(a0, l0, a1, l1, s0, s1), CUDAGlobalLoadBatching.reorder(in));
    }

    /** Store order is preserved, so two writes to the same shared address still race the same way. */
    @Test
    public void preservesStoreOrder() {
        Variable v0 = p.variable();
        Variable v1 = p.variable();
        Variable addr = p.variable();
        Variable slot = p.variable();

        LIRInstruction s0 = p.sharedStore(slot, v0);
        LIRInstruction s1 = p.sharedStore(slot, v1);
        LIRInstruction l = p.globalLoad(p.variable(), addr);

        List<LIRInstruction> out = CUDAGlobalLoadBatching.reorder(list(s0, s1, l));
        assertEquals(list(l, s0, s1), out);
    }

    /**
     * A barrier ends the run. A store before {@code __syncthreads()} must still be visible
     * to the other threads at that barrier, so it cannot sink past it.
     */
    @Test
    public void doesNotSinkAcrossABarrier() {
        Variable v = p.variable();
        Variable addr = p.variable();

        LIRInstruction s = p.sharedStore(p.variable(), v);
        LIRInstruction b = p.barrier();
        LIRInstruction l = p.globalLoad(p.variable(), addr);

        List<LIRInstruction> in = list(s, b, l);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /**
     * A shared load reads the memory the staged stores write, so the run stops there. This
     * is the case that would produce wrong answers if the pass got it wrong.
     */
    @Test
    public void doesNotSinkPastASharedLoad() {
        Variable staged = p.variable();
        LIRInstruction s = p.sharedStore(p.variable(), staged);
        LIRInstruction readBack = p.sharedLoad(p.variable(), p.variable());
        LIRInstruction l = p.globalLoad(p.variable(), p.variable());

        List<LIRInstruction> in = list(s, readBack, l);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /** Private memory is per-thread but the same argument applies: a reader ends the run. */
    @Test
    public void doesNotSinkPastAPrivateLoad() {
        LIRInstruction s = p.privateStore(p.variable(), p.variable());
        LIRInstruction readBack = p.privateLoad(p.variable(), p.variable());
        LIRInstruction l = p.globalLoad(p.variable(), p.variable());

        List<LIRInstruction> in = list(s, readBack, l);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /** A global store is not sinkable and is not eligible, so it ends the run. */
    @Test
    public void aGlobalStoreEndsTheRun() {
        LIRInstruction s = p.sharedStore(p.variable(), p.variable());
        LIRInstruction gs = p.globalStore(p.variable(), p.variable());
        LIRInstruction l = p.globalLoad(p.variable(), p.variable());

        List<LIRInstruction> in = list(s, gs, l);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /** Anything the pass does not recognise ends the run rather than being stepped over. */
    @Test
    public void anUnrecognisedInstructionEndsTheRun() {
        LIRInstruction s = p.sharedStore(p.variable(), p.variable());
        LIRInstruction opaque = p.opaque(p.variable(), p.variable());
        LIRInstruction l = p.globalLoad(p.variable(), p.variable());

        List<LIRInstruction> in = list(s, opaque, l);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /**
     * The clobber guard. If the LIR ever reuses a variable, hoisting the second load above
     * the first store would overwrite the value that store is about to read. The
     * address-space argument does not cover this, so it is checked explicitly.
     */
    @Test
    public void refusesWhenAHoistedLoadWouldClobberAStoredValue() {
        Variable reused = p.variable(42);
        LIRInstruction l0 = p.globalLoad(reused, p.variable());
        LIRInstruction s0 = p.sharedStore(p.variable(), reused);
        LIRInstruction l1 = p.globalLoad(reused, p.variable()); // redefines what s0 reads

        List<LIRInstruction> in = list(l0, s0, l1);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /** Same guard, on the store's address rather than its value. */
    @Test
    public void refusesWhenAHoistedAssignWouldClobberAStoreAddress() {
        Variable slot = p.variable(7);
        LIRInstruction s = p.sharedStore(slot, p.variable());
        LIRInstruction a = p.assign(slot, p.variable()); // redefines the address s writes to
        LIRInstruction l = p.globalLoad(p.variable(), p.variable());

        List<LIRInstruction> in = list(s, a, l);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /**
     * A store whose value is a nested LIR operator cannot have its operands enumerated, so
     * the pass declines rather than guessing.
     */
    @Test
    public void refusesWhenAStoreOperandIsANestedExpression() {
        Variable v = p.variable();
        LIRInstruction s = p.sharedStore(p.variable(), new CUDAUnary.Expr(uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDAUnaryIntrinsic.ABS, tornado.graal.compiler.core.common.LIRKind.value(CUDAKind.FLOAT), v));
        LIRInstruction l = p.globalLoad(p.variable(), p.variable());

        List<LIRInstruction> in = list(s, l);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /** An {@link CUDALIRStmt.AssignStmt} with a nested operator still moves: only its output matters. */
    @Test
    public void hoistsPastAnAssignWithANestedExpression() {
        Variable v = p.variable();
        Variable addr = p.variable();
        LIRInstruction s = p.sharedStore(p.variable(), v);
        LIRInstruction a = p.assignExpression(addr, p.variable());
        LIRInstruction l = p.globalLoad(p.variable(), addr);

        assertEquals(list(a, l, s), CUDAGlobalLoadBatching.reorder(list(s, a, l)));
    }

    /** No store followed by a global load means no dependence to break, so nothing moves. */
    @Test
    public void leavesASequenceWithNothingToGainAlone() {
        LIRInstruction l = p.globalLoad(p.variable(), p.variable());
        LIRInstruction s0 = p.sharedStore(p.variable(), p.variable());
        LIRInstruction s1 = p.sharedStore(p.variable(), p.variable());

        List<LIRInstruction> in = list(l, s0, s1);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /** Runs on both sides of a barrier are batched independently. */
    @Test
    public void batchesEachRunAroundABarrierIndependently() {
        Variable v0 = p.variable();
        Variable v1 = p.variable();
        Variable v2 = p.variable();
        Variable v3 = p.variable();

        LIRInstruction l0 = p.globalLoad(v0, p.variable());
        LIRInstruction s0 = p.sharedStore(p.variable(), v0);
        LIRInstruction l1 = p.globalLoad(v1, p.variable());
        LIRInstruction s1 = p.sharedStore(p.variable(), v1);
        LIRInstruction b = p.barrier();
        LIRInstruction l2 = p.globalLoad(v2, p.variable());
        LIRInstruction s2 = p.sharedStore(p.variable(), v2);
        LIRInstruction l3 = p.globalLoad(v3, p.variable());
        LIRInstruction s3 = p.sharedStore(p.variable(), v3);

        List<LIRInstruction> out = CUDAGlobalLoadBatching.reorder(list(l0, s0, l1, s1, b, l2, s2, l3, s3));
        assertEquals(list(l0, l1, s0, s1, b, l2, l3, s2, s3), out);
    }

    /**
     * The fp16 staging shape: a global load, a half-to-int conversion, then a shared store.
     * Before {@code HalfBitsToIntStmt} was a {@link CUDALIRStmt.PureRegisterComputation} the
     * conversion ended the run, so an interleaved fp16 kernel was silently skipped rather
     * than optimised.
     */
    @Test
    public void batchesAcrossHalfConversions() {
        Variable h0 = p.variable();
        Variable u0 = p.variable();
        Variable h1 = p.variable();
        Variable u1 = p.variable();

        LIRInstruction l0 = p.globalLoad(h0, p.variable());
        LIRInstruction c0 = p.halfBitsToInt(u0, h0);
        LIRInstruction s0 = p.sharedStore(p.variable(), u0);
        LIRInstruction l1 = p.globalLoad(h1, p.variable());
        LIRInstruction c1 = p.halfBitsToInt(u1, h1);
        LIRInstruction s1 = p.sharedStore(p.variable(), u1);

        assertEquals(list(l0, c0, l1, c1, s0, s1), CUDAGlobalLoadBatching.reorder(list(l0, c0, s0, l1, c1, s1)));
    }

    /** The same for a float conversion, which is the fp16 compute path rather than the packing one. */
    @Test
    public void batchesAcrossFloatConversions() {
        Variable h0 = p.variable();
        Variable f0 = p.variable();
        Variable h1 = p.variable();

        LIRInstruction l0 = p.globalLoad(h0, p.variable());
        LIRInstruction c0 = p.halfToFloat(f0, h0);
        LIRInstruction s0 = p.sharedStore(p.variable(), f0);
        LIRInstruction l1 = p.globalLoad(h1, p.variable());

        assertEquals(list(l0, c0, l1, s0), CUDAGlobalLoadBatching.reorder(list(l0, c0, s0, l1)));
    }

    /** A conversion that redefines a value the store reads is still refused. */
    @Test
    public void refusesWhenAHoistedConversionWouldClobberAStoredValue() {
        Variable reused = p.variable(11);
        LIRInstruction s = p.sharedStore(p.variable(), reused);
        LIRInstruction c = p.halfBitsToInt(reused, p.variable()); // redefines what s reads
        LIRInstruction l = p.globalLoad(p.variable(), p.variable());

        List<LIRInstruction> in = list(s, c, l);
        assertSame(in, CUDAGlobalLoadBatching.reorder(in));
    }

    /** A move is a pure register computation, so it floats above a sunk store. */
    @Test
    public void hoistsPastAMove() {
        Variable v = p.variable();
        Variable m = p.variable();
        LIRInstruction s = p.sharedStore(p.variable(), v);
        LIRInstruction mv = p.move(m, p.variable());
        LIRInstruction l = p.globalLoad(p.variable(), m);

        assertEquals(list(mv, l, s), CUDAGlobalLoadBatching.reorder(list(s, mv, l)));
    }

    /** An empty block and a block with nothing eligible come back as-is. */
    @Test
    public void leavesIneligibleBlocksAlone() {
        List<LIRInstruction> empty = list();
        assertSame(empty, CUDAGlobalLoadBatching.reorder(empty));

        List<LIRInstruction> opaque = list(p.opaque(p.variable(), p.variable()), p.barrier());
        assertSame(opaque, CUDAGlobalLoadBatching.reorder(opaque));
    }

    /** The LIR the caller owns is never mutated; a reorder returns a new list. */
    @Test
    public void neverMutatesTheInputList() {
        Variable v0 = p.variable();
        Variable v1 = p.variable();
        LIRInstruction l0 = p.globalLoad(v0, p.variable());
        LIRInstruction s0 = p.sharedStore(p.variable(), v0);
        LIRInstruction l1 = p.globalLoad(v1, p.variable());
        LIRInstruction s1 = p.sharedStore(p.variable(), v1);

        List<LIRInstruction> in = list(l0, s0, l1, s1);
        List<LIRInstruction> snapshot = list(l0, s0, l1, s1);
        List<LIRInstruction> out = CUDAGlobalLoadBatching.reorder(in);

        assertNotSame(in, out);
        assertEquals(snapshot, in);
    }
}
