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
 * Source location of an instruction: Java source file, line, and bytecode index ({@code -1} when
 * unknown).
 */
public record Loc(String file, int line, int bci) {

    public static final Loc UNKNOWN = new Loc("", -1, -1);

    public boolean isKnown() {
        return line >= 0 || bci >= 0;
    }

    public String text() {
        var sb = new StringBuilder("loc(\"").append(file).append("\":").append(line);
        if (bci >= 0) {
            sb.append(", bci=").append(bci);
        }
        return sb.append(')').toString();
    }
}
