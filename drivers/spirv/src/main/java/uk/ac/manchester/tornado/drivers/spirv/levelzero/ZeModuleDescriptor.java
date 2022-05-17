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
package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeModuleDescriptor extends LevelZeroDescriptor {

    private int format;

    private int inputSize;
    private byte[] pInputModule;
    private String pBuildFlags;

    // Unfold ze_module_constants_t structure from level-zero
    private int numConstants;
    private long pConstantsIds;
    private long pConstantValues;

    private long ptrZeModuleDesc;

    public ZeModuleDescriptor() {
        this.stype = Ze_Structure_Type.ZE_STRUCTURE_TYPE_MODULE_DESC;
        this.ptrZeModuleDesc = -1;
    }

    private native void materializeNative_ZeModuleDescriptor();

    @Override
    public void materialize() {
        materializeNative_ZeModuleDescriptor();
    }

    public int getStype() {
        return stype;
    }

    public long getpNext() {
        return pNext;
    }

    public int getFormat() {
        return format;
    }

    public int getInputSize() {
        return inputSize;
    }

    public byte[] getInputModule() {
        return pInputModule;
    }

    public String getpBuildFlags() {
        return pBuildFlags;
    }

    public int getNumConstants() {
        return numConstants;
    }

    public long getpConstantsIds() {
        return pConstantsIds;
    }

    public long getpConstantValues() {
        return pConstantValues;
    }

    public void setFormat(int format) {
        this.format = format;
    }

    public void setInputSize(int inputSize) {
        this.inputSize = inputSize;
    }

    public void setBuildFlags(String buildFlags) {
        this.pBuildFlags = buildFlags;
    }

    public void setInputModule(byte[] inputModule) {
        this.pInputModule = inputModule;
    }
}
