/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.tc.frontend;

import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import org.junit.Test;

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
            assertTrue("bindings without a matching API member: " + missing, missing.isEmpty());
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
