package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVAccessQualifier extends SPIRVEnum {

    protected SPIRVAccessQualifier(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVAccessQualifier ReadOnly() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVAccessQualifier(0, "ReadOnly", params, SPIRVCapability.Kernel());
    }
    public static SPIRVAccessQualifier WriteOnly() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVAccessQualifier(1, "WriteOnly", params, SPIRVCapability.Kernel());
    }
    public static SPIRVAccessQualifier ReadWrite() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVAccessQualifier(2, "ReadWrite", params, SPIRVCapability.Kernel());
    }
}
