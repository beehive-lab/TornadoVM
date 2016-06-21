package tornado.benchmarks.spmv;

import tornado.api.Event;
import tornado.benchmarks.BenchmarkDriver;
import tornado.benchmarks.LinearAlgebraArrays;
import tornado.collections.matrix.SparseMatrixUtils.CSRMatrix;
import tornado.benchmarks.EventList;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskUtils;
import tornado.runtime.api.CompilableTask;
import tornado.collections.math.TornadoMath;
import tornado.common.DeviceMapping;

public class SpmvTornado extends BenchmarkDriver {

    private final CSRMatrix<float[]> matrix;

    private float[] v, y;

    private final DeviceMapping device;

    private CompilableTask spmv;

    private Event last;
    private final EventList<Event> events;

    public SpmvTornado(int iterations, CSRMatrix<float[]> matrix,
            DeviceMapping device) {
        super(iterations);
        this.matrix = matrix;
        this.device = device;
        events = new EventList<Event>(iterations);
        last = null;
    }

    @Override
    public void setUp() {
        v = new float[matrix.size];
        y = new float[matrix.size];

        Benchmark.populateVector(v);

        spmv = TaskUtils.createTask(LinearAlgebraArrays::spmv, matrix.vals,
                matrix.cols, matrix.rows, v, matrix.size, y);
        spmv.mapTo(device);
    }

    @Override
    public void tearDown() {
        spmv.invalidate();

        v = null;
        y = null;

        ((OCLDeviceMapping) device).reset();
        super.tearDown();
    }

    @Override
    public void code() {

        if (last == null)
            spmv.schedule();
        else
            spmv.schedule(last);
        last = spmv.getEvent();

        events.add(last);
    }

    @Override
    public void barrier() {
        last.waitOn();
    }

    @Override
    public boolean validate() {

        final float[] ref = new float[matrix.size];

        spmv.execute();

        System.out.printf("spmv: status=%s %s\n", spmv.getEvent().getStatus(),
                spmv.getEvent().toString());

        LinearAlgebraArrays.spmv(matrix.vals, matrix.cols, matrix.rows, v,
                matrix.size, ref);
        
        final float ulp = TornadoMath.findULPDistance(y,ref);
        return ulp < MAX_ULP;
    }

    public void printSummary() {
        if (isValid())
            System.out.printf(
                    "id=opencl-device-%d, elapsed=%f, per iteration=%f, %s\n",
                    ((OCLDeviceMapping) device).getDeviceIndex(), getElapsed(),
                    getElapsedPerIteration(), events.summeriseEvents());
        else
            System.out.printf("id=opencl-device-%d produced invalid result\n",
                    ((OCLDeviceMapping) device).getDeviceIndex());
    }

    public double getOverhead() {
        return 1.0 - events.getMeanExecutionTime() / getElapsedPerIteration();
    }

}
