/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.runtime.interpreter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * Emits a Graphviz DOT view of what the interpreter executed, selected with
 * {@code -Dtornado.print.bytecodes=dot}.
 *
 * <p>
 * Tasks are clustered per task-graph while buffers are global nodes, so a buffer that two task-graphs
 * both touch - the {@code persistOnDevice} / {@code consumeFromDevice} case that the textual log cannot
 * show - appears as a single node with edges crossing the cluster boundary. Every execution of every
 * graph is merged into one file, which is rewritten after each execution so it is always valid:
 * </p>
 *
 * <pre>
 * dot -Tsvg tornado-bytecodes.dot -o bytecodes.svg
 * </pre>
 *
 * <p>
 * Insertion-ordered collections are used throughout so that two runs of the same application produce
 * byte-identical files and can be diffed.
 * </p>
 */
final class BytecodeDotWriter {

    private static final String FILE_NAME = "tornado-bytecodes.dot";

    private static final Map<String, Set<String>> TASKS_PER_GRAPH = new LinkedHashMap<>();
    private static final Map<String, String> BUFFER_NODES = new LinkedHashMap<>();
    private static final Set<String> EDGES = new LinkedHashSet<>();
    private static final Set<String> PERSISTED = new LinkedHashSet<>();

    private BytecodeDotWriter() {
    }

    static synchronized void write(String graphName, long execution, List<BytecodeLogEntry> entries) {
        record(graphName, entries);
        Path target = Path.of(TornadoOptions.DUMP_BYTECODES.isBlank() ? "." : TornadoOptions.DUMP_BYTECODES, FILE_NAME);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(target))) {
                emit(writer);
            }
        } catch (IOException e) {
            new TornadoLogger().error("unable to write %s: %s", target, e.getMessage());
        }
    }

    private static void record(String graphName, List<BytecodeLogEntry> entries) {
        Set<String> tasks = TASKS_PER_GRAPH.computeIfAbsent(graphName, key -> new LinkedHashSet<>());
        List<String> pendingInputs = new ArrayList<>();
        String previousTask = null;
        String lastTask = null;

        for (BytecodeLogEntry entry : entries) {
            if (entry.object() != null) {
                declareBuffer(entry);
            }
            switch (entry.op()) {
                case "LAUNCH" -> {
                    String task = nodeId("task", entry.taskName());
                    tasks.add(String.format("    %s [label=\"%s\", shape=box, style=rounded];", task, entry.taskName()));
                    for (String input : pendingInputs) {
                        EDGES.add(String.format("  %s -> %s;", input, task));
                    }
                    pendingInputs.clear();
                    if (previousTask != null) {
                        EDGES.add(String.format("  %s -> %s [style=dotted, color=gray, label=\"order\"];", previousTask, task));
                    }
                    previousTask = task;
                    lastTask = task;
                }
                case "PERSIST", "ON_DEVICE" -> PERSISTED.add(nodeId("buf", BytecodeRenderer.objectLabel(entry.object())));
                default -> {
                    if (entry.object() == null) {
                        continue;
                    }
                    String buffer = nodeId("buf", BytecodeRenderer.objectLabel(entry.object()));
                    // Only transfers carry data flow: an ALLOC just declares the buffer node.
                    if (entry.op().startsWith("TRANSFER_HOST_TO_DEVICE")) {
                        pendingInputs.add(buffer);
                    } else if (entry.op().startsWith("TRANSFER_DEVICE_TO_HOST") && lastTask != null) {
                        EDGES.add(String.format("  %s -> %s;", lastTask, buffer));
                    }
                }
            }
        }
    }

    private static void declareBuffer(BytecodeLogEntry entry) {
        String label = BytecodeRenderer.objectLabel(entry.object());
        String id = nodeId("buf", label);
        String size = entry.size() > 0 ? "\\n" + RuntimeUtilities.humanReadableByteCount(entry.size(), false) : "";
        BUFFER_NODES.putIfAbsent(id, String.format("  %s [label=\"%s%s\", shape=ellipse];", id, label, size));
    }

    private static String nodeId(String prefix, String label) {
        return prefix + "_" + label.replaceAll("[^A-Za-z0-9]", "_");
    }

    private static void emit(PrintWriter writer) {
        writer.println("digraph tornado_bytecodes {");
        writer.println("  rankdir=LR;");
        writer.println("  node [fontname=\"monospace\", fontsize=10];");
        writer.println("  edge [fontname=\"monospace\", fontsize=9];");
        for (Map.Entry<String, String> buffer : BUFFER_NODES.entrySet()) {
            String declaration = buffer.getValue();
            if (PERSISTED.contains(buffer.getKey())) {
                declaration = declaration.replace("];", ", style=filled, fillcolor=\"#ffe0b2\"];");
            }
            writer.println(declaration);
        }
        int cluster = 0;
        for (Map.Entry<String, Set<String>> graph : TASKS_PER_GRAPH.entrySet()) {
            writer.printf("  subgraph cluster_%d {%n", cluster++);
            writer.printf("    label=\"task-graph %s\";%n", graph.getKey());
            writer.println("    color=blue;");
            graph.getValue().forEach(writer::println);
            writer.println("  }");
        }
        EDGES.forEach(writer::println);
        writer.println("}");
    }
}
