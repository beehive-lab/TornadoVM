/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.unittests.tile;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.api.tile.PartitionView;
import uk.ac.manchester.tornado.api.tile.Tile;
import uk.ac.manchester.tornado.api.tile.TileContext;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * The recurrent gated delta rule from NVIDIA's TileGym
 * ({@code ops/cutile/recurrent_gated_delta_rule.py}): linear attention as a recurrence over a
 * state matrix.
 *
 * <p>
 * This is structurally unlike every other kernel in the suite. There is no {@code mma} in it at
 * all: the state update is an outer product, expressed as a broadcast multiply of a
 * {@code [K, 1]} key column by a {@code [1, V]} delta row, and the readouts are reductions along
 * the state's key axis. What is carried across the loop is a whole {@code [K, V]} state tile,
 * updated every time step, which is the hardest thing to keep in registers and the reason this
 * kernel exists in tile form rather than as a sequence of GEMMs.
 * </p>
 *
 * <p>
 * Per step, following the reference implementation:
 * </p>
 *
 * <pre>
 * state  = state * exp(gate)                      decay
 * kvMem  = sum(state * key_column, axis 0)         what the state already predicts
 * delta  = (value - kvMem) * beta                  the correction
 * state  = state + key_column * delta_row          rank-1 update
 * out    = sum(state * query_column, axis 0)       readout
 * </pre>
 *
 * <p>
 * The gate and beta are per-step scalars, and they arrive as {@code [1, 1]} tiles loaded from
 * their own views rather than as Java floats: that keeps the exponential on the device and out
 * of scalar code, and it is the only way to get a runtime scalar into a tile expression without
 * a {@code full(...)}, whose fill value has to fold.
 * </p>
 *
 * <pre>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tile.TestTileRecurrence
 * </pre>
 */
public class TestTileRecurrence extends TornadoTestBase {

    /** Key width, and the state's first dimension. */
    private static final int KEYS = 32;

    /** Value width, and the state's second dimension. */
    private static final int VALUES = 32;

    @Before
    public void tileMustBeAvailable() {
        TileSupport.requireTileSupport();
    }

