package uk.ac.manchester.tornado.unittests.api;

import org.junit.Test;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.TestHello;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestCommonBuffer extends TornadoTestBase {

    @Test
    public void test01() throws TornadoExecutionPlanException {
        int numElements = 16;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);

        TaskGraph tg1 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestHello::add, a, b, c) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, c);

        TaskGraph tg2 = new TaskGraph("s1") //
                .consumeFromDevice(c) //
                .task("t1", TestHello::add, c, c, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);


        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(tg1.snapshot(), tg2.snapshot())) {

            // Select graph 1 (tg2) to execute
            // Once selected, every time we call the execute method,
            // TornadoVM will launch the passed task-graph.
            executionPlan.withGraph(0).execute();
            //

            // Select the graph 0 (tg1) to execute
            TornadoExecutionResult execute = executionPlan.withGraph(1).execute();

            execute.transferToHost(c);

            for (int i=0; i < a.getSize(); i++) {
                System.out.println(c.get(i));
            }

            // Select all graphs (tg1 and tg2) to execute.
            // Since we selected individual task-graphs, we should be
            // able to reverse this action and invoke all task-graph
            // again. This is achieved with the `withAllGraphs` from the
            // execution plan.
            //            executionPlan.withAllGraphs().execute();
        }
    }
}
