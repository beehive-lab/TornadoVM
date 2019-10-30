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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.policy.InliningPolicy;
import org.graalvm.compiler.phases.common.inlining.walker.MethodInvocation;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSnippetReflectionProvider;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumDesiredSize;
import static org.graalvm.compiler.core.common.GraalOptions.MaximumInliningSize;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

public class TornadoInliningPolicy implements InliningPolicy {

    private final DebugContext debugContext;

    public TornadoInliningPolicy() {
        TornadoSnippetReflectionProvider snippetReflection = new TornadoSnippetReflectionProvider();
        this.debugContext = DebugContext.create(getTornadoRuntime().getOptions(),
                new GraalDebugHandlersFactory(snippetReflection));
    }

    @Override
    public boolean continueInlining(StructuredGraph graph) {

        if (graph.getNodeCount() >= MaximumDesiredSize.getValue(graph.getOptions())) {
            InliningUtil.logInliningDecision(debugContext, "inlining is cut off by MaximumDesiredSize");
            return false;
        }
        return true;
    }

    @Override
    public Decision isWorthInlining(Replacements replacements, MethodInvocation invocation, int inliningDepth, boolean fullyProcessed) {
        final InlineInfo info = invocation.callee();
        int nodes = info.determineNodeCount();
        if (nodes > MaximumInliningSize.getValue(info.graph().getOptions()) && !invocation.isRoot()) {
            return Decision.NO;
        }
        return Decision.YES;
    }
}
