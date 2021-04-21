package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVDim extends SPIRVEnum {

    protected SPIRVDim(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVDim oneD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDim(0, "oneD", params, SPIRVCapability.Sampled1D());
    }
    public static SPIRVDim twoD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDim(1, "twoD", params);
    }
    public static SPIRVDim threeD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDim(2, "threeD", params);
    }
    public static SPIRVDim Cube() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDim(3, "Cube", params, SPIRVCapability.Shader());
    }
    public static SPIRVDim Rect() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDim(4, "Rect", params, SPIRVCapability.SampledRect());
    }
    public static SPIRVDim Buffer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDim(5, "Buffer", params, SPIRVCapability.SampledBuffer());
    }
    public static SPIRVDim SubpassData() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDim(6, "SubpassData", params, SPIRVCapability.InputAttachment());
    }
}
