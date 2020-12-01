
/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 * Author Gary Frost
 */
package uk.ac.manchester.tornado.api.profiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.EnumSet;

public class JSonWriter<T extends JSonWriter> {
    private StringBuilder sb;
    private int scope = 0;
    private boolean compactMode = false;
    public final static long epochUs = System.nanoTime() / 1000;

    enum State {
        NONE, AFTER_COMMA, AFTER_OBRACE, AFTER_CBRACE, AFTER_COLON, AFTER_VALUE;

        static final private EnumSet<State> needsComma = EnumSet.of(State.AFTER_CBRACE, State.AFTER_VALUE);
        static final private EnumSet<State> needsNewline = EnumSet.of(State.AFTER_CBRACE, State.AFTER_COMMA, State.AFTER_OBRACE);

        boolean needsComma() {
            return needsComma.contains(this);
        }

        boolean needsNewLine() {
            return needsNewline.contains(this);
        }
    }

    interface ContentWriter {
        void write();
    }

    private State currentState = State.NONE;

    JSonWriter() {
        sb = new StringBuilder();
    }

    protected T incScope() {
        scope++;
        return (T) this;
    }

    protected T decScope() {
        scope--;
        return (T) this;
    }

    protected T state(State newState) {
        currentState = newState;
        return (T) this;
    }

    protected T append(String s) {
        sb.append(s);
        return (T) this;
    }

    protected T append(StringBuilder sb) {
        return (T) append(sb.toString());
    }

    protected T append(JSonWriter json) {
        return (T) append(json.toString());
    }

    protected T append(long value) {
        append(Long.toString(value));
        return (T) this;
    }

    protected T append(int value) {
        append(Integer.toString(value));
        return (T) this;
    }

    protected T append(float value) {
        append(Float.toString(value));
        return (T) this;
    }

    protected T append(double value) {
        append(Double.toString(value));
        return (T) this;
    }

    protected T quote(String value) {
        return (T) append("\"" + value + "\"").state(State.AFTER_VALUE);
    }

    protected T colon() {
        return (T) append(":").state(State.AFTER_COLON);
    }

    protected T obrace() {
        return (T) commaIfNeeded().append("{").state(State.AFTER_OBRACE).incScope().newlineIfNeeded();
    }

    protected T cbrace() {
        decScope();
        if (currentState == State.AFTER_VALUE) {
            newline(); // we just processed a value so need a newline
        }
        return (T) append("}").state(State.AFTER_CBRACE).newlineIfNeeded();
    }

    protected T osqbrace() {
        return (T) commaIfNeeded().append("[").state(State.AFTER_OBRACE).incScope().newlineIfNeeded();
    }

    protected T csqbrace() {
        decScope();
        if (currentState == State.AFTER_VALUE) {
            newline(); // we just processed a value so need a newline
        }
        return (T) append("]").state(State.AFTER_CBRACE).newlineIfNeeded();
    }

    T arrayStart(String k) {
        return (T) key(k).osqbrace();
    }

    T arrayEnd() {
        return csqbrace();
    }

    T objectStart(String k) {
        return (T) key(k).obrace();
    }

    T objectStart() {
        return (T) obrace();
    }

    T objectEnd() {
        return cbrace();
    }

    T object(ContentWriter w) {
        objectStart();
        if (w != null) {
            w.write();
        }
        return (T) objectEnd();
    }

    T object(String k, ContentWriter w) {
        objectStart(k);
        if (w != null) {
            w.write();
        }
        return (T) objectEnd();
    }

    T array(String k, ContentWriter w) {
        arrayStart(k);
        if (w != null) {
            w.write();
        }
        return (T) arrayEnd();
    }

    protected T compact() {
        compactMode = true;
        return (T) this;
    }

    protected T nonCompact() {
        compactMode = false;
        return (T) this;
    }

    protected T newline() {
        append("\n");
        for (int i = 0; i < scope; i++) {
            append(" ");
        }
        return (T) this;
    }

    protected T newlineIfNeeded() {
        if (compactMode == false && currentState.needsNewLine()) {
            newline();
        }
        return (T) this;
    }

    protected T commaIfNeeded() {
        if (currentState.needsComma()) {
            append(",").state(State.AFTER_COMMA).newlineIfNeeded();
        }
        return (T) this;
    }

    protected T key(String k) {
        return (T) commaIfNeeded().quote(k).colon().state(State.AFTER_COLON);
    }

    T kv(String k, String value) {
        return value != null ? (T) key(k).quote(value).state(State.AFTER_VALUE) : (T) this;
    }

    T kv(String k, long value) {
        return (T) key(k).append(value).state(State.AFTER_VALUE);
    }

    T kv(String k, int value) {
        return (T) key(k).append(value).state(State.AFTER_VALUE);
    }

    T kv(String k, float value) {
        return (T) key(k).append(value).state(State.AFTER_VALUE);
    }

    T kv(String k, boolean value) {
        return (T) key(k).append(value ? "true" : "false").state(State.AFTER_VALUE);
    }

    T us(String k, long valueUs) {
        return (T) kv(k, valueUs - epochUs);
    }

    T ns(String k, long valueNs) {
        return (T) us(k, valueNs / 1000);
    }

    T ms(String k, long valueMs) {
        return (T) us(k, valueMs * 1000);
    }

    T usd(String k, long valueUs) {
        return (T) kv(k, valueUs);
    }

    T nsd(String k, long valueNs) {

        return (T) usd(k, valueNs / 1000);
    }

    T msd(String k, long valueMs) {
        return (T) usd(k, valueMs * 1000);
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    void write(File file) {
        try {
            OutputStreamWriter o = new OutputStreamWriter(new FileOutputStream(file));
            o.append(toString());
            o.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
