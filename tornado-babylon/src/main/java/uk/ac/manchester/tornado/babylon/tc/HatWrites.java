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
package uk.ac.manchester.tornado.babylon.tc;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which members of its HAT buffer parameters a kernel may write, from the Babylon translator: the
 * members its accessors store to, through the helpers it passes buffers to. A host read of a member
 * no kernel wrote needs no copy back (TornadoHatBackend).
 */
public final class HatWrites {

    private static final String ALL = "*";

    private static final Map<Method, Map<Integer, Set<String>>> MEMBERS = new ConcurrentHashMap<>();
    private static final Map<Method, Map<Integer, List<int[]>>> EDGE_PARAMS = new ConcurrentHashMap<>();
    private static final Map<Method, Map<Integer, List<Method>>> EDGE_CALLEES = new ConcurrentHashMap<>();
    private static final Set<Method> ANALYSED = ConcurrentHashMap.newKeySet();

    private HatWrites() {
    }

    static void analysed(Method method) {
        ANALYSED.add(method);
    }

    static void member(Method method, int parameter, String member) {
        MEMBERS.computeIfAbsent(method, k -> new ConcurrentHashMap<>()).computeIfAbsent(parameter, k -> ConcurrentHashMap.newKeySet()).add(member);
    }

    static void all(Method method, int parameter) {
        member(method, parameter, ALL);
    }

    /** {@code caller} passes its buffer parameter {@code parameter} to {@code callee} as {@code calleeParameter}. */
    static void passes(Method caller, int parameter, Method callee, int calleeParameter) {
        EDGE_PARAMS.computeIfAbsent(caller, k -> new ConcurrentHashMap<>()).computeIfAbsent(parameter, k -> new CopyOnWriteArrayList<>()).add(new int[] { calleeParameter });
        EDGE_CALLEES.computeIfAbsent(caller, k -> new ConcurrentHashMap<>()).computeIfAbsent(parameter, k -> new CopyOnWriteArrayList<>()).add(callee);
    }

    /**
     * The members of parameter {@code parameter} that {@code kernel} may write, or null when it may
     * write any of them (a store the translator could not attribute, a callee it did not translate).
     */
    public static Set<String> written(Method kernel, int parameter) {
        var result = new HashSet<String>();
        return collect(kernel, parameter, result, new HashSet<>()) ? result : null;
    }

    private static boolean collect(Method method, int parameter, Set<String> into, Set<String> visited) {
        if (!visited.add(method + "#" + parameter)) {
            return true;
        }
        if (!ANALYSED.contains(method)) {
            return false;
        }
        var members = MEMBERS.getOrDefault(method, Map.of()).getOrDefault(parameter, Set.of());
        if (members.contains(ALL)) {
            return false;
        }
        into.addAll(members);
        var callees = EDGE_CALLEES.getOrDefault(method, Map.of()).getOrDefault(parameter, List.of());
        var params = EDGE_PARAMS.getOrDefault(method, Map.of()).getOrDefault(parameter, List.of());
        for (int i = 0; i < callees.size(); i++) {
            if (!collect(callees.get(i), params.get(i)[0], into, visited)) {
                return false;
            }
        }
        return true;
    }
}
