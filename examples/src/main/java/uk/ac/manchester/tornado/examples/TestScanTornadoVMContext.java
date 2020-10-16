package uk.ac.manchester.tornado.examples;

import uk.ac.manchester.tornado.api.GridTask;
import uk.ac.manchester.tornado.api.TornadoVMContext;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;

import java.util.stream.IntStream;

/**
 * Example of how to express a SCAN operation in TornadoVM using the new API
 *
 */
public class TestScanTornadoVMContext {

    public static int sumScratch(int[] scratch, TornadoVMContext context) {
        int lid = context.localIdx;
        int lsz = context.getLocalGroupSize(0);
        context.localBarrier();
        for (int step = 2; step <= lsz; step <<= 1) {
            if (((lid + 1) % step) == 0) {
                scratch[lid] += scratch[lid - (step >> 1)];
            }
            context.localBarrier();
        }
        int sum = 0;
        if ((lid + 1) == lsz) {
            sum = scratch[lid];
            scratch[lid] = 0;
        }

        context.localBarrier();
        for (int step = lsz; step > 1; step >>= 1) {
            if (((lid + 1) % step) == 0) {
                int prev = scratch[lid - (step >> 1)];
                scratch[lid - (step >> 1)] = scratch[lid];
                scratch[lid] += prev;
            }
            context.localBarrier();
        }
        return sum;
    }

    // 4 2 3 2 6 1 2 3
    // \ | \ | \ | \ |
    // \ | \ | \ | \ |
    // \ | \ | \ | \ |
    // \| \| \| \|
    // + + + +
    // 4 6 3 5 6 7 2 5
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \ | \ |
    // \| \|
    // + +
    // 4 6 3 11 6 7 2 12
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ | This last pass can be ommitted!
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \ |
    // \|
    // +
    // 4 6 3 11 6 7 2 23
    // |
    // overwrite with 0
    // |
    // V
    // 4 6 3 11 6 7 2 0
    // \ /|
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // \ / |
    // / |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \ |
    // / \|
    // V +
    // 4 6 3 0 6 7 2 11
    // \ /| \ / |
    // \ / | \ / |
    // \ / | \ / |
    // \/ | \/ |
    // /\ | /\ |
    // / \ | / \ |
    // / \ | / \ |
    // / \| / \ |
    // V + V +
    // 4 0 3 6 6 11 2 18
    // \ /| \ /| \ /| \ /|
    // \/ | \/ | \/ | \/ |
    // /\ | /\ | /\ | /\ |
    // / \| / \| / \| / \|
    // V + V + V + V +
    // 0 4 6 9 11 17 18 20
    public static void prefixKernel(int[] data, TornadoVMContext context) {
        int lid = context.localIdx;
        int lsz = context.getLocalGroupSize(0);
        int gid = context.groupIdx;
        int[] scratch = context.allocateIntLocalArray(1024); // int.class
        scratch[lid] = data[gid]; // copy into local scratch for the reduction

        // Inline method sumScratch:
        int sum = sumScratch(scratch, context);
        // context.localBarrier();
        // for (int step = 2; step <= lsz; step <<= 1) {
        // if (((lid + 1) % step) == 0) {
        // scratch[lid] += scratch[lid - (step >> 1)];
        // }
        // context.localBarrier();
        // }
        // int sum = 0;
        // if ((lid + 1) == lsz) {
        // sum = scratch[lid];
        // scratch[lid] = 0;
        // }
        //
        // context.localBarrier();
        // for (int step = lsz; step > 1; step >>= 1) {
        // if (((lid + 1) % step) == 0) {
        // int prev = scratch[lid - (step >> 1)];
        // scratch[lid - (step >> 1)] = scratch[lid];
        // scratch[lid] += prev;
        // }
        // context.localBarrier();
        // }
        // //

        if ((lid + 1) == lsz) {
            data[gid] = sum;
        } else {
            data[gid] = scratch[lid + 1];
        }
    }

    static void javaPrefixSum(int arr[], int n, int prefixSum[]) {
        prefixSum[0] = arr[0];

        // Adding present element
        // with previous element
        for (int i = 1; i < n; ++i)
            prefixSum[i] = prefixSum[i - 1] + arr[i];
    }

    public static void main(String[] args) {
        final int size = 1024;
        final int localSize = 1024;
        int[] input = new int[size];
        int[] javaOutput = new int[size];
        IntStream.range(0, input.length).sequential().forEach(i -> input[i] = i);
        javaPrefixSum(input, size, javaOutput);

        WorkerGrid worker = new WorkerGrid1D(size);
        GridTask gridTask = new GridTask();
        gridTask.set("s0.t0", worker);
        TornadoVMContext context = new TornadoVMContext(worker);

        TaskSchedule s0 = new TaskSchedule("s0").streamIn(input, localSize).task("t0", TestScanTornadoVMContext::prefixKernel, input, context).streamOut(input);
        // Change the Grid
        worker.setGlobalWork(size, 1, 1);
        worker.setLocalWork(localSize, 1, 1);
        s0.execute(gridTask);

        for (int v = 0; v < input.length; v++) {
            if (v < 10) {
                System.out.println(input[v] + " - " + javaOutput[v]);
            }
        }

        // System.out.println("Final SUM = " + finalSum + " vs seq= " + sequential);
        // if ((sequential - finalSum) == 0) {
        // System.out.println("Result is correct");
        // } else {
        // System.out.println("Result is wrong");
        // }
    }
}
