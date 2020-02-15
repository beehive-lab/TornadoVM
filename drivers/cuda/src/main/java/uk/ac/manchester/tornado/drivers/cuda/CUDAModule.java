package uk.ac.manchester.tornado.drivers.cuda;

public class CUDAModule {
    public final byte[] nativeModule;

    public CUDAModule(byte[] source) {
        nativeModule = cuModuleLoadData(source);
    }

    private native static byte[] cuModuleLoadData(byte[] source);
}
