package uk.ac.manchester.tornado.examples;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntime;

import java.util.stream.IntStream;


public class TestMultiContext {

    public static void task0Initialization(int[] a) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = 10;
        }
    }

    public static void task1Multiplication(int[] a, int alpha) {
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * alpha;
        }
    }

    public static void main(String[] args) {

        final int numElements = 8192;
        int[] a = new int[numElements];
        int[] b = new int[numElements];
        int devices = TornadoRuntime.getTornadoRuntime().getDriver(0).getDeviceCount();

        IntStream.range(0, numElements).forEach(i -> {
            a[i] = 30;
            b[i] = 10;
        });

        if (devices == 1) {
//            assertTrue("This test needs at least 2 OpenCL-compatible devices.", devices == 1);
        } else {
            System.setProperty("tornado.debug", "true");
            System.setProperty("s0.t0.device", "0:0");
            System.setProperty("s0.t1.device", "0:1");
        }
        System.setProperty("s0.t0.device", "0:0");
        System.setProperty("s0.t1.device", "0:1");
        TaskGraph taskGraph = new TaskGraph("s0")//
                .task("t0", TestMultiContext::task0Initialization, b) //z
                .task("t1", TestMultiContext::task1Multiplication, a, 12) //
//                .transferToHost(DataTransferMode.EVERY_EXECUTION, b, a); //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, a, b); //
//        taskGraph.s
        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();

        TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executionPlan.execute();

        for (int i = 0; i < a.length; i++) {
//            assert(10, b[i]);
//            assertEquals(360, a[i]);
        }
    }
}
