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
package uk.ac.manchester.tornado.tc.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * A kernel or device function. Its parameters are the arguments of the entry block (the first block).
 */
public final class Function {

    public enum Kind {
        KERNEL("kernel"), DEVICE("device");

        public final String text;

        Kind(String text) {
            this.text = text;
        }
    }

    private String name;
    private final Kind kind;
    private final Type returnType;
    private final List<Block> blocks = new ArrayList<>();
    private final List<Attribute> attributes = new ArrayList<>();

    public Function(String name, Kind kind, Type returnType) {
        this.name = name;
        this.kind = kind;
        this.returnType = returnType;
    }

    public String name() {
        return name;
    }

    /** Renames the function (a kernel launched from the device gets its own entry-point name). */
    public void rename(String newName) {
        this.name = newName;
    }

    public Kind kind() {
        return kind;
    }

    public Type returnType() {
        return returnType;
    }

    public List<Block> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    /** Creates a block at the end of the function; the first block created is the entry block. */
    public Block newBlock(String label) {
        var block = new Block(this, label);
        blocks.add(block);
        return block;
    }

    /** Removes a block; its instructions must already have dropped their references. */
    public void removeBlock(Block block) {
        if (block == entry()) {
            throw new IllegalArgumentException("cannot remove the entry block");
        }
        blocks.remove(block);
    }

    /** Moves {@code block} to position {@code index}; the block at index 0 is the entry. */
    public void moveBlock(Block block, int index) {
        blocks.remove(block);
        blocks.add(index, block);
    }

    public Block entry() {
        if (blocks.isEmpty()) {
            throw new IllegalStateException("function " + name + " has no blocks");
        }
        return blocks.get(0);
    }

    public List<BlockArg> params() {
        return entry().args();
    }

    /** Predecessors of every block, in block order, without duplicates. */
    public Map<Block, List<Block>> predecessors() {
        var preds = new IdentityHashMap<Block, LinkedHashSet<Block>>();
        for (var block : blocks) {
            preds.put(block, new LinkedHashSet<>());
        }
        for (var block : blocks) {
            for (var successor : block.successors()) {
                preds.computeIfAbsent(successor, _k -> new LinkedHashSet<>()).add(block);
            }
        }
        var result = new LinkedHashMap<Block, List<Block>>();
        for (var block : blocks) {
            result.put(block, List.copyOf(preds.get(block)));
        }
        return result;
    }

    public List<Attribute> attributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(Attribute attribute) {
        attributes.add(attribute);
    }

    @Override
    public String toString() {
        return kind.text + " @" + name;
    }
}
