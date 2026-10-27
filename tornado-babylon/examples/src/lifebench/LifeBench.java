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
package lifebench;

import java.lang.invoke.MethodHandles;
import java.util.Random;

import hat.Accelerator;
import hat.Accelerator.Compute;
import hat.backend.Backend;
import jdk.incubator.code.Reflect;
import life.CellGrid;
import life.ComputeLife;
import life.Control;

/**
 * HAT's Game of Life (hat/examples/life) without its window: generations per second of
 * ComputeLife.steps, one compute call of many dispatches, on whichever backend HAT finds first.
 * Checks the final grid against a plain Java run of the same generations.
 *
 * <pre>
 * ./run.sh -d 1:0 $H/hat-example-life-1.0.jar:build/examples lifebench.LifeBench [width] [height] [generations] [rounds]
 * </pre>
 */
public class LifeBench {

    public static void main(String[] args) {
        int w = args.length > 0 ? Integer.parseInt(args[0]) : 1024;
        int h = args.length > 1 ? Integer.parseInt(args[1]) : 1024;
        int gens = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST);
        var grid = CellGrid.create(accelerator, w, h);
        var random = new Random(42);
        byte[] java = new byte[w * h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (random.nextInt(4) == 0) {
                    grid.cell(x + y * w, CellGrid.ALIVE);
                    java[x + y * w] = CellGrid.ALIVE;
                }
            }
        }
        var control = Control.create(accelerator, grid);
        // the generation is in the first slice: from = 0, to = w * h
        control.from(0);
        control.to(w * h);
        System.out.println("backend: " + accelerator.backend.getClass().getSimpleName() + ", " + w + " x " + h + ", " + gens + " generations a round");
        int g = gens;
        accelerator.compute((@Reflect Compute) cc -> ComputeLife.steps(cc, control, grid, g)); // warm-up: compiles
        long best = Long.MAX_VALUE;
        for (int r = 0; r < rounds; r++) {
            long start = System.nanoTime();
            accelerator.compute((@Reflect Compute) cc -> ComputeLife.steps(cc, control, grid, g));
            best = Math.min(best, System.nanoTime() - start);
        }
        System.out.printf("best round: %.1f ms, %.0f generations per second%n", best / 1e6, gens / (best / 1e9));

        // check: the same number of generations in Java
        int total = gens * (rounds + 1);
        byte[] next = new byte[w * h];
        for (int s = 0; s < total; s++) {
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int count = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if ((dx != 0 || dy != 0) && java[(x + dx) + (y + dy) * w] == CellGrid.ALIVE) {
                                count++;
                            }
                        }
                    }
                    byte cell = java[x + y * w];
                    next[x + y * w] = (count == 3 || (count == 2 && cell == CellGrid.ALIVE)) ? CellGrid.ALIVE : CellGrid.DEAD;
                }
            }
            byte[] t = java;
            java = next;
            next = t;
        }
        int from = control.from();
        int wrong = 0;
        for (int i = 0; i < w * h; i++) {
            if (grid.cell(from + i) != java[i]) {
                wrong++;
            }
        }
        System.out.println(wrong == 0 ? "check: the grid equals Java's" : "check: WRONG, " + wrong + " cells differ");
    }
}
