package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVSourceLanguage extends SPIRVEnum {

    protected SPIRVSourceLanguage(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVSourceLanguage Unknown() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSourceLanguage(0, "Unknown", params);
    }
    public static SPIRVSourceLanguage ESSL() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSourceLanguage(1, "ESSL", params);
    }
    public static SPIRVSourceLanguage GLSL() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSourceLanguage(2, "GLSL", params);
    }
    public static SPIRVSourceLanguage OpenCL_C() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSourceLanguage(3, "OpenCL_C", params);
    }
    public static SPIRVSourceLanguage OpenCL_CPP() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSourceLanguage(4, "OpenCL_CPP", params);
    }
    public static SPIRVSourceLanguage HLSL() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSourceLanguage(5, "HLSL", params);
    }
}
