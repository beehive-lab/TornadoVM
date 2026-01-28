package uk.ac.manchester.tornado.unittests.fields;

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static org.junit.Assert.assertEquals;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fields.TestSameVariableDifferentClass
 * </code>
 */
public class TestSameVariableDifferentClass extends TornadoTestBase {


    /**
     * Primitive
     *
     */
    public static class ClassePrimitive {
        public final OggettoPrimitive oggetto1;
        public final OggettoPrimitive oggetto2;

        public ClassePrimitive(float[] x) {
            oggetto1 = new OggettoPrimitive(x);
            oggetto2 = new OggettoPrimitive(x);
        }
    }

    public static class OggettoPrimitive {
        public final float[] x;

        public OggettoPrimitive(float[] x) {
            this.x = x;
        }
    }

    public static void increment(ClassePrimitive a) {
        for (@Parallel int i = 0; i < 1000; i++) {
            a.oggetto1.x[i] += 1f;
            a.oggetto2.x[i] += 1f;
        }
    }

    public static void increment(float[] x) {
        for (@Parallel int i = 0; i < 1000; i++) {
            x[i] += 1f;
        }
    }

    @Test
    public void testIncrementSameVariableFromArrayAndObjectPrimitive() {
        float[] x = new float[1000];
        ClassePrimitive a = new ClassePrimitive(x);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, a, x);
        tg.task("t0", TestSameVariableDifferentClass::increment, a);
        tg.task("t1", TestSameVariableDifferentClass::increment, x);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, a, x);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(3f, a.oggetto1.x[0], 0.0);
            assertEquals(3f, a.oggetto2.x[0], 0.0);
            assertEquals(3f, x[0], 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testIncrementSameVariableFromObjectPrimitive() {
        float[] x = new float[1000];
        ClassePrimitive a = new ClassePrimitive(x);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, a);
        tg.task("t0", TestSameVariableDifferentClass::increment, a);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(2f, a.oggetto1.x[0], 0.0);
            assertEquals(2f, a.oggetto2.x[0], 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testIncrementSameVariableFromArraysPrimitive() {
        float[] x = new float[1000];
        float[] y = x;

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, x, y);
        tg.task("t0", TestSameVariableDifferentClass::increment, x);
        tg.task("t1", TestSameVariableDifferentClass::increment, y);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, x, y);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(2f, x[0], 0.0);
            assertEquals(2f, y[0], 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Tornado-Native
     */
    public static class ClasseTornado {
        public final OggettoTornado oggetto1;
        public final OggettoTornado oggetto2;

        public ClasseTornado(FloatArray x) {
            oggetto1 = new OggettoTornado(x);
            oggetto2 = new OggettoTornado(x);
        }
    }

    public static class OggettoTornado {
        public final FloatArray x;

        public OggettoTornado(FloatArray x) {
            this.x = x;
        }
    }

    public static void increment(ClasseTornado a) {
        for (@Parallel int i = 0; i < 1000; i++) {
            a.oggetto1.x.set(i, a.oggetto1.x.get(i) + 1f);
            a.oggetto1.x.set(i, a.oggetto2.x.get(i) + 1f);
        }
    }

    public static void increment(FloatArray x) {
        for (@Parallel int i = 0; i < 1000; i++) {
            x.set(i, x.get(i) + 1f);
        }
    }

    @Test
    public void testIncrementSameVariableFromArrayAndObjectTornado() {
        FloatArray x = new FloatArray(1000);
        ClasseTornado a = new ClasseTornado(x);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, a, x);
        tg.task("t0", TestSameVariableDifferentClass::increment, a);
        tg.task("t1", TestSameVariableDifferentClass::increment, x);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, a, x);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(3f, a.oggetto1.x.get(0), 0.0);
            assertEquals(3f, a.oggetto2.x.get(0), 0.0);
            assertEquals(3f, x.get(0), 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testIncrementSameVariableFromObjectTornado() {
        FloatArray x = new FloatArray(1000);
        ClasseTornado a = new ClasseTornado(x);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, a);
        tg.task("t0", TestSameVariableDifferentClass::increment, a);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(2f, a.oggetto1.x.get(0), 0.0);
            assertEquals(2f, a.oggetto2.x.get(0), 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testIncrementSameVariableFromArraysTornado() {
        FloatArray x = new FloatArray(1000);
        FloatArray y = x;

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, x, y);
        tg.task("t0", TestSameVariableDifferentClass::increment, x);
        tg.task("t1", TestSameVariableDifferentClass::increment, y);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, x, y);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            assertEquals(2f, x.get(0), 0.0);
            assertEquals(2f, y.get(0), 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
