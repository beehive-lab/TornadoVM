package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVImageChannelDataType extends SPIRVEnum {

    protected SPIRVImageChannelDataType(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVImageChannelDataType SnormInt8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(0, "SnormInt8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType SnormInt16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(1, "SnormInt16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(2, "UnormInt8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(3, "UnormInt16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormShort565() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(4, "UnormShort565", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormShort555() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(5, "UnormShort555", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt101010() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(6, "UnormInt101010", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType SignedInt8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(7, "SignedInt8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType SignedInt16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(8, "SignedInt16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType SignedInt32() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(9, "SignedInt32", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnsignedInt8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(10, "UnsignedInt8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnsignedInt16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(11, "UnsignedInt16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnsignedInt32() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(12, "UnsignedInt32", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType HalfFloat() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(13, "HalfFloat", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType Float() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(14, "Float", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt24() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(15, "UnormInt24", params, SPIRVCapability.Kernel());
    }
    public static SPIRVImageChannelDataType UnormInt101010_2() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageChannelDataType(16, "UnormInt101010_2", params, SPIRVCapability.Kernel());
    }
}
