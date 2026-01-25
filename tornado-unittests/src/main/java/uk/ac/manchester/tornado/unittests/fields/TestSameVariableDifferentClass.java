package uk.ac.manchester.tornado.unittests.fields;

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fields.TestSameVariableDifferentClass
 * </code>
 */
public class TestSameVariableDifferentClass extends TornadoTestBase {

    public static class Classe {
        public final Oggetto oggetto1;
        public final Oggetto oggetto2;

        public Classe(float[] x) {
            oggetto1 = new Oggetto(x);
            oggetto2 = new Oggetto(x);
        }
    }

    public static class Oggetto {
        public final float[] x;

        public Oggetto(float[] x) {
            this.x = x;
        }
    }

    public static void increment(Classe a) {
        for (@Parallel int i = 0; i < 1000; i++) {
            a.oggetto1.x[i] += 1f;
            a.oggetto2.x[i] += 1f;
        }
    }

    @Test
    public void testIncrementSameVariable(){
        float[] x = new float[1000];
        Classe a = new Classe(x);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.FIRST_EXECUTION, a);
        tg.task("t0", TestSameVariableDifferentClass::increment, a);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, a);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())) {
            executionPlan.execute();
            if (a.oggetto1.x[0] != 2f || a.oggetto2.x[0] != 2f) {
                throw new IllegalArgumentException("Non uguali! " + a.oggetto1.x[0] + " != " + a.oggetto2.x[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
