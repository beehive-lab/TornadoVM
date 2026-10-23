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
package uk.ac.manchester.tornado.tc.frontend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every binding names a method or field that exists in the TornadoVM API (or the JDK) with exactly
 * that descriptor: a binding whose signature drifted would never match, and its kernels would be
 * refused.
 */
public class BindingsTest {

    @Test
    public void everyBindingNamesARealMember() throws Exception {
        var api = Path.of("../../tornado-api/target/classes").toUri().toURL();
        try (var loader = new URLClassLoader(new URL[] { api }, ClassLoader.getPlatformClassLoader())) {
            var missing = new ArrayList<String>();
            for (var key : Bindings.methodKeys()) {
                int paren = key.indexOf('(');
                int dot = key.lastIndexOf('.', paren);
                var owner = key.substring(0, dot);
                var name = key.substring(dot + 1, paren);
                // Lane accessors, fill and duplicate are bound for the whole vector family; the API defines them class by class.
                boolean vectorFamily = owner.startsWith("uk/ac/manchester/tornado/api/types/vectors/") && name.matches("getS\\d+|setS\\d+|fill|duplicate");
                if (!vectorFamily && !hasMethod(loader, owner, name, key.substring(paren))) {
                    missing.add(key);
                }
            }
            for (var key : Bindings.fieldKeys()) {
                int dot = key.lastIndexOf('.');
                if (!hasField(loader, key.substring(0, dot), key.substring(dot + 1))) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), "bindings without a matching API member: " + missing);
        }
    }

    private static boolean hasMethod(ClassLoader loader, String owner, String name, String descriptor) throws ClassNotFoundException {
        for (var type = Class.forName(owner.replace('/', '.'), false, loader); type != null; type = type.getSuperclass()) {
            Stream<? extends Executable> members = name.equals("<init>") ? Stream.of(type.getDeclaredConstructors()) : Stream.of(type.getDeclaredMethods()).filter(m -> m.getName().equals(name));
            if (members.anyMatch(m -> descriptorOf(m).equals(descriptor))) {
                return true;
            }
            for (var i : type.getInterfaces()) {
                if (Stream.of(i.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name) && descriptorOf(m).equals(descriptor))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasField(ClassLoader loader, String owner, String name) throws ClassNotFoundException {
        for (var type = Class.forName(owner.replace('/', '.'), false, loader); type != null; type = type.getSuperclass()) {
            if (Stream.of(type.getDeclaredFields()).anyMatch(f -> f.getName().equals(name))) {
                return true;
            }
        }
        return false;
    }

    private static String descriptorOf(Executable e) {
        var returnType = e instanceof Method m ? m.getReturnType() : void.class;
        return MethodType.methodType(returnType, e.getParameterTypes()).toMethodDescriptorString();
    }
}
