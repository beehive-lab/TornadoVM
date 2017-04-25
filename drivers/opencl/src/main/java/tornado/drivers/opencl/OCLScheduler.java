package tornado.drivers.opencl;

import static tornado.common.Tornado.fatal;

public class OCLScheduler {

    public static final OCLKernelScheduler create(final OCLDeviceContext context) {

        if (null != context.getDevice().getDeviceType()) {
            switch (context.getDevice().getDeviceType()) {
                case CL_DEVICE_TYPE_GPU:
                    return new OCLGpuScheduler(context);
                case CL_DEVICE_TYPE_CPU:
                case CL_DEVICE_TYPE_ACCELERATOR:
                    return new OCLCpuScheduler(context);
                default:
                    fatal("No scheduler available for device: %s", context);
                    break;
            }
        }

        return null;
    }
}
