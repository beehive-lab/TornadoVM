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
import java.util.List;
import java.util.Optional;

/**
 * A basic block: arguments, a straight-line list of instructions, and one terminator at the end.
 */
public final class Block {

    private final Function function;
    private String label;
    private final List<BlockArg> args = new ArrayList<>();
    private final List<Inst> insts = new ArrayList<>();
    private final List<Attribute> attributes = new ArrayList<>();

    Block(Function function, String label) {
        this.function = function;
        this.label = label;
    }

    public Function function() {
        return function;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<BlockArg> args() {
        return Collections.unmodifiableList(args);
    }

    public BlockArg addArg(Type type, String name) {
        var arg = new BlockArg(this, type, name);
        args.add(arg);
        return arg;
    }

    /** Removes an argument that has no users. Callers must also update every incoming edge. */
    public void removeArg(BlockArg arg) {
        if (arg.hasUses()) {
            throw new IllegalStateException("cannot remove a block argument that still has users");
        }
        args.remove(arg);
    }

    public List<Inst> insts() {
        return Collections.unmodifiableList(insts);
    }

    public void append(Inst inst) {
        attach(inst);
        insts.add(inst);
    }

    public void insertBefore(Inst inst, Inst anchor) {
        int index = insts.indexOf(anchor);
        if (index < 0) {
            throw new IllegalArgumentException("anchor is not in this block");
        }
        attach(inst);
        insts.add(index, inst);
    }

    void remove(Inst inst) {
        insts.remove(inst);
        inst.setBlock(null);
        if (!inst.successors().isEmpty() || inst.isTerminator()) {
            function.cfgChanged();
        }
    }

    private void attach(Inst inst) {
        if (inst.block().isPresent()) {
            throw new IllegalStateException("instruction is already in a block");
        }
        inst.setBlock(this);
        if (!inst.successors().isEmpty() || inst.isTerminator()) {
            function.cfgChanged();
        }
    }

    /** The terminator, if the block currently ends with one. */
    public Optional<Inst> terminator() {
        if (insts.isEmpty()) {
            return Optional.empty();
        }
        var last = insts.get(insts.size() - 1);
        return last.isTerminator() ? Optional.of(last) : Optional.empty();
    }

    /** Successor blocks in terminator order (may repeat a block). */
    public List<Block> successors() {
        return terminator().map(t -> t.successors().stream().map(Successor::target).toList()).orElse(List.of());
    }

    public List<Attribute> attributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(Attribute attribute) {
        attributes.add(attribute);
    }

    public void removeAttribute(String name) {
        attributes.removeIf(a -> a.name().equals(name));
    }

    public boolean hasAttribute(String name) {
        return attributes.stream().anyMatch(a -> a.name().equals(name));
    }

    @Override
    public String toString() {
        return "^" + label;
    }
}
