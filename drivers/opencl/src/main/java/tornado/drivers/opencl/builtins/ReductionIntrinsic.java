package tornado.drivers.opencl.builtins;

import tornado.api.ReductionOp;
import tornado.collections.types.Float2;
import tornado.collections.types.Float4;
import tornado.collections.types.VectorFloat4;

import static tornado.drivers.opencl.builtins.Intrinsics.*;

public class ReductionIntrinsic {

    public static <T> T op(T a, T b) {
        return a;
    }

    public static void reduceLocalThreadsPrimitive4(float[] global, float[] local, float value) {

        final int index = getLocalId(0);
        local[index] = value;
        localBarrier();

        VectorFloat4 values = new VectorFloat4(local);
        Float4 a = values.get(index);
        for (int i = values.getLength() >> 1; i > 0; i >>= 1) {
            if (index < i) {
                Float4 b = values.get(index + i);
                a = op(a, b);
                values.set(index, a);
            }
            localBarrier();
        }

        Float2 x = op(a.getHi(), a.getLo());
        global[getGroupId(0)] = op(x.getX(), x.getY());
    }

    public static float reduceGF4(ReductionOp op, float[] global) {

        final int index = getGlobalId(0);
        VectorFloat4 values = new VectorFloat4(global);
        Float4 a = values.get(index);
        for (int i = values.getLength() >> 1; i > 0; i >>= 1) {
            if (index < i) {
                Float4 b = values.get(index + i);
                a = Intrinsics.op(op, a, b);
                values.set(index, a);
            }
            localBarrier();
        }

        Float2 x = Intrinsics.op(op, a.getHi(), a.getLo());
        return Intrinsics.op(op, x.getX(), x.getY());
    }

}
