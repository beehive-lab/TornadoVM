import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/** The three C3 kernels in Java, compiled by the Graal path for comparison with TC. */
public class C3Kernels {

    public static void add(KernelContext ctx, FloatArray a, FloatArray b, FloatArray c) {
        for (int i = ctx.globalIdx; i < a.getSize(); i += ctx.globalGroupSizeX) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void compute(KernelContext ctx, FloatArray in, FloatArray out) {
        int i = ctx.globalIdx;
        if (i < in.getSize()) {
            float x = in.get(i);
            float v;
            if (x > 0) {
                v = TornadoMath.sqrt(x) * 2;
            } else {
                v = -x;
            }
            out.set(i, v);
        }
    }

    public static void blockSum(KernelContext ctx, FloatArray in, FloatArray out) {
        int gid = ctx.globalIdx;
        int lid = ctx.localIdx;
        int grp = ctx.groupIdx;
        int lsz = ctx.localGroupSizeX;
        float[] scratch = ctx.allocateFloatLocalArray(256);
        scratch[lid] = in.get(gid);
        ctx.localBarrier();
        for (int s = lsz / 2; s > 0; s /= 2) {
            if (lid < s) {
                scratch[lid] += scratch[lid + s];
            }
            ctx.localBarrier();
        }
        if (lid == 0) {
            out.set(grp, scratch[0]);
        }
    }

    public static void main(String[] args) throws Exception {
        int n = 1 << 20;
        var a = new FloatArray(n);
        var b = new FloatArray(n);
        var c = new FloatArray(n);
        var r = new FloatArray(n / 256);
        a.init(1f);
        b.init(2f);
        var ctx = new KernelContext();
        var tg = new TaskGraph("s")
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b)
                .task("add", C3Kernels::add, ctx, a, b, c)
                .task("compute", C3Kernels::compute, ctx, a, c)
                .task("blockSum", C3Kernels::blockSum, ctx, a, r)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c, r);
        var gs = new GridScheduler();
        for (var t : new String[] { "add", "compute", "blockSum" }) {
            var g = new WorkerGrid1D(n);
            g.setLocalWork(256, 1, 1);
            gs.addWorkerGrid("s." + t, g);
        }
        try (var plan = new TornadoExecutionPlan(tg.snapshot())) {
            plan.withGridScheduler(gs).withDevice(TornadoExecutionPlan.getDevice(1, 0)).execute();
        }
        System.out.println("c[0]=" + c.get(0) + " r[0]=" + r.get(0));
    }
}
