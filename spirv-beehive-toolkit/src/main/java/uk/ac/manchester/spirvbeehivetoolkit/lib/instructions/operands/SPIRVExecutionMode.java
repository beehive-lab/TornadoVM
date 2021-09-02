/*
* MIT License
*
* Copyright (c) 2021, APT Group, Department of Computer Science,
* The University of Manchester.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

package uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirvbeehivetoolkit.generator")
public class SPIRVExecutionMode extends SPIRVEnum {

    protected SPIRVExecutionMode(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVExecutionMode Invocations(SPIRVLiteralInteger numberOfInvocationInvocations) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(numberOfInvocationInvocations);
        return new SPIRVExecutionMode(0, "Invocations", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionMode SpacingEqual() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(1, "SpacingEqual", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode SpacingFractionalEven() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(2, "SpacingFractionalEven", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode SpacingFractionalOdd() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(3, "SpacingFractionalOdd", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode VertexOrderCw() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(4, "VertexOrderCw", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode VertexOrderCcw() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(5, "VertexOrderCcw", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode PixelCenterInteger() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(6, "PixelCenterInteger", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionMode OriginUpperLeft() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(7, "OriginUpperLeft", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionMode OriginLowerLeft() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(8, "OriginLowerLeft", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionMode EarlyFragmentTests() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(9, "EarlyFragmentTests", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionMode PointMode() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(10, "PointMode", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode Xfb() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(11, "Xfb", params, SPIRVCapability.TransformFeedback());
    }
    public static SPIRVExecutionMode DepthReplacing() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(12, "DepthReplacing", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionMode DepthGreater() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(14, "DepthGreater", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionMode DepthLess() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(15, "DepthLess", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionMode DepthUnchanged() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(16, "DepthUnchanged", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionMode LocalSize(SPIRVLiteralInteger xSize, SPIRVLiteralInteger ySize, SPIRVLiteralInteger zSize) {
        List<SPIRVOperand> params = new ArrayList<>(3);
        params.add(xSize);
        params.add(ySize);
        params.add(zSize);
        return new SPIRVExecutionMode(17, "LocalSize", params);
    }
    public static SPIRVExecutionMode LocalSizeHint(SPIRVLiteralInteger xSize, SPIRVLiteralInteger ySize, SPIRVLiteralInteger zSize) {
        List<SPIRVOperand> params = new ArrayList<>(3);
        params.add(xSize);
        params.add(ySize);
        params.add(zSize);
        return new SPIRVExecutionMode(18, "LocalSizeHint", params, SPIRVCapability.Kernel());
    }
    public static SPIRVExecutionMode InputPoints() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(19, "InputPoints", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionMode InputLines() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(20, "InputLines", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionMode InputLinesAdjacency() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(21, "InputLinesAdjacency", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionMode Triangles() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(22, "Triangles", params, SPIRVCapability.Geometry(), SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode InputTrianglesAdjacency() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(23, "InputTrianglesAdjacency", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionMode Quads() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(24, "Quads", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode Isolines() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(25, "Isolines", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode OutputVertices(SPIRVLiteralInteger vertexCount) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(vertexCount);
        return new SPIRVExecutionMode(26, "OutputVertices", params, SPIRVCapability.Geometry(), SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionMode OutputPoints() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(27, "OutputPoints", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionMode OutputLineStrip() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(28, "OutputLineStrip", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionMode OutputTriangleStrip() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(29, "OutputTriangleStrip", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionMode VecTypeHint(SPIRVLiteralInteger vectorType) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(vectorType);
        return new SPIRVExecutionMode(30, "VecTypeHint", params, SPIRVCapability.Kernel());
    }
    public static SPIRVExecutionMode ContractionOff() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(31, "ContractionOff", params, SPIRVCapability.Kernel());
    }
    public static SPIRVExecutionMode Initializer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(33, "Initializer", params, SPIRVCapability.Kernel());
    }
    public static SPIRVExecutionMode Finalizer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(34, "Finalizer", params, SPIRVCapability.Kernel());
    }
    public static SPIRVExecutionMode SubgroupSize(SPIRVLiteralInteger subgroupSize) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(subgroupSize);
        return new SPIRVExecutionMode(35, "SubgroupSize", params, SPIRVCapability.SubgroupDispatch());
    }
    public static SPIRVExecutionMode SubgroupsPerWorkgroup(SPIRVLiteralInteger subgroupsPerWorkgroup) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(subgroupsPerWorkgroup);
        return new SPIRVExecutionMode(36, "SubgroupsPerWorkgroup", params, SPIRVCapability.SubgroupDispatch());
    }
    public static SPIRVExecutionMode SubgroupsPerWorkgroupId(SPIRVId subgroupsPerWorkgroup) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(subgroupsPerWorkgroup);
        return new SPIRVExecutionMode(37, "SubgroupsPerWorkgroupId", params, SPIRVCapability.SubgroupDispatch());
    }
    public static SPIRVExecutionMode LocalSizeId(SPIRVId xSize, SPIRVId ySize, SPIRVId zSize) {
        List<SPIRVOperand> params = new ArrayList<>(3);
        params.add(xSize);
        params.add(ySize);
        params.add(zSize);
        return new SPIRVExecutionMode(38, "LocalSizeId", params);
    }
    public static SPIRVExecutionMode LocalSizeHintId(SPIRVId localSizeHint) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(localSizeHint);
        return new SPIRVExecutionMode(39, "LocalSizeHintId", params, SPIRVCapability.Kernel());
    }
    public static SPIRVExecutionMode PostDepthCoverage() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(4446, "PostDepthCoverage", params, SPIRVCapability.SampleMaskPostDepthCoverage());
    }
    public static SPIRVExecutionMode StencilRefReplacingEXT() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionMode(5027, "StencilRefReplacingEXT", params, SPIRVCapability.StencilExportEXT());
    }
}
