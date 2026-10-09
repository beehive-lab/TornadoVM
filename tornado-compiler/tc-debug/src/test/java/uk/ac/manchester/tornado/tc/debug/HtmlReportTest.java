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
package uk.ac.manchester.tornado.tc.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.runtime.TcCompiler;
import uk.ac.manchester.tornado.tc.runtime.TcOptions;

public class HtmlReportTest {

    @Test
    public void reportIsSelfContainedAndShowsEveryStage() {
        var c = TcCompiler.compile(IrParser.parseFunction("""
                kernel @K.copy(%a: array<i32>, %b: array<i32>) -> void {
                ^entry:
                  %i = tornado.thread_id.global.x() : i32
                  %x = array.load %a[%i] : i32
                  array.store %b[%i], %x
                  ret
                }
                """), TargetCaps.CUDA_SM89, TcOptions.DEFAULTS.withReport(true, java.nio.file.Path.of("tc-dumps")));
        var html = HtmlReport.render(c);
        assertTrue(html.startsWith("<!doctype html>"));
        assertFalse("no external resources", html.contains("http://") || html.contains("https://") || html.contains("src="));
        for (var stage : List.of("input", "abi", "structure", "emit")) {
            assertTrue(stage, html.contains("&middot; " + stage + " <small>"));
        }
        assertTrue(html.contains("K_copy"));
        assertTrue("ABI lowering adds lines", html.contains("line(s) added since the previous IR stage"));
    }

    @Test
    public void addedLinesFollowALongestCommonSubsequence() {
        assertEquals(Set.of(1, 3), HtmlReport.addedLines(List.of("a", "b", "c"), List.of("a", "x", "b", "y", "c")));
        assertEquals(Set.of(), HtmlReport.addedLines(List.of("a", "b"), List.of("a", "b")));
    }
}
