package uk.ac.manchester.tornado.unittests.fields;

import org.junit.Before;
import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMPTXNotSupported;

import static org.junit.Assert.assertEquals;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fields.TestInheritedFields
 * </code>
 */
public class TestInheritedFields extends TornadoTestBase {

    public static void incrementPrimitiveA(PrimitiveA a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            a.valueA[i] += 1f;
        }
    }

    public static void incrementPrimitiveB(PrimitiveB b, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            b.valueA[i] += 1f;
            b.valueB[i] += 1f;
        }
    }

    public static void incrementTornadoA(TornadoA a, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            a.valueA.set(i, a.valueA.get(i) + 1f);
        }
    }

    public static void incrementTornadoB(TornadoB b, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            b.valueA.set(i, b.valueA.get(i) + 1f);
            b.valueB.set(i, b.valueB.get(i) + 1f);
        }
    }

    /**
     * Prevent test from running on PTX backends.
     */
    @Before
    public void backendCheck() {
        if (TornadoExecutionPlan.DEFAULT_DEVICE.getTornadoVMBackend() == TornadoVMBackendType.PTX) {
            throw new TornadoVMPTXNotSupported("Test designed to run with multiple backends, including a PTX backend");
        }
    }

    @Test
    public void testIncrementPrimitiveB() {
        final int N = 1000;
        PrimitiveB b = new PrimitiveB(N);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, b);
        tg.task("t0", TestInheritedFields::incrementPrimitiveB, b, N);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(1f, b.valueA[0], 0.0);
            assertEquals(1f, b.valueB[0], 0.0);
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testIncrementPrimitiveAB() {
        final int N = 1000;
        PrimitiveB b = new PrimitiveB(N);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, b);
        tg.task("t0", TestInheritedFields::incrementPrimitiveA, b, N);
        tg.task("t1", TestInheritedFields::incrementPrimitiveB, b, N);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(2f, b.valueA[0], 0.0);
            assertEquals(1f, b.valueB[0], 0.0);
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testIncrementTornadoB() {
        final int N = 1000;
        TornadoB b = new TornadoB(N);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, b);
        tg.task("t0", TestInheritedFields::incrementTornadoB, b, N);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(1f, b.valueA.get(0), 0.0);
            assertEquals(1f, b.valueB.get(0), 0.0);
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testIncrementTornadoAB() {
        final int N = 1000;
        TornadoB b = new TornadoB(N);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, b);
        tg.task("t0", TestInheritedFields::incrementTornadoA, b, N);
        tg.task("t1", TestInheritedFields::incrementTornadoB, b, N);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(2f, b.valueA.get(0), 0.0);
            assertEquals(1f, b.valueB.get(0), 0.0);
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
        }
    }

    public static class PrimitiveA {
        public final float[] valueA;

        public PrimitiveA(int size) {
            valueA = new float[size];
        }
    }

    public static class PrimitiveB extends PrimitiveA {
        public final float[] valueB;

        public PrimitiveB(int size) {
            super(size);
            valueB = new float[size];
        }
    }

    /**
     * tornado native types
     */

    public static class TornadoA {
        public final FloatArray valueA;

        public TornadoA(int size) {
            valueA = new FloatArray(size);
        }
    }

    public static class TornadoB extends TornadoA {
        public final FloatArray valueB;

        public TornadoB(int size) {
            super(size);
            valueB = new FloatArray(size);
        }
    }
}
