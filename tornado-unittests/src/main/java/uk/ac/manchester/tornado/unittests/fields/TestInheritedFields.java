package uk.ac.manchester.tornado.unittests.fields;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.fields.TestInheritedFields
 * </code>
 */
public class TestInheritedFields extends TornadoTestBase {

    public static class A{
        public final float[] valueA;
        public A(int size){
            valueA = new float[size];
        }
    }

    public static class B extends A{
        public final float[] valueB;
        public B(int size){
            super(size);
            valueB = new float[size];
        }
    }

    public static void incrementA(A a, int size){
        for(@Parallel int i = 0; i<size; i++){
            a.valueA[i] += 1f;
        }
    }

    public static void incrementB(B b, int size){
        for(@Parallel int i = 0; i<size; i++){
            b.valueA[i] += 1f;
            b.valueB[i] += 1f;
        }
    }

    @Test
    public void testIncrementB(){
        final int N = 1000;
        B b = new B(N);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, b);
        tg.task("t0", TestInheritedFields::incrementB, b, N);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try(TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())){
            executionPlan.execute();
            assertTrue(b.valueA[0] == 1f);
            assertTrue(b.valueB[0] == 1f);
        }catch (TornadoExecutionPlanException e){
            e.printStackTrace();
        }
    }

    @Test
    public void testIncrementAB(){
        final int N = 1000;
        B b = new B(N);

        TaskGraph tg = new TaskGraph("tg");
        tg.transferToDevice(DataTransferMode.EVERY_EXECUTION, b);
        tg.task("t0", TestInheritedFields::incrementA, b, N);
        tg.task("t1", TestInheritedFields::incrementB, b, N);
        tg.transferToHost(DataTransferMode.EVERY_EXECUTION, b);

        try(TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg.snapshot())){
            executionPlan.execute();
            assertTrue(b.valueA[0] == 2f);
            assertTrue(b.valueB[0] == 1f);
        }catch (TornadoExecutionPlanException e){
            e.printStackTrace();
        }
    }
}
