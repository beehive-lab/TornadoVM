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

package uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler;

public class CLIHighlighter implements SPIRVSyntaxHighlighter {
    String IDFormat = "\033[1;33m%s\033[0m";
    String stringFormat = "\033[0;32m%s\033[0m";
    String intFormat = "\033[0;34m%s\033[0m";
    String commentFormat = "\u001b[38;5;246m%s\033[0m";

    private final boolean shouldHighlight;

    public CLIHighlighter(boolean shouldHighlight) {
        this.shouldHighlight = shouldHighlight;
    }

    @Override
    public String highlightId(String ID) {
        if (shouldHighlight) return String.format(IDFormat, ID);
        else return ID;
    }

    @Override
    public String highlightString(String string) {
        if (shouldHighlight) return String.format(stringFormat, string);
        else return string;
    }

    @Override
    public String highlightInt(String integer) {
        if (shouldHighlight) return String.format(intFormat, integer);
        else return integer;
    }

    @Override
    public String highlightComment(String comment) {
        if (shouldHighlight) return String.format(commentFormat, comment);
        else return comment;
    }
}
