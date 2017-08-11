package tornado.drivers.opencl.builtins;

import tornado.api.ReductionOp;

public class Intrinsics {

    public static int getGlobalId(int value) {
        return 0;
    }

    public static int getLocalId(int value) {
        return 0;
    }

    public static int getGlobalSize(int value) {
        return 1;
    }

    public static int getLocalSize(int value) {
        return 1;
    }

    public static int getGroupId(int value) {
        return 0;
    }

    public static int getGroupSize(int value) {
        return 1;
    }

    public static void localBarrier() {

    }

    public static void globalBarrier() {

    }

    public static <T1, T2, R> R op(ReductionOp op, T1 x, T2 y) {
        return null;
    }

}
