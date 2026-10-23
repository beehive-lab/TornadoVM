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
 * A compiler diagnostic with a stable code ({@code TC-E1xxx} frontend, {@code E2xxx} IR and verifier,
 * {@code E3xxx} TornadoVM lowering and structuring, {@code E4xxx} target, {@code E9xxx} internal).
 *
 * @param code     stable identifier, for example {@code TC-E2003}
 * @param message  what is wrong, in one sentence
 * @param where    the offending construct in context (an instruction's text, or a source position)
 * @param loc      Java source location when known
 * @param line     line in the IR text the construct was parsed from, or {@code -1}
 */
public record Diagnostic(String code, String message, String where, Loc loc, int line) {

    public Diagnostic(String code, String message, String where, Loc loc) {
        this(code, message, where, loc, -1);
    }

    public String render() {
        var sb = new StringBuilder(line >= 0 ? "line " + line + ": " : "").append("error ").append(code).append(": ").append(message);
        if (!where.isEmpty()) {
            sb.append("\n    ").append(where);
        }
        if (loc.isKnown()) {
            sb.append("\n    at ").append(loc.file()).append(':').append(loc.line());
            if (loc.bci() >= 0) {
                sb.append(" (bci ").append(loc.bci()).append(')');
            }
        }
        return sb.toString();
    }
}
