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

import java.lang.classfile.Attributes;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.CodeAttribute;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@code RuntimeVisibleTypeAnnotations} attribute of a {@code Code} attribute (JVMS section
 * 4.7.20) and returns the annotations on local variables, which is where {@code @Parallel} and
 * {@code @Reduce} on loop indices and accumulators live. Other targets are ignored.
 */
public final class TypeAnnotations {

    /** An annotation on local variable {@code slot}, live for {@code [startBci, startBci + length)}. */
    public record LocalAnnotation(String descriptor, int slot, int startBci, int length) {
        public boolean covers(int bci) {
            return bci >= startBci && bci < startBci + length;
        }
    }

    private TypeAnnotations() {
    }

    static List<LocalAnnotation> locals(CodeAttribute code) {
        var result = new ArrayList<LocalAnnotation>();
        for (var attribute : code.findAttributes(Attributes.runtimeVisibleTypeAnnotations())) {
            for (var annotation : attribute.annotations()) {
                if (annotation.targetInfo() instanceof TypeAnnotation.LocalVarTarget target) {
                    var descriptor = annotation.annotation().className().stringValue();
                    for (var range : target.table()) {
                        int start = code.labelToBci(range.startLabel());
                        result.add(new LocalAnnotation(descriptor, range.index(), start, code.labelToBci(range.endLabel()) - start));
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
