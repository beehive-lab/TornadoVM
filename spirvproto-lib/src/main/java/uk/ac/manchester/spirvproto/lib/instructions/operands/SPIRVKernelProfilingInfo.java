package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVKernelProfilingInfo extends SPIRVEnum {

    protected SPIRVKernelProfilingInfo(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }

    public void add(SPIRVKernelProfilingInfo other) {
        if (this.value == 0) this.name = other.name;
        else if (other.value != 0) this.name += "|" + other.name;

        this.value |= other.value;
        this.parameters.addAll(other.parameters);
        SPIRVCapability[] oldCapabilities = this.capabilities;
        this.capabilities = new SPIRVCapability[oldCapabilities.length + other.capabilities.length];
        int capPos = 0;
        for (SPIRVCapability capability : oldCapabilities) {
            this.capabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : other.capabilities) {
            this.capabilities[capPos++] = capability;
        }
    }

    public static SPIRVKernelProfilingInfo None() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVKernelProfilingInfo(0x0000, "None", params);
    }
    public static SPIRVKernelProfilingInfo CmdExecTime() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVKernelProfilingInfo(0x0001, "CmdExecTime", params, SPIRVCapability.Kernel());
    }
}
