package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVKernelEnqueueFlags extends SPIRVEnum {

    protected SPIRVKernelEnqueueFlags(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVKernelEnqueueFlags NoWait() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVKernelEnqueueFlags(0, "NoWait", params, SPIRVCapability.Kernel());
    }
    public static SPIRVKernelEnqueueFlags WaitKernel() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVKernelEnqueueFlags(1, "WaitKernel", params, SPIRVCapability.Kernel());
    }
    public static SPIRVKernelEnqueueFlags WaitWorkGroup() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVKernelEnqueueFlags(2, "WaitWorkGroup", params, SPIRVCapability.Kernel());
    }
}
