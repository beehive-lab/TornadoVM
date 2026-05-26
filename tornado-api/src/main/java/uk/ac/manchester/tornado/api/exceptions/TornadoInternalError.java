/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.api.exceptions;

import java.util.ArrayList;

public class TornadoInternalError extends Error {

    private static final long serialVersionUID = 6639694094043791236L;

    private final ArrayList<String> context = new ArrayList<>();

    public static RuntimeException unimplemented() {
        throw new TornadoInternalError("unimplemented");
    }

    public static RuntimeException unimplementedMetal() {
        throw new TornadoInternalError("unimplemented yet in Metal backend.");
    }

    public static RuntimeException unimplemented(String msg) {
        throw new TornadoInternalError("unimplemented: %s", msg);
    }

    public static RuntimeException unimplemented(String msg, Object... args) {
        throw new TornadoInternalError("unimplemented: " + msg, args);
    }

    public static RuntimeException shouldNotReachHere() {
        throw new TornadoInternalError("should not reach here");
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new TornadoInternalError("should not reach here: %s", msg);
    }

    public static RuntimeException shouldNotReachHere(String msg, Object... args) {
        throw new TornadoInternalError("should not reach here: " + msg, args);
    }

    public static RuntimeException shouldNotReachHere(Throwable cause) {
        throw new TornadoInternalError(cause);
    }

    public static void guarantee(boolean condition, String msg, Object... args) {
        if (!condition) {
            throw new TornadoInternalError("failed guarantee: " + msg, args);
        }
    }

    public TornadoInternalError(String msg, Object... args) {
        super(String.format(msg, args));
    }

    public TornadoInternalError(Throwable cause) {
        super(cause);
    }

    public TornadoInternalError addContext(String newContext) {
        context.add(newContext);
        return this;
    }

    public TornadoInternalError addContext(String name, Object obj) {
        return addContext(String.format("%s: %s", name, obj));
    }

}
