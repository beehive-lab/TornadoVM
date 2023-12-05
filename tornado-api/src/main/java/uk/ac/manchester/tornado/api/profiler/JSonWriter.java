/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.api.profiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.EnumSet;

public class JSonWriter<T extends JSonWriter> {
    private StringBuilder sb;
    private int scope = 0;
    private boolean compactMode = false;
    public static final long EPOCH_US = System.nanoTime() / 1000;

    enum State {
        NONE, AFTER_COMMA, AFTER_OBRACE, AFTER_CBRACE, AFTER_COLON, AFTER_VALUE;

        private static final EnumSet<State> needsComma = EnumSet.of(State.AFTER_CBRACE, State.AFTER_VALUE);
        private static final EnumSet<State> needsNewline = EnumSet.of(State.AFTER_CBRACE, State.AFTER_COMMA, State.AFTER_OBRACE);

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
        return (T) kv(k, valueUs - EPOCH_US);
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
