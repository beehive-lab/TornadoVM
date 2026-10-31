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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class OptionsDocToolTest {

    /** The flags page of the documentation, from the tornado-api module directory Maven runs the tests in. */
    private static final Path FLAGS_PAGE = Path.of("..", "docs", "source", "flags.rst");

    private static final Pattern SINGLE_BACKQUOTE = Pattern.compile("(?<!`)`(?!`)");

    private final OptionsRenderer renderer = new OptionsRenderer(OptionRegistry.catalog());

    @Test
    public void replacesOnlyTheGeneratedSection() {
        String document = "Intro\n" + OptionsDocTool.BEGIN_MARKER + "\nold\n" + OptionsDocTool.END_MARKER + "\nOutro\n";
        assertEquals("Intro\n" + OptionsDocTool.BEGIN_MARKER + "\n\nnew\n" + OptionsDocTool.END_MARKER + "\nOutro\n", OptionsDocTool.withGeneratedSection(document, "new\n"));
    }

    @Test
    public void documentWithoutMarkersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OptionsDocTool.withGeneratedSection("no markers", "new"));
    }

    @Test
    public void documentationListsEveryPublicOptionAndEveryRemovedOne() {
        String rst = renderer.rst();
        OptionRegistry.catalog().options().forEach(option -> assertTrue(rst.contains("``-D" + option.key() + "``"), option.key()));
        OptionRegistry.catalog().removed().forEach(option -> assertTrue(rst.contains("``-D" + option.key() + "``"), option.key()));
        assertFalse(SINGLE_BACKQUOTE.matcher(rst).find(), "single backquotes left in reStructuredText");
    }

    @Test
    public void listingHidesInternalOptionsUnlessAsked() {
        OptionResolver resolver = new OptionResolver(MapSource.of("tornado.debug", "true"), message -> {
        });
        String publicOnly = renderer.text(resolver, false);
        assertTrue(publicOnly.contains("-Dtornado.debug=<boolean>  (default: false)  [set: true]"), publicOnly);
        assertFalse(publicOnly.contains("tornado.tc.memoize"));
        assertTrue(renderer.text(resolver, true).contains("-Dtornado.tc.memoize=<boolean>  (default: true)  [internal]"));
    }

    @Test
    public void flagsPageIsUpToDate() throws IOException {
        String document = Files.readString(FLAGS_PAGE);
        assertEquals(OptionsDocTool.withGeneratedSection(document, renderer.rst()), document,
                "docs/source/flags.rst is out of date: run OptionsDocTool --rst-update docs/source/flags.rst");
    }
}
