package tornado.drivers.opencl;

public class OCLCpuScheduler extends OCLKernelScheduler {

    private final double CPU_COMPUTE_UNIT_COEFF = 1;

    public OCLCpuScheduler(final OCLDeviceContext context) {
        super(context);

//        System.out.printf("max work item sizes: %s\n", Arrays.toString(deviceContext.getDevice().getMaxWorkItemSizes()));
//        System.out.printf("kernel scheduler: compute units=%d, max work group=%d\n", deviceContext.getDevice().getMaxComputeUnits(), deviceContext.getDevice().getMaxWorkGroupSize());
    }

    @Override
    public void calculateGlobalWork(final OCLKernelConfig kernelInfo) {
        long[] maxItems = deviceContext.getDevice().getMaxWorkItemSizes();

        final long[] globalWork = kernelInfo.getGlobalWork();
//        switch (kernelInfo.getDims()) {
//            case 3:
//                globalWork[2] = 1;
//
//            case 2:
//                globalWork[1] = 1;
//                globalWork[0] = maxWorkGroupSize;
//                break;
//            case 1:
//                globalWork[0] = maxWorkGroupSize;
//                break;
//            default:
//                break;
//        }
        for (int i = 0; i < kernelInfo.getDims(); i++) {
//            long value = maxItems[i] > 1 ? (long) (kernelInfo.getDomain().get(i).cardinality())  : 1; /// utils[utilsIndex]);
            /*
             * if( value % 32 != 0){ value = ((value / 32) + 1) * 32; }
             */
//            globalWork[i] = value;
            globalWork[i] = maxItems[i] > 1 ? deviceContext.getDevice().getMaxComputeUnits() : 1;
        }
    }

    @Override
    public void calculateLocalWork(OCLKernelConfig kernelInfo) {
        final long[] globalWork = kernelInfo.getGlobalWork();
        final long[] localWork = kernelInfo.getLocalWork();

        for (int i = 0; i < globalWork.length; i++) {
            localWork[i] = 1; //globalWork[i];
        }
    }

}
