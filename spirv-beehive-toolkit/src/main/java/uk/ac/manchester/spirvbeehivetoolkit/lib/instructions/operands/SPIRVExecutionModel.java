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
public class SPIRVExecutionModel extends SPIRVEnum {

    protected SPIRVExecutionModel(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVExecutionModel Vertex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(0, "Vertex", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionModel TessellationControl() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(1, "TessellationControl", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionModel TessellationEvaluation() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(2, "TessellationEvaluation", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionModel Geometry() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(3, "Geometry", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionModel Fragment() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(4, "Fragment", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionModel GLCompute() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(5, "GLCompute", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionModel Kernel() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(6, "Kernel", params, SPIRVCapability.Kernel());
    }
}
