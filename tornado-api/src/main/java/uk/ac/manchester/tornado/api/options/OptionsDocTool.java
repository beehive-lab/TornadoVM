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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point that lists the options or keeps their documentation up to date.
 *
 * <pre>
 *   OptionsDocTool --list [--all]      options and their current values, as {@code tornado --list-flags} prints them
 *   OptionsDocTool --rst-update FILE   regenerates the options section of a reStructuredText file
 *   OptionsDocTool --rst-check FILE    fails if that section is out of date
 * </pre>
 */
public final class OptionsDocTool {

    static final String BEGIN_MARKER = ".. BEGIN GENERATED OPTIONS (OptionsDocTool --rst-update; do not edit by hand)";
    static final String END_MARKER = ".. END GENERATED OPTIONS";

    private OptionsDocTool() {
    }

    public static void main(String[] args) throws IOException {
        List<String> arguments = List.of(args);
        OptionsRenderer renderer = new OptionsRenderer(OptionRegistry.catalog());
        if (arguments.isEmpty() || arguments.get(0).equals("--list")) {
            System.out.print(renderer.text(OptionResolver.global(), arguments.contains("--all")));
        } else if (arguments.size() == 2 && arguments.get(0).equals("--rst-update")) {
            Path file = Path.of(arguments.get(1));
            Files.writeString(file, withGeneratedSection(Files.readString(file), renderer.rst()));
        } else if (arguments.size() == 2 && arguments.get(0).equals("--rst-check")) {
            Path file = Path.of(arguments.get(1));
            String document = Files.readString(file);
            if (!document.equals(withGeneratedSection(document, renderer.rst()))) {
                System.err.println(file + " is out of date: run OptionsDocTool --rst-update " + file);
                System.exit(1);
            }
        } else {
            System.err.println("Usage: OptionsDocTool --list [--all] | --rst-update FILE | --rst-check FILE");
            System.exit(2);
        }
    }

    /**
     * @return the document with the text between its markers replaced by the generated section
     * @throws IllegalArgumentException
     *     if the document lacks the markers
     */
    static String withGeneratedSection(String document, String generated) {
        int begin = document.indexOf(BEGIN_MARKER);
        int end = document.indexOf(END_MARKER);
        if (begin < 0 || end < begin) {
            throw new IllegalArgumentException("The document has no '" + BEGIN_MARKER + "' ... '" + END_MARKER + "' section");
        }
        return document.substring(0, begin) + BEGIN_MARKER + "\n\n" + generated + END_MARKER + document.substring(end + END_MARKER.length());
    }
}
