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

/** A syntax or name-resolution error in TC-SSA text, with its position. */
public final class IrParseException extends RuntimeException {

    private final transient Diagnostic diagnostic;
    private final int line;
    private final int column;

    IrParseException(String code, String message, int line, int column) {
        super(code + " at " + line + ":" + column + ": " + message);
        this.diagnostic = new Diagnostic(code, message, "column " + column, Loc.UNKNOWN, line);
        this.line = line;
        this.column = column;
    }

    public Diagnostic diagnostic() {
        return diagnostic;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}
