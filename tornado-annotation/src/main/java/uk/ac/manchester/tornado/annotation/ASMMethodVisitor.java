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
package uk.ac.manchester.tornado.annotation;

import java.util.List;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

import uk.ac.manchester.tornado.runtime.common.ParallelAnnotationProvider;

public class ASMMethodVisitor extends MethodVisitor {

    private final List<ParallelAnnotationProvider> parallelAnnotations;
    static String parallelAnnotationClassPath = System.getProperty("tornado.load.annotation.parallel");

    public ASMMethodVisitor(int api, MethodVisitor methodVisitor, List<ParallelAnnotationProvider> parallelAnnotations) {
        super(api, methodVisitor);
        this.parallelAnnotations = parallelAnnotations;
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
        String annotationName = descriptor.replaceFirst("L", "").replace(";", "").replace("/", ".");

        if (annotationName.equals(parallelAnnotationClassPath)) {
            ParallelAnnotationProvider parallelAnnotation = new ParallelAnnotation(start[0].getOffset(), end[0].getOffset() - start[0].getOffset(), index[0]);
            parallelAnnotations.add(parallelAnnotation);
        }

        return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
    }
}
