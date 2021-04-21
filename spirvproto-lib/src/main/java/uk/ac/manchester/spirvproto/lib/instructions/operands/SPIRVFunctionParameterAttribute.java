package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVFunctionParameterAttribute extends SPIRVEnum {

    protected SPIRVFunctionParameterAttribute(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVFunctionParameterAttribute Zext() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFunctionParameterAttribute(0, "Zext", params, SPIRVCapability.Kernel());
    }
    public static SPIRVFunctionParameterAttribute Sext() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFunctionParameterAttribute(1, "Sext", params, SPIRVCapability.Kernel());
    }
    public static SPIRVFunctionParameterAttribute ByVal() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFunctionParameterAttribute(2, "ByVal", params, SPIRVCapability.Kernel());
    }
    public static SPIRVFunctionParameterAttribute Sret() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFunctionParameterAttribute(3, "Sret", params, SPIRVCapability.Kernel());
    }
    public static SPIRVFunctionParameterAttribute NoAlias() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFunctionParameterAttribute(4, "NoAlias", params, SPIRVCapability.Kernel());
    }
    public static SPIRVFunctionParameterAttribute NoCapture() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFunctionParameterAttribute(5, "NoCapture", params, SPIRVCapability.Kernel());
    }
    public static SPIRVFunctionParameterAttribute NoWrite() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFunctionParameterAttribute(6, "NoWrite", params, SPIRVCapability.Kernel());
    }
    public static SPIRVFunctionParameterAttribute NoReadWrite() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVFunctionParameterAttribute(7, "NoReadWrite", params, SPIRVCapability.Kernel());
    }
}
