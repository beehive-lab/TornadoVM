package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVScope extends SPIRVEnum {

    protected SPIRVScope(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVScope CrossDevice() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVScope(0, "CrossDevice", params);
    }
    public static SPIRVScope Device() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVScope(1, "Device", params);
    }
    public static SPIRVScope Workgroup() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVScope(2, "Workgroup", params);
    }
    public static SPIRVScope Subgroup() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVScope(3, "Subgroup", params);
    }
    public static SPIRVScope Invocation() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVScope(4, "Invocation", params);
    }
}