    /**
     * One head of the recurrence over {@code steps} time steps.
     *
     * <p>
     * Keys and queries are stored {@code [steps, KEYS]} and loaded as a row, then reshaped to a
     * column so the broadcast lands on the state's key axis. Values and outputs are
     * {@code [steps, VALUES]} rows.
     * </p>
     *
     * <p>
     * The emitted kernel, from
     * {@code tornado-test --printKernel -V ...TestTileRecurrence#testDeltaRuleLongerSequence}
     * at 32 steps. The seven view constructions and the reserved ABI parameters are elided:
     * </p>
     *
     * <pre>{@code
     * extern "C" __tile_global__ void gatedDeltaRule(<4 reserved>, <7 buffers>, int arg8)
     * {
     *   unsigned long long ul_0, ul_1, ul_2, ul_3, ul_4, ul_5, ul_6;
     *   int i_16, i_40;
     *   ct::tile<float, ct::shape<32, 32>> ul_15;        // <- the loop-carried state
     *
     *   // BLOCK 0
     *   ... seven partition_view constructions ...
     *   ct::tile<float, ct::shape<32, 32>> tile_14 = ct::full<ct::tile<float, ct::shape<32, 32>>>(0.0f);
     *   // BLOCK 1 MERGES [0 2 ]
     *   ul_15  =  tile_14;
     *   i_16  =  0;
     *   for(;i_16 < 32;)
     *   {
     *     // BLOCK 2
     *     auto tile_17 = tview_10.load(i_16, 0);                 // the gate, a [1, 1] tile
     *     auto tile_18 = ct::exp(tile_17);                       // exp stays on the device
     *     auto tile_19 = ct::broadcast(tile_18, ct::shape<32, 32>{});
     *     auto tile_20 = ul_15 * tile_19;                        // state = state * decay
     *     auto tile_21 = tview_7.load(i_16, 0);                  // a key row
     *     auto tile_22 = ct::reshape(tile_21, ct::shape<32, 1>{});
     *     auto tile_23 = ct::broadcast(tile_22, ct::shape<32, 32>{});
     *     auto tile_24 = tile_20 * tile_23;
     *     auto tile_25 = ct::sum(tile_24, 0_ic);                 // what the state predicts
     *     auto tile_26 = tview_11.load(i_16, 0);                 // beta
     *     auto tile_27 = ct::broadcast(tile_26, ct::shape<1, 32>{});
     *     auto tile_28 = tview_9.load(i_16, 0);                  // the value row
     *     auto tile_29 = tile_28 - tile_25;
     *     auto tile_30 = tile_29 * tile_27;                      // delta
     *     auto tile_31 = ct::broadcast(tile_22, ct::shape<32, 32>{});
     *     auto tile_32 = ct::broadcast(tile_30, ct::shape<32, 32>{});
     *     auto tile_33 = tile_31 * tile_32;                      // the rank-1 update
     *     auto tile_34 = tile_20 + tile_33;                      // the new state
     *     auto tile_35 = tview_8.load(i_16, 0);                  // a query row
     *     auto tile_36 = ct::reshape(tile_35, ct::shape<32, 1>{});
     *     auto tile_37 = ct::broadcast(tile_36, ct::shape<32, 32>{});
     *     auto tile_38 = tile_34 * tile_37;
     *     auto tile_39 = ct::sum(tile_38, 0_ic);                 // the readout
     *     tview_12.store(tile_39, i_16, 0);
     *     i_40  =  i_16 + 1;
     *     ul_15  =  tile_34;                                     // <- the state travels round
     *     i_16  =  i_40;
     *   }  // B1
     *   // BLOCK 3
     *   tview_13.store(ul_15, 0, 0);
     *   return;
     * }
     * }</pre>
     *
     * <p>
     * The time loop survives as a real backward branch rather than being unrolled, and the
     * interesting line is the declaration of {@code ul_15}. Every other tile in the body is an
     * {@code auto} bound once, because the emitter is walking SSA values; the state is the one
     * value that merges from two predecessors, so it becomes a named, explicitly typed
     * {@code ct::tile<float, ct::shape<32, 32>>} at function scope with an assignment at the
     * latch. That declaration is the phi node, and it is why a loop-carried tile needs the
     * declared-type side table rather than {@code auto} - which is the part of the backend this
     * kernel exercises that no straight-line kernel does.
     * </p>
     *
     * <p>
     * Two smaller things to read off it. {@code steps} has been constant-folded into both the
     * loop bound and the view extents, so a different step count is a different compilation.
     * And {@code ct::broadcast(tile_22, ...)} is emitted twice, at {@code tile_23} and
     * {@code tile_31}: the same Java expression appears twice in the source and nothing is
     * common-subexpression-eliminated at this level, which leaves the tile compiler to decide
     * whether that is one broadcast or two.
     * </p>
     */
    public static void gatedDeltaRule(TileContext tc, FloatArray keys, FloatArray queries, FloatArray values, FloatArray gates, FloatArray betas, FloatArray out,
            FloatArray finalState, int steps) {
        PartitionView keyView = tc.partition(tc.view(keys, steps, KEYS), 1, KEYS);
        PartitionView queryView = tc.partition(tc.view(queries, steps, KEYS), 1, KEYS);
        PartitionView valueView = tc.partition(tc.view(values, steps, VALUES), 1, VALUES);
        PartitionView gateView = tc.partition(tc.view(gates, steps, 1), 1, 1);
        PartitionView betaView = tc.partition(tc.view(betas, steps, 1), 1, 1);
        PartitionView outView = tc.partition(tc.view(out, steps, VALUES), 1, VALUES);
        PartitionView stateView = tc.partition(tc.view(finalState, KEYS, VALUES), KEYS, VALUES);

        Tile state = tc.zeros(DType.F32, KEYS, VALUES);

        for (int step = 0; step < steps; step++) {
            // The decay is a per-step scalar, so it travels as a [1, 1] tile and the
            // exponential happens on the device.
            Tile decay = tc.broadcast(tc.exp(gateView.load(step, 0)), KEYS, VALUES);
            state = tc.mul(state, decay);

            // A key as a column, so the broadcast multiplies along the state's key axis.
            Tile keyColumn = tc.reshape(keyView.load(step, 0), KEYS, 1);
            Tile predicted = tc.sum(tc.mul(state, tc.broadcast(keyColumn, KEYS, VALUES)), 0);

            Tile beta = tc.broadcast(betaView.load(step, 0), 1, VALUES);
            Tile delta = tc.mul(tc.sub(valueView.load(step, 0), predicted), beta);

            // The rank-1 update: a [KEYS, 1] column times a [1, VALUES] row.
            state = tc.add(state, tc.mul(tc.broadcast(keyColumn, KEYS, VALUES), tc.broadcast(delta, KEYS, VALUES)));

            Tile queryColumn = tc.reshape(queryView.load(step, 0), KEYS, 1);
            outView.store(tc.sum(tc.mul(state, tc.broadcast(queryColumn, KEYS, VALUES)), 0), step, 0);
        }

        stateView.store(state, 0, 0);
    }

