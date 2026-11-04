/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class OptionParsersTest {

    private enum Mode {
        FAST, SLOW
    }

    @Test
    public void booleansIgnoreLetterCase() {
        assertEquals(true, OptionParsers.BOOLEAN.parse("TRUE"));
        assertEquals(true, OptionParsers.BOOLEAN.parse("True"));
        assertEquals(false, OptionParsers.BOOLEAN.parse("false"));
        assertEquals(false, OptionParsers.BOOLEAN.parse(" False "));
    }

    @Test
    public void rejectsTextThatIsNotABoolean() {
        assertThrows(IllegalArgumentException.class, () -> OptionParsers.BOOLEAN.parse("ture"));
        assertThrows(IllegalArgumentException.class, () -> OptionParsers.BOOLEAN.parse("1"));
    }

    @Test
    public void sizesTakeBinaryUnits() {
        assertEquals(512L, OptionParsers.SIZE.parse("512"));
        assertEquals(64L * 1024 * 1024, OptionParsers.SIZE.parse("64MB"));
        assertEquals(4L * 1024 * 1024 * 1024, OptionParsers.SIZE.parse("4GB"));
    }

    @Test
    public void numbersRejectText() {
        assertThrows(NumberFormatException.class, () -> OptionParsers.INTEGER.parse("four"));
        assertEquals(42L, OptionParsers.LONG.parse(" 42 "));
    }

    @Test
    public void enumsMatchByNameInAnyCase() {
        OptionParser<Mode> parser = OptionParsers.enumOf(Mode.class);
        assertEquals(Mode.FAST, parser.parse("fast"));
        assertEquals("fast|slow", parser.typeName());
        assertThrows(IllegalArgumentException.class, () -> parser.parse("medium"));
    }

    @Test
    public void choiceAcceptsOnlyItsWordsInAnyCase() {
        OptionParsers.Choice parser = OptionParsers.choice("yield", "spin", "blocking");
        assertEquals("blocking", parser.parse(" Blocking "));
        assertEquals("yield|spin|blocking", parser.typeName());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse("blcoking"));
        assertEquals("'blcoking' is not one of yield, spin, blocking", e.getMessage());
        assertEquals(false, parser.acceptsBlank());
    }

    @Test
    public void choiceListTakesSeveralWordsOrNone() {
        OptionParsers.Choice parser = OptionParsers.choiceList("opencl", "cuda", "metal");
        assertEquals("cuda,opencl", parser.parse("CUDA, opencl"));
        assertEquals("", parser.parse(""));
        assertEquals(true, parser.acceptsBlank());
        assertThrows(IllegalArgumentException.class, () -> parser.parse("opencl,cdua"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("opencl,,cuda"));
    }

    @Test
    public void positiveListChecksEveryDimension() {
        OptionParser<String> parser = OptionParsers.positiveList(3);
        assertEquals("1024,1024", parser.parse("1024, 1024"));
        assertEquals("n[,n[,n]]", parser.typeName());
        assertThrows(IllegalArgumentException.class, () -> parser.parse("16,x"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("0,16"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1,2,3,4"));
    }

    @Test
    public void deviceIsBackendColonDevice() {
        assertEquals("1:0", OptionParsers.DEVICE.parse(" 1:0"));
        assertThrows(IllegalArgumentException.class, () -> OptionParsers.DEVICE.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> OptionParsers.DEVICE.parse("gpu:0"));
    }

    @Test
    public void nameListRejectsEmptyItems() {
        assertEquals("input,emit", OptionParsers.NAME_LIST.parse("input, emit"));
        assertThrows(IllegalArgumentException.class, () -> OptionParsers.NAME_LIST.parse("input,"));
    }

    @Test
    public void onlyStringsTakeBlankText() {
        assertEquals(true, OptionParsers.STRING.acceptsBlank());
        assertEquals(false, OptionParsers.BOOLEAN.acceptsBlank());
        assertEquals(false, OptionParsers.INTEGER.acceptsBlank());
    }
}
