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

package uk.ac.manchester.spirvbeehivetoolkit.lib;

import java.nio.ByteBuffer;

public class SPIRVHeader {
    public final int magicNumber = 0x7230203;
    public final int majorVersion;
    public final int minorVersion;
    public final int genMagicNumber;
    public int bound;
    public final int schema;


    public SPIRVHeader(int version, int genMagicNumber, int bound, int schema) {
        this((version >> 16) & 0xFF, (version >> 8)  & 0xFF, genMagicNumber, bound, schema);
    }

    public SPIRVHeader(int majorVersion, int minorVersion, int genMagicNumber, int bound, int schema) {
        this.genMagicNumber = genMagicNumber;
        this.bound = bound;
        this.schema = schema;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    @Override
    public String toString() {

        return String.format("; MagicNumber: 0x%x\n", magicNumber) +
                String.format("; Version: %d.%d\n", majorVersion, minorVersion) +
                String.format("; Generator ID: %d\n", genMagicNumber >> 16) +
                String.format("; Bound: %d\n", bound) +
                String.format("; Schema: %d\n", schema);
    }

    public void write(ByteBuffer output) {
        int version = (minorVersion << 8) | (majorVersion << 16);
        output.putInt(magicNumber)
              .putInt(version)
              .putInt(genMagicNumber)
              .putInt(bound)
              .putInt(schema);
    }

    public void setBound(int currentBound) {
        bound = currentBound;
    }
}
