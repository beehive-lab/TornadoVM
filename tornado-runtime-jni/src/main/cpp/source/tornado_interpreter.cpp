/*
 * MIT License
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

#include "tornado_interpreter.h"
#include "tornado_bytecodes.h"

static inline int64_t pack(int32_t status, int32_t position) {
    return ((int64_t) status << 32) | ((int64_t) (uint32_t) position);
}

int64_t tornado_interpret(const uint8_t *code, int32_t position, int32_t limit, int32_t flags) {
    /*
     * No case reads `flags` yet. Will read after each bytecode is ported to cpp.
     */
    (void) flags;

    int32_t pc = position;

    while (pc < limit) {
        switch (static_cast<TornadoBytecode>(code[pc])) {
            case TornadoBytecode::END:
                return pack(TORNADO_STATUS_END, pc + 1);
            default:
                /*
                 * Not implemented here. Leave the cursor on the opcode so that the Java
                 * interpreter decodes and executes it exactly as it would have on its own.
                 *
                 */
                return pack(TORNADO_STATUS_BAIL, pc);
        }
    }

    return pack(TORNADO_STATUS_EOF, pc);
}
