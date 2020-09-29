package uk.ac.manchester.tornado.examples;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TornadoVMContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;

import uk.ac.manchester.tornado.api.TaskSchedule;

public class TestReductionTornadoVMContext {

    // Reduction in Global memory using the API 2.0
    public static void reduction(float[] a, float[] b, TornadoVMContext context) {
        int localIdx = context.localIdx;
        int localGroupSize = context.getLocalGroupSize(0);
        int groupID = context.groupIdx; // Expose Group ID
        int id = localGroupSize * groupID + localIdx;

        for (int stride = (localGroupSize / 2); stride > 0; stride /= 2) {
            context.localBarrier();
            if (localIdx < stride) {
                a[id] += a[id + stride];
            }
        }
        context.globalBarrier();
        if (localIdx == 0) {
            b[groupID] = a[id];
        }
    }

    public static float computeSequential(float[] input) {
        float acc = 0;
        for (float v : input) {
            acc += v;
        }
        return acc;
    }

    public static void runEmulator(float[] input, float[] reduce) throws IllegalAccessException {
        WorkerGrid worker = new WorkerGrid1D(16);
        worker.setLocalWork(8, 1, 1);
        TornadoVMContext context = new TornadoVMContext(worker);

        Class<?> klass = context.getClass();
        Field fieldA = null;
        Field fieldB = null;
        try {
            fieldA = klass.getDeclaredField("localIdx");
            fieldA.setAccessible(true);

            fieldB = klass.getDeclaredField("groupID");
            fieldB.setAccessible(true);

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        final int numGroups = context.getGlobalGroupSize(0) / context.getLocalGroupSize(0);
        for (int j = (context.getLocalGroupSize(0) - 1); j >= 0; j--) {
            for (int i = (numGroups - 1); i >= 0; i--) {
                fieldA.set(context, j);
                fieldB.set(context, i);
                reduction(input, reduce, context);
            }
        }
    }

    public static void main(String[] args) {
        final int size = 1024;
        float[] input = new float[size];
        float[] reduce = new float[2];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        float sequential = computeSequential(input);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.set("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(input).task("t0", TestReductionTornadoVMContext::reduction, input, reduce, context).streamOut(reduce);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(size, 1, 1);
        s0.execute(gridTask);

        // Final SUM
        int finalSum = 0;
        for (float v : reduce) {
            System.out.println(v);
            finalSum += v;
        }

        System.out.println("Final SUM = " + finalSum + " vs seq= " + sequential);
        if ((sequential - finalSum) == 0) {
            System.out.println("Result is correct");
        } else {
            System.out.println("Result is wrong");
        }
    }
}