    // -------------------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------------------

    private void checkDeltaRule(int steps) throws TornadoExecutionPlanException {
        FloatArray keys = new FloatArray(steps * KEYS);
        FloatArray queries = new FloatArray(steps * KEYS);
        FloatArray values = new FloatArray(steps * VALUES);
        FloatArray gates = new FloatArray(steps);
        FloatArray betas = new FloatArray(steps);
        FloatArray out = new FloatArray(steps * VALUES);
        FloatArray finalState = new FloatArray(KEYS * VALUES);

        Random random = new Random(1901 + steps);
        for (int i = 0; i < steps * KEYS; i++) {
            // Small keys and queries: the state is a running sum of outer products, and a
            // recurrence with large inputs diverges rather than testing anything.
            keys.set(i, 0.2f * (random.nextFloat() - 0.5f));
            queries.set(i, 0.2f * (random.nextFloat() - 0.5f));
        }
        for (int i = 0; i < steps * VALUES; i++) {
            values.set(i, random.nextFloat() - 0.5f);
        }
        for (int i = 0; i < steps; i++) {
            // A decaying gate, as the reference uses: exp of a small negative number.
            gates.set(i, -0.05f * random.nextFloat());
            betas.set(i, 0.5f + 0.5f * random.nextFloat());
        }

        // The whole recurrence is one tile block: the state cannot be split across blocks.
        GridScheduler grid = new GridScheduler("delta.k", new WorkerGrid1D(1));
        TaskGraph graph = new TaskGraph("delta") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, queries, values, gates, betas) //
                .task("k", TestTileRecurrence::gatedDeltaRule, new TileContext(), keys, queries, values, gates, betas, out, finalState, steps) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out, finalState);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(grid).execute();
        }

        // The same recurrence on the host, in double precision.
        double[][] state = new double[KEYS][VALUES];
        double[] expectedOut = new double[steps * VALUES];
        for (int step = 0; step < steps; step++) {
            double decay = Math.exp(gates.get(step));
            for (int key = 0; key < KEYS; key++) {
                for (int value = 0; value < VALUES; value++) {
                    state[key][value] *= decay;
                }
            }
            double[] predicted = new double[VALUES];
            for (int value = 0; value < VALUES; value++) {
                for (int key = 0; key < KEYS; key++) {
                    predicted[value] += state[key][value] * keys.get(step * KEYS + key);
                }
            }
            double[] delta = new double[VALUES];
            for (int value = 0; value < VALUES; value++) {
                delta[value] = (values.get(step * VALUES + value) - predicted[value]) * betas.get(step);
            }
            for (int key = 0; key < KEYS; key++) {
                for (int value = 0; value < VALUES; value++) {
                    state[key][value] += keys.get(step * KEYS + key) * delta[value];
                }
            }
            for (int value = 0; value < VALUES; value++) {
                double sum = 0.0;
                for (int key = 0; key < KEYS; key++) {
                    sum += state[key][value] * queries.get(step * KEYS + key);
                }
                expectedOut[step * VALUES + value] = sum;
            }
        }

        for (int i = 0; i < steps * VALUES; i++) {
            assertEquals("output " + i, expectedOut[i], out.get(i), 1e-3);
        }
        for (int key = 0; key < KEYS; key++) {
            for (int value = 0; value < VALUES; value++) {
                assertEquals("state (" + key + "," + value + ")", state[key][value], finalState.get(key * VALUES + value), 1e-3);
            }
        }
    }

    /** Four steps: enough for the state to carry information between them. */
    @Test
    public void testDeltaRuleShortSequence() throws TornadoExecutionPlanException {
        checkDeltaRule(4);
    }

    /**
     * Thirty-two steps. The state is read and written every step, so an error in the
     * loop-carried tile compounds rather than staying local - which is what makes a longer
     * sequence a different test and not just a slower one.
     */
    @Test
    public void testDeltaRuleLongerSequence() throws TornadoExecutionPlanException {
        checkDeltaRule(32);
    }

    /** One step: the state starts at zero, so the output is the readout of a single update. */
    @Test
    public void testDeltaRuleSingleStep() throws TornadoExecutionPlanException {
        checkDeltaRule(1);
    }
}
