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
package uk.ac.manchester.tornado.tc.runtime;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Options of a TC compilation, read from {@code -Dtornado.tc.*} system properties.
 *
 * <ul>
 * <li>{@code tornado.tc.dump}: {@code none} (default), {@code all}, or a comma-separated list of
 * stage names to write (for example {@code abi,emit}).</li>
 * <li>{@code tornado.tc.dumpDir}: directory for dumps and reports (default {@code tc-dumps}).</li>
 * <li>{@code tornado.tc.verify}: {@code each} (default) runs the verifier after every IR stage;
 * {@code end} only before emission.</li>
 * <li>{@code tornado.tc.only}: compile-and-dump only functions whose name contains this text.</li>
 * <li>{@code tornado.tc.report}: {@code html} writes a self-contained HTML report per compilation.</li>
 * </ul>
 */
public record TcOptions(Set<String> dump, Path dumpDir, boolean verifyEach, Optional<String> only, boolean htmlReport) {

    public static final TcOptions DEFAULTS = new TcOptions(Set.of(), Path.of("tc-dumps"), true, Optional.empty(), false);

    public static TcOptions fromSystemProperties() {
        var dump = System.getProperty("tornado.tc.dump", "none");
        var stages = switch (dump) {
            case "none", "" -> Set.<String>of();
            case "all" -> Set.of("all");
            default -> Set.of(dump.split(","));
        };
        return new TcOptions(stages, Path.of(System.getProperty("tornado.tc.dumpDir", "tc-dumps")), !"end".equals(System.getProperty("tornado.tc.verify", "each")),
                Optional.ofNullable(System.getProperty("tornado.tc.only")), "html".equals(System.getProperty("tornado.tc.report")));
    }

    public boolean dumps(String stage) {
        return dump.contains("all") || dump.contains(stage);
    }

    public boolean selects(String functionName) {
        return only.map(functionName::contains).orElse(true);
    }

    public TcOptions withReport(boolean html, Path dir) {
        return new TcOptions(dump, dir, verifyEach, only, html);
    }
}
