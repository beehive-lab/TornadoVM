package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVAddressingModel extends SPIRVEnum {

    protected SPIRVAddressingModel(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVAddressingModel Logical() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVAddressingModel(0, "Logical", params);
    }
    public static SPIRVAddressingModel Physical32() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVAddressingModel(1, "Physical32", params, SPIRVCapability.Addresses());
    }
    public static SPIRVAddressingModel Physical64() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVAddressingModel(2, "Physical64", params, SPIRVCapability.Addresses());
    }
}
