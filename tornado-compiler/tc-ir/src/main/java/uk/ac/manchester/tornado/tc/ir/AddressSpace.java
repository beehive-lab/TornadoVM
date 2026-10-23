/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.tc.ir;

/**
 * Memory address spaces. The numbers follow the NVPTX convention so that exported LLVM IR text needs
 * no remapping; other dialects map them in their printers.
 */
public enum AddressSpace {
    GLOBAL("global", 1),
    SHARED("shared", 3),
    CONSTANT("constant", 4),
    PRIVATE("private", 5);

    private final String text;
    private final int number;

    AddressSpace(String text, int number) {
        this.text = text;
        this.number = number;
    }

    public String text() {
        return text;
    }

    public int number() {
        return number;
    }

    public static AddressSpace fromText(String text) {
        for (var space : values()) {
            if (space.text.equals(text)) {
                return space;
            }
        }
        throw new IllegalArgumentException("unknown address space: " + text);
    }
}
