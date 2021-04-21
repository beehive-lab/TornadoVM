package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVMemoryModel extends SPIRVEnum {

    protected SPIRVMemoryModel(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVMemoryModel Simple() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemoryModel(0, "Simple", params, SPIRVCapability.Shader());
    }
    public static SPIRVMemoryModel GLSL450() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemoryModel(1, "GLSL450", params, SPIRVCapability.Shader());
    }
    public static SPIRVMemoryModel OpenCL() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemoryModel(2, "OpenCL", params, SPIRVCapability.Kernel());
    }
}
