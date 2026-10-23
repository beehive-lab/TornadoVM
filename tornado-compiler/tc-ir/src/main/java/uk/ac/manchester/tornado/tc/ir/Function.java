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
package uk.ac.manchester.tornado.tc.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    /** Bumped by every change to the control-flow graph; analyses of the CFG are cached against it. */
    private long cfgVersion;
    private long cachedVersion = -1;
    private final Map<Class<?>, Object> cfgAnalyses = new HashMap<>();

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
        cfgChanged();
        return block;
    }

    /** Removes a block; its instructions must already have dropped their references. */
    public void removeBlock(Block block) {
        if (block == entry()) {
            throw new IllegalArgumentException("cannot remove the entry block");
        }
        blocks.remove(block);
        cfgChanged();
    }

    /** Moves {@code block} to position {@code index}; the block at index 0 is the entry. */
    public void moveBlock(Block block, int index) {
        blocks.remove(block);
        blocks.add(index, block);
        cfgChanged();
    }

    /** Records a change to the control-flow graph (blocks, edges or terminators). */
    void cfgChanged() {
        cfgVersion++;
    }

    /**
     * An analysis of this function's control-flow graph (dominators, loops), computed once per CFG
     * version: any change to blocks, edges or terminators recomputes it on the next request.
     */
    public <T> T cfgAnalysis(Class<T> kind, java.util.function.Function<Function, T> compute) {
        if (cachedVersion != cfgVersion) {
            cfgAnalyses.clear();
            cachedVersion = cfgVersion;
        }
        var cached = cfgAnalyses.get(kind);
        if (cached == null) {
            cached = compute.apply(this);
            cfgAnalyses.put(kind, cached);
        }
        return kind.cast(cached);
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
