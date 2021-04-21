package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVCapability extends SPIRVEnum {

    protected SPIRVCapability(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVCapability Matrix() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(0, "Matrix", params);
    }
    public static SPIRVCapability Shader() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(1, "Shader", params, SPIRVCapability.Matrix());
    }
    public static SPIRVCapability Geometry() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(2, "Geometry", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability Tessellation() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(3, "Tessellation", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability Addresses() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4, "Addresses", params);
    }
    public static SPIRVCapability Linkage() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5, "Linkage", params);
    }
    public static SPIRVCapability Kernel() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(6, "Kernel", params);
    }
    public static SPIRVCapability Vector16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(7, "Vector16", params, SPIRVCapability.Kernel());
    }
    public static SPIRVCapability Float16Buffer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(8, "Float16Buffer", params, SPIRVCapability.Kernel());
    }
    public static SPIRVCapability Float16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(9, "Float16", params);
    }
    public static SPIRVCapability Float64() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(10, "Float64", params);
    }
    public static SPIRVCapability Int64() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(11, "Int64", params);
    }
    public static SPIRVCapability Int64Atomics() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(12, "Int64Atomics", params, SPIRVCapability.Int64());
    }
    public static SPIRVCapability ImageBasic() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(13, "ImageBasic", params, SPIRVCapability.Kernel());
    }
    public static SPIRVCapability ImageReadWrite() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(14, "ImageReadWrite", params, SPIRVCapability.ImageBasic());
    }
    public static SPIRVCapability ImageMipmap() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(15, "ImageMipmap", params, SPIRVCapability.ImageBasic());
    }
    public static SPIRVCapability Pipes() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(17, "Pipes", params, SPIRVCapability.Kernel());
    }
    public static SPIRVCapability Groups() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(18, "Groups", params);
    }
    public static SPIRVCapability DeviceEnqueue() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(19, "DeviceEnqueue", params, SPIRVCapability.Kernel());
    }
    public static SPIRVCapability LiteralSampler() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(20, "LiteralSampler", params, SPIRVCapability.Kernel());
    }
    public static SPIRVCapability AtomicStorage() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(21, "AtomicStorage", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability Int16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(22, "Int16", params);
    }
    public static SPIRVCapability TessellationPointSize() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(23, "TessellationPointSize", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVCapability GeometryPointSize() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(24, "GeometryPointSize", params, SPIRVCapability.Geometry());
    }
    public static SPIRVCapability ImageGatherExtended() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(25, "ImageGatherExtended", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability StorageImageMultisample() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(27, "StorageImageMultisample", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability UniformBufferArrayDynamicIndexing() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(28, "UniformBufferArrayDynamicIndexing", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability SampledImageArrayDynamicIndexing() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(29, "SampledImageArrayDynamicIndexing", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability StorageBufferArrayDynamicIndexing() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(30, "StorageBufferArrayDynamicIndexing", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability StorageImageArrayDynamicIndexing() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(31, "StorageImageArrayDynamicIndexing", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability ClipDistance() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(32, "ClipDistance", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability CullDistance() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(33, "CullDistance", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability ImageCubeArray() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(34, "ImageCubeArray", params, SPIRVCapability.SampledCubeArray());
    }
    public static SPIRVCapability SampleRateShading() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(35, "SampleRateShading", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability ImageRect() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(36, "ImageRect", params, SPIRVCapability.SampledRect());
    }
    public static SPIRVCapability SampledRect() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(37, "SampledRect", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability GenericPointer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(38, "GenericPointer", params, SPIRVCapability.Addresses());
    }
    public static SPIRVCapability Int8() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(39, "Int8", params, SPIRVCapability.Kernel());
    }
    public static SPIRVCapability InputAttachment() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(40, "InputAttachment", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability SparseResidency() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(41, "SparseResidency", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability MinLod() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(42, "MinLod", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability Sampled1D() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(43, "Sampled1D", params);
    }
    public static SPIRVCapability Image1D() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(44, "Image1D", params, SPIRVCapability.Sampled1D());
    }
    public static SPIRVCapability SampledCubeArray() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(45, "SampledCubeArray", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability SampledBuffer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(46, "SampledBuffer", params);
    }
    public static SPIRVCapability ImageBuffer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(47, "ImageBuffer", params, SPIRVCapability.SampledBuffer());
    }
    public static SPIRVCapability ImageMSArray() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(48, "ImageMSArray", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability StorageImageExtendedFormats() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(49, "StorageImageExtendedFormats", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability ImageQuery() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(50, "ImageQuery", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability DerivativeControl() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(51, "DerivativeControl", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability InterpolationFunction() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(52, "InterpolationFunction", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability TransformFeedback() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(53, "TransformFeedback", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability GeometryStreams() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(54, "GeometryStreams", params, SPIRVCapability.Geometry());
    }
    public static SPIRVCapability StorageImageReadWithoutFormat() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(55, "StorageImageReadWithoutFormat", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability StorageImageWriteWithoutFormat() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(56, "StorageImageWriteWithoutFormat", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability MultiViewport() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(57, "MultiViewport", params, SPIRVCapability.Geometry());
    }
    public static SPIRVCapability SubgroupDispatch() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(58, "SubgroupDispatch", params, SPIRVCapability.DeviceEnqueue());
    }
    public static SPIRVCapability NamedBarrier() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(59, "NamedBarrier", params, SPIRVCapability.Kernel());
    }
    public static SPIRVCapability PipeStorage() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(60, "PipeStorage", params, SPIRVCapability.Pipes());
    }
    public static SPIRVCapability SubgroupBallotKHR() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4423, "SubgroupBallotKHR", params);
    }
    public static SPIRVCapability DrawParameters() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4427, "DrawParameters", params);
    }
    public static SPIRVCapability SubgroupVoteKHR() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4431, "SubgroupVoteKHR", params);
    }
    public static SPIRVCapability StorageBuffer16BitAccess() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4433, "StorageBuffer16BitAccess", params);
    }
    public static SPIRVCapability StorageUniform16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4434, "StorageUniform16", params, SPIRVCapability.StorageBuffer16BitAccess());
    }
    public static SPIRVCapability StoragePushConstant16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4435, "StoragePushConstant16", params);
    }
    public static SPIRVCapability StorageInputOutput16() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4436, "StorageInputOutput16", params);
    }
    public static SPIRVCapability DeviceGroup() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4437, "DeviceGroup", params);
    }
    public static SPIRVCapability MultiView() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4439, "MultiView", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability VariablePointersStorageBuffer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4441, "VariablePointersStorageBuffer", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability VariablePointers() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4442, "VariablePointers", params, SPIRVCapability.VariablePointersStorageBuffer());
    }
    public static SPIRVCapability AtomicStorageOps() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4445, "AtomicStorageOps", params);
    }
    public static SPIRVCapability SampleMaskPostDepthCoverage() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(4447, "SampleMaskPostDepthCoverage", params);
    }
    public static SPIRVCapability ImageGatherBiasLodAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5009, "ImageGatherBiasLodAMD", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability FragmentMaskAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5010, "FragmentMaskAMD", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability StencilExportEXT() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5013, "StencilExportEXT", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability ImageReadWriteLodAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5015, "ImageReadWriteLodAMD", params, SPIRVCapability.Shader());
    }
    public static SPIRVCapability SampleMaskOverrideCoverageNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5249, "SampleMaskOverrideCoverageNV", params, SPIRVCapability.SampleRateShading());
    }
    public static SPIRVCapability GeometryShaderPassthroughNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5251, "GeometryShaderPassthroughNV", params, SPIRVCapability.Geometry());
    }
    public static SPIRVCapability ShaderViewportIndexLayerEXT() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5254, "ShaderViewportIndexLayerEXT", params, SPIRVCapability.MultiViewport());
    }
    public static SPIRVCapability ShaderViewportMaskNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5255, "ShaderViewportMaskNV", params, SPIRVCapability.ShaderViewportIndexLayerEXT());
    }
    public static SPIRVCapability ShaderStereoViewNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5259, "ShaderStereoViewNV", params, SPIRVCapability.ShaderViewportMaskNV());
    }
    public static SPIRVCapability PerViewAttributesNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5260, "PerViewAttributesNV", params, SPIRVCapability.MultiView());
    }
    public static SPIRVCapability SubgroupShuffleINTEL() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5568, "SubgroupShuffleINTEL", params);
    }
    public static SPIRVCapability SubgroupBufferBlockIOINTEL() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5569, "SubgroupBufferBlockIOINTEL", params);
    }
    public static SPIRVCapability SubgroupImageBlockIOINTEL() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVCapability(5570, "SubgroupImageBlockIOINTEL", params);
    }
}
