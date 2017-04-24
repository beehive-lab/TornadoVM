package tornado.drivers.opencl;

import static tornado.common.Tornado.USE_THREAD_COARSENING;
import static tornado.common.Tornado.getProperty;

public class OCLCpuScheduler extends OCLKernelScheduler {

    private final double CPU_COMPUTE_UNIT_COEFF = Double.parseDouble(getProperty("tornado.opencl.cpu.coeff", "1.0"));

    public OCLCpuScheduler(final OCLDeviceContext context) {
        super(context);
    }

    @Override
    public void calculateGlobalWork(final OCLKernelConfig kernelInfo) {
        long[] maxItems = deviceContext.getDevice().getMaxWorkItemSizes();

        final long[] globalWork = kernelInfo.getGlobalWork();
        for (int i = 0; i < kernelInfo.getDims(); i++) {
            if (USE_THREAD_COARSENING) {
                globalWork[i] = maxItems[i] > 1 ? (long) (kernelInfo.getDomain().get(i).cardinality()) : 1;
            } else {
                globalWork[i] = i == 0 ? (long) (deviceContext.getDevice().getMaxComputeUnits() * CPU_COMPUTE_UNIT_COEFF) : 1;
            }
        }
    }

    @Override
    public void calculateLocalWork(OCLKernelConfig kernelInfo) {
        final long[] globalWork = kernelInfo.getGlobalWork();
        final long[] localWork = kernelInfo.getLocalWork();

        for (int i = 0; i < globalWork.length; i++) {
            localWork[i] = 1;
        }
    }

}
