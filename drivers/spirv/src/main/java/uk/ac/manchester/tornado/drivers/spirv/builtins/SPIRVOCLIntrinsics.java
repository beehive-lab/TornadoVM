package uk.ac.manchester.tornado.drivers.spirv.builtins;

public class SPIRVOCLIntrinsics {

    public static native int get_global_id(int value);

    public static native int get_local_id(int value);

    public static native int get_global_size(int value);

    public static native int get_local_size(int value);

    public static native int get_group_id(int value);

    public static native int get_group_size(int value);

    public static native void localBarrier();

    public static native void globalBarrier();

    public static native void printf();

    public static native void printEmpty();

    public static native void createLocalMemory(int[] array, int size);

}
