/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graal.phases;

import java.util.Collections;
import java.util.HashMap;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

public class TornadoFeatureExtraction extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        HashMap<String, Integer> features = new HashMap<>();

        for (Node node : graph.getNodes()) {

            Integer j = features.get(node.asNode().getNodeClass().shortName());
            features.put(node.asNode().getNodeClass().shortName(), (j == null) ? 1 : j + 1);

            if (node instanceof LoopBeginNode) {

            }
            // System.out.println(node.getNodeClass().shortName().toLowerCase());
            System.out.println(node.asNode().getNodeClass().shortName());
            // System.out.println(node.getClass().getName());

        }
        System.out.println(Collections.singletonList(features));
    }

    public void outJson(HashMap map) {

    }
}
