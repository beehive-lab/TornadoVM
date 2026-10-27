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

import hat.Accelerator;
import hat.Accelerator.Compute;
import jdk.incubator.code.Reflect;
import life.CellGrid;
import life.ComputeLife;
import life.Control;
import life.Viewer;
import io.github.robertograham.rleparser.RleParser;
import io.github.robertograham.rleparser.domain.PatternData;

/**
 * HAT's Game of Life demo with its window (hat/examples/life, the same setup as life.Main), started
 * without its start button: the compute loop runs generations and refreshes the viewer at its frame
 * rate. Prints the generations per second over {@code seconds}, then exits.
 *
 * <pre>
 * DISPLAY=:0 ./run.sh -d 1:0 $H/hat-example-life-1.0.jar:build/examples lifebench.LifeWindow [seconds]
 * </pre>
 */
public class LifeWindow {

    public static void main(String[] args) throws Exception {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        Accelerator accelerator = new Accelerator(MethodHandles.lookup());
        PatternData patternData = RleParser.readPatternData(ComputeLife.class.getClassLoader().getResourceAsStream("orig.rle"));
        CellGrid cellGrid = CellGrid.create(accelerator, patternData.getMetaData().getWidth() + 2, patternData.getMetaData().getHeight() + 2);
        patternData.getLiveCells().getCoordinates().forEach(c -> cellGrid.cell((1 + c.getX()) + (1 + c.getY()) * cellGrid.width(), CellGrid.ALIVE));
        Control control = Control.create(accelerator, cellGrid);
        Viewer.State state = new Viewer.State();
        Viewer viewer = new Viewer("Life", cellGrid, state);
        var tempFrom = control.from();
        control.from(control.to());
        control.to(tempFrom);
        viewer.mainPanel.repaint();
        String backend = accelerator.backend.getClass().getSimpleName() + (accelerator.config().minimizeCopies() ? " (HAT=MC)" : "");
        System.out.println("backend: " + backend + ", grid " + cellGrid.width() + " x " + cellGrid.height());

        Thread sampler = new Thread(() -> {
            try {
                Thread.sleep(2000); // warm-up: compilation, first frames
                long g0 = state.generations;
                long t0 = System.nanoTime();
                Thread.sleep(seconds * 1000L);
                long g1 = state.generations;
                double s = (System.nanoTime() - t0) / 1e9;
                System.out.printf("%d generations in %.1f s with the window refreshing: %.0f generations per second%n", g1 - g0, s, (g1 - g0) / s);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        });
        sampler.setDaemon(true);
        sampler.start();
        int frameDelayMillis = 0;
        accelerator.compute((@Reflect Compute) cc -> ComputeLife.compute(cc, viewer, control, cellGrid, frameDelayMillis));
    }
}
