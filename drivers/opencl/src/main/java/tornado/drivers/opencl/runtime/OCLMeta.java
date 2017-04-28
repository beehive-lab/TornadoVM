/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import tornado.api.Read;
import tornado.api.ReadWrite;
import tornado.api.Write;
import tornado.common.enums.Access;
import tornado.meta.Meta;

public class OCLMeta extends Meta {

    public OCLMeta(Method method, boolean readMetaData) {
        this(Modifier.isStatic(method.getModifiers()) ? method.getParameterCount() : method.getParameterCount() + 1);
        if (readMetaData) {
            readTaskMetadata(method);
        }
    }

    public OCLMeta(int numParameters) {
        super(numParameters);
    }

    protected final void readStaticMethodMetadata(Method method) {

        final int paramCount = method.getParameterCount();

        final Annotation[][] paramAnnotations = method
                .getParameterAnnotations();

        for (int i = 0; i < paramCount; i++) {
            Access access = Access.UNKNOWN;
            for (final Annotation an : paramAnnotations[i]) {
                if (an instanceof Read) {
                    access = Access.READ;
                } else if (an instanceof ReadWrite) {
                    access = Access.READ_WRITE;
                } else if (an instanceof Write) {
                    access = Access.WRITE;
                }
                if (access != Access.UNKNOWN) {
                    break;
                }
            }
            argumentsAccess[i] = access;
        }
    }

    protected final void readTaskMetadata(Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            readStaticMethodMetadata(method);
        } else {
            readVirtualMethodMetadata(method);
        }
    }

    protected final void readVirtualMethodMetadata(Method method) {
        final int paramCount = method.getParameterCount();

        Access thisAccess = Access.NONE;
        for (final Annotation an : method.getAnnotatedReceiverType()
                .getAnnotations()) {
            if (an instanceof Read) {
                thisAccess = Access.READ;
            } else if (an instanceof ReadWrite) {
                thisAccess = Access.READ_WRITE;
            } else if (an instanceof Write) {
                thisAccess = Access.WRITE;
            }
            if (thisAccess != Access.UNKNOWN) {
                break;
            }
        }

        argumentsAccess[0] = thisAccess;

        final Annotation[][] paramAnnotations = method
                .getParameterAnnotations();

        for (int i = 0; i < paramCount; i++) {
            Access access = Access.UNKNOWN;
            for (final Annotation an : paramAnnotations[i]) {
                if (an instanceof Read) {
                    access = Access.READ;
                } else if (an instanceof ReadWrite) {
                    access = Access.READ_WRITE;
                } else if (an instanceof Write) {
                    access = Access.WRITE;
                }
                if (access != Access.UNKNOWN) {
                    break;
                }
            }
            argumentsAccess[i + 1] = access;
        }

    }

}
