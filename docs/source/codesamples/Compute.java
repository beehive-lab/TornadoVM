public class Compute {
    private static void mxmLoop(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get(i, k) * B.get(k, j);
                }
                C.set(i, j, sum);
            }
        }
    }

    public void run(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        TaskGraph taskGraph = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, A, B) // Stream data from host to device and mark buffers as read-only
                .task("t0", Compute::mxmLoop, A, B, C, size)             // Each task points to an existing Java method
                .transferToHost(C);                                      // sync arrays with the host side
        taskGraph.execute();   // It will execute the code on the default device (e.g. a GPU)
    }
}
