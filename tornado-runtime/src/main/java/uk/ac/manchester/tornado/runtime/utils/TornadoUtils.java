/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.utils;

import uk.ac.manchester.tornado.runtime.common.FieldLayout;
import uk.ac.manchester.tornado.api.enums.TornadoDeviceType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public class TornadoUtils {

    public static int getSizeReduction(int inputSize, TornadoDeviceType where) {
        switch (where) {
            case CPU:
                // If it is executed on the CPU, we return the number of threads
                // of the current
                // CPU
                return Runtime.getRuntime().availableProcessors();
            case GPU:
                // size will be the number of work-groups on the GPU
                int size = 1;
                if (inputSize > 256) {
                    size = inputSize / 256;
                }
                return size;
            default:
                return 0;
        }
    }

    public static boolean hasAnnotatedField(Object wrapper, Class<? extends Annotation> annotation) {
        for (Field field : wrapper.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    public static Object getAnnotatedObjectFromField(Object wrapper, Class<? extends Annotation> annotation) {
        Field storageField = null;
        for (Field field : wrapper.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(annotation)) {
                storageField = field;
            }
        }
        assert storageField != null;
        storageField.setAccessible(true);
        try {
            return storageField.get(wrapper);
        } catch (IllegalAccessException e) {
            throw new TornadoRuntimeException(e);
        }
    }

    public static Object getObjectFromField(Field field, Object obj) {
        return FieldLayout.get(field, obj);
    }

}
