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
package uk.ac.manchester.tornado.unittests.branching;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * A short-circuiting {@code &&} nested inside an {@code ||} is canonicalised by Graal into a negated
 * node. These tests check that the negation survives code generation: an {@code A || (B && C)} that
 * loses it silently computes {@code A || (!B || !C)}, which agrees with the host for some inputs and
 * not others, so only an exhaustive comparison catches it.
 *
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.branching.TestCompoundBooleans
 * </code>
 */
public class TestCompoundBooleans extends TornadoTestBase {

    /** 16 values of c crossed with 16 values of v: every branch of every predicate below. */
    private static final int SIZE = 256;

    public static void orOfAnd(IntArray c, IntArray v, IntArray out) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            int cc = c.get(i);
            int vv = v.get(i);
            out.set(i, ((cc == 3) || ((cc == 2) && (vv == -1))) ? -1 : 0);
        }
    }

    public static void orOfAndHoisted(IntArray c, IntArray v, IntArray out) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            int cc = c.get(i);
            int vv = v.get(i);
            boolean born = (cc == 3);
            boolean survives = (cc == 2) && (vv == -1);
            out.set(i, (born || survives) ? -1 : 0);
        }
    }

    public static void orOfAndNestedIf(IntArray c, IntArray v, IntArray out) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            int cc = c.get(i);
            int vv = v.get(i);
            int r = 0;
            if (cc == 3) {
                r = -1;
            } else if (cc == 2) {
                if (vv == -1) {
                    r = -1;
                }
            }
            out.set(i, r);
        }
    }

    public static void andOfOr(IntArray c, IntArray v, IntArray out) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            int cc = c.get(i);
            int vv = v.get(i);
            out.set(i, ((cc == 3) && ((cc == 2) || (vv == -1))) ? -1 : 0);
        }
    }

    public static void orOfAndOfAnd(IntArray c, IntArray v, IntArray out) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            int cc = c.get(i);
            int vv = v.get(i);
            out.set(i, ((cc == 3) || ((cc == 2) && ((vv == -1) && (cc != 7)))) ? -1 : 0);
        }
    }

    public static void negatedAnd(IntArray c, IntArray v, IntArray out) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            int cc = c.get(i);
            int vv = v.get(i);
            out.set(i, (!((cc == 2) && (vv == -1))) ? -1 : 0);
        }
    }

    private interface HostPredicate {
        boolean test(int c, int v);
    }

    private void assertMatchesHost(String name, Task3<IntArray, IntArray, IntArray> kernel, HostPredicate host) throws TornadoExecutionPlanException {
        IntArray c = new IntArray(SIZE);
        IntArray v = new IntArray(SIZE);
        IntArray out = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            c.set(i, i / 16);
            v.set(i, (i % 16) - 8);
        }
        out.init(0);

        TaskGraph taskGraph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, c, v) //
                .task("t", kernel, c, v, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < SIZE; i++) {
            int expected = host.test(c.get(i), v.get(i)) ? -1 : 0;
            assertEquals("c=" + c.get(i) + " v=" + v.get(i), expected, out.get(i));
        }
    }

    @Test
    public void testOrOfAnd() throws TornadoExecutionPlanException {
        assertMatchesHost("orOfAnd", TestCompoundBooleans::orOfAnd, (c, v) -> (c == 3) || ((c == 2) && (v == -1)));
    }

    @Test
    public void testOrOfAndHoisted() throws TornadoExecutionPlanException {
        assertMatchesHost("orOfAndHoisted", TestCompoundBooleans::orOfAndHoisted, (c, v) -> (c == 3) || ((c == 2) && (v == -1)));
    }

    @Test
    public void testOrOfAndNestedIf() throws TornadoExecutionPlanException {
        assertMatchesHost("orOfAndNestedIf", TestCompoundBooleans::orOfAndNestedIf, (c, v) -> (c == 3) || ((c == 2) && (v == -1)));
    }

    @Test
    public void testAndOfOr() throws TornadoExecutionPlanException {
        assertMatchesHost("andOfOr", TestCompoundBooleans::andOfOr, (c, v) -> (c == 3) && ((c == 2) || (v == -1)));
    }

    @Test
    public void testOrOfAndOfAnd() throws TornadoExecutionPlanException {
        assertMatchesHost("orOfAndOfAnd", TestCompoundBooleans::orOfAndOfAnd, (c, v) -> (c == 3) || ((c == 2) && ((v == -1) && (c != 7))));
    }

    @Test
    public void testNegatedAnd() throws TornadoExecutionPlanException {
        assertMatchesHost("negatedAnd", TestCompoundBooleans::negatedAnd, (c, v) -> !((c == 2) && (v == -1)));
    }

    // The same predicates written as KernelContext kernels: the grid comes from a GridScheduler and
    // there is no @Parallel loop, which is a different path through the code generator.

    public static void orOfAndContext(KernelContext context, IntArray c, IntArray v, IntArray out) {
        int i = context.globalIdx;
        int cc = c.get(i);
        int vv = v.get(i);
        out.set(i, ((cc == 3) || ((cc == 2) && (vv == -1))) ? -1 : 0);
    }

    public static void orOfAndHoistedContext(KernelContext context, IntArray c, IntArray v, IntArray out) {
        int i = context.globalIdx;
        int cc = c.get(i);
        int vv = v.get(i);
        boolean born = cc == 3;
        boolean survives = (cc == 2) && (vv == -1);
        out.set(i, (born || survives) ? -1 : 0);
    }

    public static void orOfAndNestedIfContext(KernelContext context, IntArray c, IntArray v, IntArray out) {
        int i = context.globalIdx;
        int cc = c.get(i);
        int vv = v.get(i);
        int r = 0;
        if (cc == 3) {
            r = -1;
        } else if (cc == 2) {
            if (vv == -1) {
                r = -1;
            }
        }
        out.set(i, r);
    }

    private void assertContextMatchesHost(String name, Task4<KernelContext, IntArray, IntArray, IntArray> kernel, HostPredicate host) throws TornadoExecutionPlanException {
        IntArray c = new IntArray(SIZE);
        IntArray v = new IntArray(SIZE);
        IntArray out = new IntArray(SIZE);
        for (int i = 0; i < SIZE; i++) {
            c.set(i, i / 16);
            v.set(i, (i % 16) - 8);
        }
        out.init(0);

        KernelContext context = new KernelContext();
        WorkerGrid worker = new WorkerGrid1D(SIZE);
        worker.setGlobalWork(SIZE, 1, 1);
        worker.setLocalWork(64, 1, 1);
        GridScheduler gridScheduler = new GridScheduler(name + ".t", worker);

        TaskGraph taskGraph = new TaskGraph(name) //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, c, v) //
                .task("t", kernel, context, c, v, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            plan.withGridScheduler(gridScheduler).execute();
        }

        for (int i = 0; i < SIZE; i++) {
            int expected = host.test(c.get(i), v.get(i)) ? -1 : 0;
            assertEquals("c=" + c.get(i) + " v=" + v.get(i), expected, out.get(i));
        }
    }

    @Test
    public void testOrOfAndKernelContext() throws TornadoExecutionPlanException {
        assertContextMatchesHost("ctxOrOfAnd", TestCompoundBooleans::orOfAndContext, (c, v) -> (c == 3) || ((c == 2) && (v == -1)));
    }

    @Test
    public void testOrOfAndHoistedKernelContext() throws TornadoExecutionPlanException {
        assertContextMatchesHost("ctxHoisted", TestCompoundBooleans::orOfAndHoistedContext, (c, v) -> (c == 3) || ((c == 2) && (v == -1)));
    }

    @Test
    public void testOrOfAndNestedIfKernelContext() throws TornadoExecutionPlanException {
        assertContextMatchesHost("ctxNestedIf", TestCompoundBooleans::orOfAndNestedIfContext, (c, v) -> (c == 3) || ((c == 2) && (v == -1)));
    }
}
