public class Compute {
    private static void mxmKernel(KernelContext context, Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        int idx = context.threadIdx;
        int jdx = context.threadIdy;
        float sum = 0;
        for (int k = 0; k < size; k++) {
            sum += A.get(idx, k) * B.get(k, jdx);
        }
        C.set(idx, jdx, sum);
    }

    public void run(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        // When using the kernel-parallel API, we need to create a Grid and a Worker

        WorkerGrid workerGrid = new WorkerGrid2D(size, size);    // Create a 2D Worker
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);  // Attach the worker to the Grid
        KernelContext context = new KernelContext();             // Create a context
        workerGrid.setLocalWork(32, 32, 1);                      // Set the local-group size

        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B) // Stream data from host to device
                .task("t0", Compute::mxmKernel, context, A, B, C, size)   // Each task points to an existing Java method
                .transferToHost(C);                                       // sync arrays with the host side
        taskGraph.execute(gridScheduler);   // Execute with a GridScheduler
    }
}