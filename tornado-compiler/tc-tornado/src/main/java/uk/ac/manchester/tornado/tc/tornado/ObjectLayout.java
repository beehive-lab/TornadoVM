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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.Map;

/**
 * Where the fields of an object argument are in its device copy. The TornadoVM runtime copies an
 * object field by field to the byte offsets the JVM uses for it; a reference field holds the offset of
 * the referenced object's device copy relative to the referencing object, divided by 8 (compressed
 * references). Primitive fields are read in place; a reference field {@code r} of object {@code o} is
 * {@code o + 8 * (u32 at o + offset(r))}.
 *
 * @param className the simple class name, as in {@code obj<Name>}
 * @param offsets byte offset of each field, by name (superclass fields included)
 * @param referenceClasses for fields that hold objects (not arrays), the simple class name of the object
 */
public record ObjectLayout(String className, Map<String, Integer> offsets, Map<String, String> referenceClasses) {

    /** Bytes a compressed reference is scaled by. */
    public static final int REFERENCE_SCALE = 8;
}
