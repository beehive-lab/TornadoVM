package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVDecoration extends SPIRVEnum {

    protected SPIRVDecoration(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVDecoration RelaxedPrecision() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(0, "RelaxedPrecision", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration SpecId(SPIRVLiteralInteger specializationConstantID) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(specializationConstantID);
        return new SPIRVDecoration(1, "SpecId", params, SPIRVCapability.Shader(), SPIRVCapability.Kernel());
    }
    public static SPIRVDecoration Block() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(2, "Block", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration BufferBlock() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(3, "BufferBlock", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration RowMajor() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(4, "RowMajor", params, SPIRVCapability.Matrix());
    }
    public static SPIRVDecoration ColMajor() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(5, "ColMajor", params, SPIRVCapability.Matrix());
    }
    public static SPIRVDecoration ArrayStride(SPIRVLiteralInteger arrayStride) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(arrayStride);
        return new SPIRVDecoration(6, "ArrayStride", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration MatrixStride(SPIRVLiteralInteger matrixStride) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(matrixStride);
        return new SPIRVDecoration(7, "MatrixStride", params, SPIRVCapability.Matrix());
    }
    public static SPIRVDecoration GLSLShared() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(8, "GLSLShared", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration GLSLPacked() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(9, "GLSLPacked", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration CPacked() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(10, "CPacked", params, SPIRVCapability.Kernel());
    }
    public static SPIRVDecoration BuiltIn(SPIRVBuiltIn parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVDecoration(11, "BuiltIn", params);
    }
    public static SPIRVDecoration NoPerspective() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(13, "NoPerspective", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration Flat() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(14, "Flat", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration Patch() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(15, "Patch", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVDecoration Centroid() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(16, "Centroid", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration Sample() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(17, "Sample", params, SPIRVCapability.SampleRateShading());
    }
    public static SPIRVDecoration Invariant() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(18, "Invariant", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration Restrict() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(19, "Restrict", params);
    }
    public static SPIRVDecoration Aliased() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(20, "Aliased", params);
    }
    public static SPIRVDecoration Volatile() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(21, "Volatile", params);
    }
    public static SPIRVDecoration Constant() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(22, "Constant", params, SPIRVCapability.Kernel());
    }
    public static SPIRVDecoration Coherent() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(23, "Coherent", params);
    }
    public static SPIRVDecoration NonWritable() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(24, "NonWritable", params);
    }
    public static SPIRVDecoration NonReadable() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(25, "NonReadable", params);
    }
    public static SPIRVDecoration Uniform() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(26, "Uniform", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration SaturatedConversion() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(28, "SaturatedConversion", params, SPIRVCapability.Kernel());
    }
    public static SPIRVDecoration Stream(SPIRVLiteralInteger streamNumber) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(streamNumber);
        return new SPIRVDecoration(29, "Stream", params, SPIRVCapability.GeometryStreams());
    }
    public static SPIRVDecoration Location(SPIRVLiteralInteger location) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(location);
        return new SPIRVDecoration(30, "Location", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration Component(SPIRVLiteralInteger component) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(component);
        return new SPIRVDecoration(31, "Component", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration Index(SPIRVLiteralInteger index) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(index);
        return new SPIRVDecoration(32, "Index", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration Binding(SPIRVLiteralInteger bindingPoint) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(bindingPoint);
        return new SPIRVDecoration(33, "Binding", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration DescriptorSet(SPIRVLiteralInteger descriptorSet) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(descriptorSet);
        return new SPIRVDecoration(34, "DescriptorSet", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration Offset(SPIRVLiteralInteger byteOffset) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(byteOffset);
        return new SPIRVDecoration(35, "Offset", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration XfbBuffer(SPIRVLiteralInteger xFBBufferNumber) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(xFBBufferNumber);
        return new SPIRVDecoration(36, "XfbBuffer", params, SPIRVCapability.TransformFeedback());
    }
    public static SPIRVDecoration XfbStride(SPIRVLiteralInteger xFBStride) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(xFBStride);
        return new SPIRVDecoration(37, "XfbStride", params, SPIRVCapability.TransformFeedback());
    }
    public static SPIRVDecoration FuncParamAttr(SPIRVFunctionParameterAttribute functionParameterAttribute) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(functionParameterAttribute);
        return new SPIRVDecoration(38, "FuncParamAttr", params, SPIRVCapability.Kernel());
    }
    public static SPIRVDecoration FPRoundingMode(SPIRVFPRoundingMode floatingPointRoundingMode) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(floatingPointRoundingMode);
        return new SPIRVDecoration(39, "FPRoundingMode", params, SPIRVCapability.Kernel(), SPIRVCapability.StorageUniform16(), SPIRVCapability.StoragePushConstant16(), SPIRVCapability.StorageInputOutput16());
    }
    public static SPIRVDecoration FPFastMathMode(SPIRVFPFastMathMode fastMathMode) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(fastMathMode);
        return new SPIRVDecoration(40, "FPFastMathMode", params, SPIRVCapability.Kernel());
    }
    public static SPIRVDecoration LinkageAttributes(SPIRVLiteralString name, SPIRVLinkageType linkageType) {
        List<SPIRVOperand> params = new ArrayList<>(2);
        params.add(name);
        params.add(linkageType);
        return new SPIRVDecoration(41, "LinkageAttributes", params, SPIRVCapability.Linkage());
    }
    public static SPIRVDecoration NoContraction() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(42, "NoContraction", params, SPIRVCapability.Shader());
    }
    public static SPIRVDecoration InputAttachmentIndex(SPIRVLiteralInteger attachmentIndex) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(attachmentIndex);
        return new SPIRVDecoration(43, "InputAttachmentIndex", params, SPIRVCapability.InputAttachment());
    }
    public static SPIRVDecoration Alignment(SPIRVLiteralInteger alignment) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(alignment);
        return new SPIRVDecoration(44, "Alignment", params, SPIRVCapability.Kernel());
    }
    public static SPIRVDecoration MaxByteOffset(SPIRVLiteralInteger maxByteOffset) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(maxByteOffset);
        return new SPIRVDecoration(45, "MaxByteOffset", params, SPIRVCapability.Addresses());
    }
    public static SPIRVDecoration AlignmentId(SPIRVId alignment) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(alignment);
        return new SPIRVDecoration(46, "AlignmentId", params, SPIRVCapability.Kernel());
    }
    public static SPIRVDecoration MaxByteOffsetId(SPIRVId maxByteOffset) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(maxByteOffset);
        return new SPIRVDecoration(47, "MaxByteOffsetId", params, SPIRVCapability.Addresses());
    }
    public static SPIRVDecoration ExplicitInterpAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(4999, "ExplicitInterpAMD", params);
    }
    public static SPIRVDecoration OverrideCoverageNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(5248, "OverrideCoverageNV", params, SPIRVCapability.SampleMaskOverrideCoverageNV());
    }
    public static SPIRVDecoration PassthroughNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(5250, "PassthroughNV", params, SPIRVCapability.GeometryShaderPassthroughNV());
    }
    public static SPIRVDecoration ViewportRelativeNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVDecoration(5252, "ViewportRelativeNV", params, SPIRVCapability.ShaderViewportMaskNV());
    }
    public static SPIRVDecoration SecondaryViewportRelativeNV(SPIRVLiteralInteger offset) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(offset);
        return new SPIRVDecoration(5256, "SecondaryViewportRelativeNV", params, SPIRVCapability.ShaderStereoViewNV());
    }
    public static SPIRVDecoration HlslCounterBufferGOOGLE(SPIRVId counterBuffer) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(counterBuffer);
        return new SPIRVDecoration(5634, "HlslCounterBufferGOOGLE", params);
    }
    public static SPIRVDecoration HlslSemanticGOOGLE(SPIRVLiteralString semantic) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(semantic);
        return new SPIRVDecoration(5635, "HlslSemanticGOOGLE", params);
    }
}
