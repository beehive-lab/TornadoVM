package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVImageFormat extends SPIRVEnum {

    protected SPIRVImageFormat(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVImageFormat Unknown() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(0, "Unknown", params);
    }
    public static SPIRVImageFormat Rgba32f() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(1, "Rgba32f", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rgba16f() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(2, "Rgba16f", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat R32f() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(3, "R32f", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rgba8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(4, "Rgba8", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rgba8Snorm() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(5, "Rgba8Snorm", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rg32f() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(6, "Rg32f", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg16f() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(7, "Rg16f", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R11fG11fB10f() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(8, "R11fG11fB10f", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R16f() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(9, "R16f", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rgba16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(10, "Rgba16", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rgb10A2() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(11, "Rgb10A2", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(12, "Rg16", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(13, "Rg8", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(14, "R16", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(15, "R8", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rgba16Snorm() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(16, "Rgba16Snorm", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg16Snorm() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(17, "Rg16Snorm", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg8Snorm() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(18, "Rg8Snorm", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R16Snorm() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(19, "R16Snorm", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R8Snorm() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(20, "R8Snorm", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rgba32i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(21, "Rgba32i", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rgba16i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(22, "Rgba16i", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rgba8i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(23, "Rgba8i", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat R32i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(24, "R32i", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rg32i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(25, "Rg32i", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg16i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(26, "Rg16i", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg8i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(27, "Rg8i", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R16i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(28, "R16i", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R8i() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(29, "R8i", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rgba32ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(30, "Rgba32ui", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rgba16ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(31, "Rgba16ui", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rgba8ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(32, "Rgba8ui", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat R32ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(33, "R32ui", params, SPIRVCapability.Shader());
    }
    public static SPIRVImageFormat Rgb10a2ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(34, "Rgb10a2ui", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg32ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(35, "Rg32ui", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg16ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(36, "Rg16ui", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat Rg8ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(37, "Rg8ui", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R16ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(38, "R16ui", params, SPIRVCapability.StorageImageExtendedFormats());
    }
    public static SPIRVImageFormat R8ui() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVImageFormat(39, "R8ui", params, SPIRVCapability.StorageImageExtendedFormats());
    }
}
