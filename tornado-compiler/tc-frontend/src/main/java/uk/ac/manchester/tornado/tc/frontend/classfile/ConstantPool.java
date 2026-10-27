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
package uk.ac.manchester.tornado.tc.frontend.classfile;

import java.util.Optional;

/**
 * The constant pool of a class file (JVMS section 4.4), looked up by the indices the instructions carry. Two
 * readers provide it: TC's own ({@link ClassFile.Reader#TC}) and, on JDK 24 and newer, the JDK's
 * Class-File API ({@link ClassFile.Reader#JDK}). Both keep the class file's own indices.
 */
public interface ConstantPool {

    /** A resolved member reference: owner class (internal name), member name and descriptor. */
    record MemberRef(String owner, String name, String descriptor) {
    }

    /** An {@code invokedynamic} site: bootstrap-method index, name and descriptor. */
    record DynamicSite(int bootstrap, String name, String descriptor) {
    }

    String utf8(int index);

    String className(int index);

    MemberRef member(int index);

    /** The method or field a {@code CONSTANT_MethodHandle} refers to, if {@code index} is one. */
    Optional<MemberRef> methodHandle(int index);

    DynamicSite dynamicSite(int index);

    /** A loadable constant ({@code ldc}): Integer, Float, Long, Double or String. */
    Object loadable(int index);
}
