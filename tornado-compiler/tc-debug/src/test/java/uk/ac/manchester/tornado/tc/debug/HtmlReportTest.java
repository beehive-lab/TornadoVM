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
package uk.ac.manchester.tornado.tc.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

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
                  %i = tornado.thread_id.global<dim = 0>() : i32
                  %x = array.load %a[%i] : i32
                  array.store %b[%i], %x
                  ret
                }
                """), TargetCaps.CUDA_SM89, TcOptions.DEFAULTS.withReport(true, java.nio.file.Path.of("tc-dumps")));
        var html = HtmlReport.render(c);
        assertTrue(html.startsWith("<!doctype html>"));
        assertFalse(html.contains("http://") || html.contains("https://") || html.contains("src="), "no external resources");
        for (var stage : List.of("input", "abi", "structure", "emit")) {
            assertTrue(html.contains("&middot; " + stage + " <small>"), stage);
        }
        assertTrue(html.contains("K_copy"));
        assertTrue(html.contains("line(s) added since the previous IR stage"), "ABI lowering adds lines");
    }

    @Test
    public void addedLinesFollowALongestCommonSubsequence() {
        assertEquals(Set.of(1, 3), HtmlReport.addedLines(List.of("a", "b", "c"), List.of("a", "x", "b", "y", "c")));
        assertEquals(Set.of(), HtmlReport.addedLines(List.of("a", "b"), List.of("a", "b")));
    }
}
