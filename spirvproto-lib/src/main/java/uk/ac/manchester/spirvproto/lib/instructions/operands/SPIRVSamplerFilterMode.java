package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVSamplerFilterMode extends SPIRVEnum {

    protected SPIRVSamplerFilterMode(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVSamplerFilterMode Nearest() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSamplerFilterMode(0, "Nearest", params, SPIRVCapability.Kernel());
    }
    public static SPIRVSamplerFilterMode Linear() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSamplerFilterMode(1, "Linear", params, SPIRVCapability.Kernel());
    }
}
