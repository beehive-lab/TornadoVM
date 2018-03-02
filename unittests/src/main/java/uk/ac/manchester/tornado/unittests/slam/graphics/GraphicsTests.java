/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science,
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Juan Fumero
 *
 */

package uk.ac.manchester.tornado.unittests.slam.graphics;

import static org.junit.Assert.assertEquals;
import static uk.ac.manchester.tornado.collections.graphics.GraphicsMath.rigidTransform;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.max;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.min;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.sqrt;
import static uk.ac.manchester.tornado.collections.types.Float2.mult;
import static uk.ac.manchester.tornado.collections.types.Float3.add;
import static uk.ac.manchester.tornado.collections.types.Float3.div;
import static uk.ac.manchester.tornado.collections.types.Float3.length;
import static uk.ac.manchester.tornado.collections.types.Float3.normalise;
import static uk.ac.manchester.tornado.collections.types.Float3.sub;
import static uk.ac.manchester.tornado.collections.types.VolumeOps.grad;
import static uk.ac.manchester.tornado.collections.types.VolumeOps.interp;

import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.collections.graphics.GraphicsMath;
import uk.ac.manchester.tornado.collections.graphics.Renderer;
import uk.ac.manchester.tornado.collections.math.TornadoMath;
import uk.ac.manchester.tornado.collections.types.Byte3;
import uk.ac.manchester.tornado.collections.types.Byte4;
import uk.ac.manchester.tornado.collections.types.Float2;
import uk.ac.manchester.tornado.collections.types.Float3;
import uk.ac.manchester.tornado.collections.types.Float4;
import uk.ac.manchester.tornado.collections.types.Float8;
import uk.ac.manchester.tornado.collections.types.FloatOps;
import uk.ac.manchester.tornado.collections.types.ImageByte3;
import uk.ac.manchester.tornado.collections.types.ImageByte4;
import uk.ac.manchester.tornado.collections.types.ImageFloat;
import uk.ac.manchester.tornado.collections.types.ImageFloat3;
import uk.ac.manchester.tornado.collections.types.ImageFloat8;
import uk.ac.manchester.tornado.collections.types.Int2;
import uk.ac.manchester.tornado.collections.types.Int3;
import uk.ac.manchester.tornado.collections.types.Matrix4x4Float;
import uk.ac.manchester.tornado.collections.types.Short2;
import uk.ac.manchester.tornado.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.collections.types.VolumeOps;
import uk.ac.manchester.tornado.collections.types.VolumeShort2;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class GraphicsTests extends TornadoTestBase {

	private static void testPhiNode(ImageFloat3 verticies, ImageFloat depths, Matrix4x4Float invK) {
		final float depth = depths.get(0, 0);
		final Float3 pix = new Float3(0, 0, 1f);
		final Float3 vertex = (depth > 0) ? Float3.mult(rotate(invK, pix), depth) : new Float3(0f, 0f, 0f);
		verticies.set(0, 0, vertex);
	}

	private static void testPhiNode2(ImageFloat3 verticies, ImageFloat depths, Matrix4x4Float invK) {
		final float depth = depths.get(0, 0);
		final Float3 pix = new Float3(0, 0, 1f);
		final Float3 vertex = (depth > 0) ? Float3.mult(rotate(invK, pix), depth) : new Float3();
		verticies.set(0, 0, vertex);
	}

	private static final Float3 rotate(Matrix4x4Float m, Float3 v) {
		final Float3 result = new Float3(Float3.dot(m.row(0).asFloat3(), v), Float3.dot(m.row(1).asFloat3(), v),
				Float3.dot(m.row(2).asFloat3(), v));
		return result;
	}

	private static void testRotate(Matrix4x4Float m, VectorFloat3 v, VectorFloat3 result) {
		for (@Parallel int i = 0; i < v.getLength(); i++) {
			Float3 r = rotate(m, v.get(i));
			result.set(i, r);
		}
	}

	@Test
	public void testRotate() {
		final int size = 4;
		Random r = new Random();

		Matrix4x4Float matrix4 = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				matrix4.set(i, j, j + r.nextFloat());
			}
		}

		VectorFloat3 vector3 = new VectorFloat3(size);
		VectorFloat3 result = new VectorFloat3(size);
		VectorFloat3 sequential = new VectorFloat3(size);

		for (int i = 0; i < size; i++) {
			vector3.set(i, new Float3(1f, 2f, 3f));
		}

		// Sequential execution
		testRotate(matrix4, vector3, sequential);

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::testRotate, matrix4, vector3, result)
            .streamOut(result)
            .execute();        
        // @formatter:on

		for (int i = 0; i < size; i++) {
			Float3 o = result.get(i);
			Float3 s = sequential.get(i);
			assertEquals(s.getS0(), o.getS0(), 0.001);
			assertEquals(s.getS1(), o.getS1(), 0.001);
			assertEquals(s.getS1(), o.getS1(), 0.001);
		}
	}

	@Test
	public void testDepth2Vertex() {

		final int size = 4;
		Random r = new Random();

		Matrix4x4Float matrix4 = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				matrix4.set(i, j, j + r.nextFloat());
			}
		}

		ImageFloat3 vertext = new ImageFloat3(size, size);
		ImageFloat depth = new ImageFloat(size, size);

		ImageFloat3 sequential = new ImageFloat3(size, size);

		for (int i = 0; i < size; i++) {
			depth.set(i, r.nextFloat());
			for (int j = 0; j < size; j++) {
				vertext.set(i, j, new Float3(1f, 2f, 3f));
			}
		}

		// Sequential execution
		GraphicsMath.depth2vertex(sequential, depth, matrix4);

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsMath::depth2vertex, vertext, depth, matrix4)
            .streamOut(vertext)
            .execute();        
        // @formatter:on

		for (int i = 0; i < size; i++) {
			Float3 o = vertext.get(i);
			Float3 s = sequential.get(i);
			assertEquals(s.getS0(), o.getS0(), 0.001);
			assertEquals(s.getS1(), o.getS1(), 0.001);
			assertEquals(s.getS1(), o.getS1(), 0.001);
		}

	}

	@Test
	public void testPhiNode() {

		final int size = 4;
		Random r = new Random();

		Matrix4x4Float matrix4 = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				matrix4.set(i, j, j + r.nextFloat());
			}
		}

		ImageFloat3 vertext = new ImageFloat3(size, size);
		ImageFloat depth = new ImageFloat(size, size);

		for (int i = 0; i < size; i++) {
			depth.set(i, r.nextFloat());
			for (int j = 0; j < size; j++) {
				vertext.set(i, j, new Float3(1f, 2f, 3f));
			}
		}

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::testPhiNode, vertext, depth, matrix4)
            .streamOut(vertext)
            .execute();        
        // @formatter:on

	}

	@Test
	public void testPhiNode2() {

		final int size = 4;
		Random r = new Random();

		Matrix4x4Float matrix4 = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				matrix4.set(i, j, j + r.nextFloat());
			}
		}

		ImageFloat3 vertext = new ImageFloat3(size, size);
		ImageFloat depth = new ImageFloat(size, size);

		for (int i = 0; i < size; i++) {
			depth.set(i, r.nextFloat());
			for (int j = 0; j < size; j++) {
				vertext.set(i, j, new Float3(1f, 2f, 3f));
			}
		}

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::testPhiNode2, vertext, depth, matrix4)
            .streamOut(vertext)
            .execute();        
        // @formatter:on

	}

	public static void computeRigidTransform(Matrix4x4Float matrix, VectorFloat3 points, VectorFloat3 output) {
		for (@Parallel int i = 0; i < points.getLength(); i++) {
			Float3 p = GraphicsMath.rigidTransform(matrix, points.get(i));
			output.set(i, p);
		}
	}

	@Test
	public void testRigidTrasform() {

		final int size = 4;
		Random r = new Random();

		Matrix4x4Float matrix4 = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				matrix4.set(i, j, j + r.nextFloat());
			}
		}

		VectorFloat3 point = new VectorFloat3(size);
		for (int i = 0; i < 4; i++) {
			point.set(i, new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat()));
		}

		VectorFloat3 sequential = new VectorFloat3(size);
		VectorFloat3 output = new VectorFloat3(size);

		// Sequential execution
		computeRigidTransform(matrix4, point, sequential);

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::computeRigidTransform, matrix4, point, output)
            .streamOut(output)
            .execute();        
        // @formatter:on

		for (int i = 0; i < size; i++) {
			Float3 o = output.get(i);
			Float3 s = sequential.get(i);
			assertEquals(s.getS0(), o.getS0(), 0.001);
			assertEquals(s.getS1(), o.getS1(), 0.001);
			assertEquals(s.getS1(), o.getS1(), 0.001);
		}
	}

	// Ray Cast testing

	private static final float INVALID = -2;

	public static final void raycast(ImageFloat3 verticies, ImageFloat3 normals, VolumeShort2 volume, Float3 volumeDims,
			Matrix4x4Float view, float nearPlane, float farPlane, float largeStep, float smallStep) {

		for (@Parallel int y = 0; y < verticies.Y(); y++) {
			for (@Parallel int x = 0; x < verticies.X(); x++) {
				final Float4 hit = GraphicsMath.raycastPoint(volume, volumeDims, x, y, view, nearPlane, farPlane,
						smallStep, largeStep);

				final Float3 normal;
				final Float3 position;
				if (hit.getW() > 0f) {
					position = hit.asFloat3();

					final Float3 surfNorm = VolumeOps.grad(volume, volumeDims, position);

					// final Float3 surfNorm = new Float3(0f, 0f, 0f);

					if (length(surfNorm) != 0) {
						normal = normalise(surfNorm);
					} else {
						normal = new Float3(INVALID, 0f, 0f);
					}
				} else {
					normal = new Float3(INVALID, 0f, 0f);
					position = new Float3();
				}

				verticies.set(x, y, position);
				normals.set(x, y, normal);
			}
		}
	}

	public static final Float4 raycastPoint(final VolumeShort2 volume, final Float3 dim, final int x, final int y,
			final Matrix4x4Float view, float nearPlane, float farPlane, float smallStep, float largeStep) {

		final Float3 position = new Float3(x, y, 1f);

		// retrieve translation from matrix (col 3, elements =3 )
		final Float3 origin = view.column(3).asFloat3();

		final Float3 direction = rotate(view, position);
		final Float3 invR = div(new Float3(1f, 1f, 1f), direction);

		final Float3 tbot = Float3.mult(Float3.mult(invR, origin), -1f);
		final Float3 ttop = Float3.mult(invR, sub(dim, origin));

		final Float3 tmin = Float3.min(ttop, tbot);
		final Float3 tmax = Float3.max(ttop, tbot);

		final float largestTmin = Float3.max(tmin);
		final float smallestTmax = Float3.min(tmax);

		final float tnear = max(largestTmin, nearPlane);
		final float tfar = min(smallestTmax, farPlane);

		if (tnear < tfar) {

			float t = tnear;
			float stepsize = largeStep;

			Float3 pos = add(Float3.mult(direction, t), origin);

			float f_t = interp(volume, dim, pos);

			float f_tt = 0f;
			if (f_t > 0) {
				for (; t < tfar; t += stepsize) {
					pos = add(Float3.mult(direction, t), origin);

					f_tt = interp(volume, dim, pos);

					if (f_tt < 0f) {
						break;
					}

					if (f_tt < 0.8f) {
						stepsize = smallStep;
					}

					f_t = f_tt;
				}

				if (f_tt < 0) {
					t = t + ((stepsize * f_tt) / (f_t - f_tt));
					pos = add(Float3.mult(direction, t), origin);
					return new Float4(pos.getX(), pos.getY(), pos.getZ(), t);
				}
			}

		}

		return new Float4();
	}

	@Test
	public void raycastTest() {

		final int size = 4;
		Random r = new Random();

		Matrix4x4Float view = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				view.set(i, j, j + r.nextFloat());
			}
		}

		ImageFloat3 verticies = new ImageFloat3(size, size);
		ImageFloat3 normals = new ImageFloat3(size, size);
		VolumeShort2 volume = new VolumeShort2(size, size, size);
		Float3 volumeDims = new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat());

		float nearPlane = r.nextFloat();
		float farPlane = r.nextFloat();
		float largeStep = r.nextFloat();
		float smallStep = r.nextFloat();

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				verticies.set(i, j, new Float3(1f, 2f, 3f));
				normals.set(i, j, new Float3(1f, 2f, 3f));
			}
		}

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				for (int k = 0; k < size; k++) {
					volume.set(i, j, k, new Short2((short) 1, (short) 2));
				}
			}
		}

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::raycast, verticies, normals, volume, volumeDims, view, nearPlane, farPlane, largeStep, smallStep)
            .streamOut(verticies, normals)
            .execute();        
        // @formatter:on
	}

	public static final void testRayCastPointIsolation(VectorFloat4 output, ImageFloat3 normals, VolumeShort2 volume,
			Float3 volumeDims, Matrix4x4Float view, float nearPlane, float farPlane, float largeStep, float smallStep) {
		for (@Parallel int y = 0; y < normals.Y(); y++) {
			for (@Parallel int x = 0; x < normals.X(); x++) {
				final Float4 hit = GraphicsMath.raycastPoint(volume, volumeDims, x, y, view, nearPlane, farPlane,
						smallStep, largeStep);
				output.set(y * normals.X() + x, hit);
			}
		}
	}

	@Test
	public void testRaycastPoint() {

		final int size = 4;
		Random r = new Random();

		Matrix4x4Float view = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				view.set(i, j, j + r.nextFloat());
			}
		}

		ImageFloat3 normals = new ImageFloat3(size, size);
		VolumeShort2 volume = new VolumeShort2(size, size, size);
		Float3 volumeDims = new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat());
		VectorFloat4 output = new VectorFloat4(size * size);

		float nearPlane = r.nextFloat();
		float farPlane = r.nextFloat();
		float largeStep = r.nextFloat();
		float smallStep = r.nextFloat();

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				normals.set(i, j, new Float3(1f, 2f, 3f));
			}
		}

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				for (int k = 0; k < size; k++) {
					volume.set(i, j, k, new Short2((short) 1, (short) 2));
				}
			}
		}

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::testRayCastPointIsolation, output, normals, volume, volumeDims, view, nearPlane, farPlane, largeStep, smallStep)
            .streamOut(output, normals)
            .execute();        
        // @formatter:on

	}

	public static void integrate(ImageFloat filteredDepthImage, Matrix4x4Float invTrack, Matrix4x4Float K,
			Float3 volumeDims, VolumeShort2 volume, float mu, float maxWeight) {
		final Float3 tmp = new Float3(0f, 0f, volumeDims.getZ() / volume.Z());

		final Float3 integrateDelta = rotate(invTrack, tmp);
		final Float3 cameraDelta = rotate(K, integrateDelta);

		for (@Parallel int y = 0; y < volume.Y(); y++) {
			for (@Parallel int x = 0; x < volume.X(); x++) {

				final Int3 pix = new Int3(x, y, 0);
				Float3 pos = rigidTransform(invTrack, pos(volume, volumeDims, pix));
				Float3 cameraX = rigidTransform(K, pos);

				for (int z = 0; z < volume.Z(); z++, pos = add(pos, integrateDelta), cameraX = add(cameraX,
						cameraDelta)) {

					if (pos.getZ() < 0.0001f) // arbitrary near plane constant
					{
						continue;
					}

					final Float2 pixel = new Float2((cameraX.getX() / cameraX.getZ()) + 0.5f,
							(cameraX.getY() / cameraX.getZ()) + 0.5f);

					if ((pixel.getX() < 0) || (pixel.getX() > (filteredDepthImage.X() - 1)) || (pixel.getY() < 0)
							|| (pixel.getY() > (filteredDepthImage.Y() - 1))) {
						continue;
					}

					final Int2 px = new Int2((int) pixel.getX(), (int) pixel.getY());

					final float depth = filteredDepthImage.get(px.getX(), px.getY());

					if (depth == 0) {
						continue;
					}

					final float diff = (depth - cameraX.getZ())
							* sqrt(1f + FloatOps.sq(pos.getX() / pos.getZ()) + FloatOps.sq(pos.getY() / pos.getZ()));

					if (diff > -mu) {

						final float sdf = min(1f, diff / mu);

						final Short2 inputValue = volume.get(x, y, z);
						final Float2 constantValue1 = new Float2(0.00003051944088f, 1f);
						final Float2 constantValue2 = new Float2(32766.0f, 1f);

						final Float2 data = mult(new Float2(inputValue.getX(), inputValue.getY()), constantValue1);

						final float dx = TornadoMath.clamp(((data.getY() * data.getX()) + sdf) / (data.getY() + 1f),
								-1f, 1f);
						final float dy = min(data.getY() + 1f, maxWeight);

						final Float2 floatValue = mult(new Float2(dx, dy), constantValue2);
						final Short2 outputValue = new Short2((short) floatValue.getX(), (short) floatValue.getY());

						volume.set(x, y, z, outputValue);
					}
				}
			}
		}
	}

	private static Float3 pos(final VolumeShort2 volume, final Float3 volumeDims, final Int3 p) {
		return new Float3(((p.getX() + 0.5f) * volumeDims.getX()) / volume.X(),
				((p.getY() + 0.5f) * volumeDims.getY()) / volume.Y(),
				((p.getZ() + 0.5f) * volumeDims.getZ()) / volume.Z());
	}

	private float random(Random r) {
		return r.nextFloat() * 10;
	}

	@Test
	public void testIntegrate() {

		final int size = 100;
		Random r = new Random();

		Matrix4x4Float invTrack = new Matrix4x4Float();
		Matrix4x4Float m2 = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				invTrack.set(i, j, j + random(r));
				m2.set(i, j, j + random(r));
			}
		}

		ImageFloat filteredDepthImage = new ImageFloat(size, size);
		VolumeShort2 volume = new VolumeShort2(size, size, size);
		VolumeShort2 sequential = new VolumeShort2(size, size, size);
		Float3 volumeDims = new Float3(random(r), random(r), random(r));

		float mu = random(r);
		float maxW = random(r);

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				filteredDepthImage.set(i, j, random(r));
			}
		}

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				for (int k = 0; k < size; k++) {
					volume.set(i, j, k, new Short2((short) 2, (short) 3));
					sequential.set(i, j, k, new Short2((short) 2, (short) 3));
				}
			}
		}

		integrate(filteredDepthImage, invTrack, m2, volumeDims, sequential, mu, maxW);

		// @formatter:off
        TaskSchedule task = new TaskSchedule("t0")
            .task("s0", GraphicsTests::integrate, filteredDepthImage, invTrack, m2, volumeDims, volume, mu, maxW)
            .streamOut(volume);        
        // @formatter:on

		int c = 0;
		while (c++ < 10) {
			task.execute();
		}

		// Check result
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				for (int k = 0; k < size; k++) {
					Short2 got = volume.get(i, j, k);
					Short2 expected = sequential.get(i, j, k);
					assertEquals(expected.getX(), got.getX());
					assertEquals(expected.getY(), got.getY());
				}
			}
		}
	}

	@Test
	public void testRenderTrack() {
		final int size = 4;
		Random r = new Random();

		ImageByte3 output = new ImageByte3(size, size);
		ImageByte3 sequential = new ImageByte3(size, size);
		ImageFloat8 track = new ImageFloat8(size, size);

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				track.set(i, j, new Float8(random(r), random(r), random(r), random(r), random(r), random(r), random(r),
						random(r)));
			}
		}

		Renderer.renderTrack(sequential, track);

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", Renderer::renderTrack, output, track)
            .streamOut(output)
            .execute();        
        // @formatter:on

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				Byte3 o = output.get(i, j);
				Byte3 expected = sequential.get(i, j);
				assertEquals(expected.getX(), o.getX());
				assertEquals(expected.getY(), o.getY());
				assertEquals(expected.getZ(), o.getZ());
			}
		}
	}

	public static void volumeOps(VectorFloat3 output, VolumeShort2 volume, final Float3 dim, final Float3 point) {
		for (@Parallel int i = 0; i < output.getLength(); i++) {
			Float3 f = VolumeOps.grad(volume, dim, point);
			output.set(i, f);
		}
	}

	@Test
	public void testVolumeGrad() {
		final int size = 4;
		Random r = new Random();

		VolumeShort2 volume = new VolumeShort2(size, size, size);
		VectorFloat3 output = new VectorFloat3(size * size);

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				for (int k = 0; k < size; k++) {
					volume.set(i, j, k, new Short2((short) 1, (short) 2));
				}
			}
		}

		Float3 dim = new Float3(random(r), random(r), random(r));
		Float3 point = new Float3(random(r), random(r), random(r));
		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::volumeOps, output, volume, dim, point)
            .streamOut(output)
            .execute();        
        // @formatter:on

	}

	/**
	 * * Creates a 4x4 matrix representing the intrinsic camera matrix
	 *
	 * @param k
	 *            - camera parameters {f_x,f_y,x_0,y_0} where {f_x,f_y}
	 *            specifies the focal length of the camera and {x_0,y_0} the
	 *            principle point
	 * @param m
	 *            - returned matrix
	 */
	public static void getCameraMatrix(Float4 k, Matrix4x4Float m) {
		m.fill(0f);

		// focal length - f_x
		m.set(0, 0, k.getX());
		// focal length - f_y
		m.set(1, 1, k.getY());

		// principle point - x_0
		m.set(0, 2, k.getZ());

		// principle point - y_0
		m.set(1, 2, k.getW());

		m.set(2, 2, 1);
		m.set(3, 3, 1);
	}

	@Test
	public void testCameraMatrix() {

		Float4 f = new Float4(1f, 2f, 3f, 4f);
		Matrix4x4Float m = new Matrix4x4Float();
		Matrix4x4Float seq = new Matrix4x4Float();

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::getCameraMatrix, f, m)
            .streamOut(m)
            .execute();        
        // @formatter:on

	}

	public static void renderVolume(ImageByte4 output, VolumeShort2 volume, Float3 volumeDims, Matrix4x4Float view,
			float nearPlane, float farPlane, float smallStep, float largeStep, Float3 light, Float3 ambient) {

		for (@Parallel int y = 0; y < output.Y(); y++) {
			for (@Parallel int x = 0; x < output.X(); x++) {

				final Float4 hit = raycastPoint(volume, volumeDims, x, y, view, nearPlane, farPlane, smallStep,
						largeStep);

				final Byte4 pixel;
				if (hit.getW() > 0) {
					final Float3 test = hit.asFloat3();
					final Float3 surfNorm = grad(volume, volumeDims, test);

					if (Float3.length(surfNorm) > 0) {
						final Float3 diff = Float3.normalise(Float3.sub(light, test));

						final Float3 normalizedSurfNorm = Float3.normalise(surfNorm);

						final float dir = Math.max(Float3.dot(normalizedSurfNorm, diff), 0f);
						Float3 col = add(new Float3(dir, dir, dir), ambient);

						col = Float3.clamp(col, 0f, 1f);
						col = Float3.mult(col, 255f);

						pixel = new Byte4((byte) col.getX(), (byte) col.getY(), (byte) col.getZ(), (byte) 0);
					} else {
						pixel = new Byte4();
					}
				} else {
					pixel = new Byte4();
				}

				output.set(x, y, pixel);

			}
		}
	}

	@Test
	public void testRenderVolume() {
		final int size = 4;
		Random r = new Random();

		Matrix4x4Float view = new Matrix4x4Float();
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				view.set(i, j, j + r.nextFloat());
			}
		}

		ImageByte4 output = new ImageByte4(size, size);
		VolumeShort2 volume = new VolumeShort2(size, size, size);
		Float3 volumeDims = new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat());

		float nearPlane = r.nextFloat();
		float farPlane = r.nextFloat();
		float largeStep = r.nextFloat();
		float smallStep = r.nextFloat();
		Float3 light = new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat());
		Float3 ambient = new Float3(r.nextFloat(), r.nextFloat(), r.nextFloat());

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				output.set(i, j, new Byte4((byte) 1, (byte) 2, (byte) 3, (byte) 4));
			}
		}

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				for (int k = 0; k < size; k++) {
					volume.set(i, j, k, new Short2((short) 1, (short) 2));
				}
			}
		}

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::renderVolume, output, volume, volumeDims, view, nearPlane, farPlane, largeStep, smallStep, light, ambient)
            .streamOut(output)
            .execute();        
        // @formatter:on
	}

	public static void reduceValues(float[] sums, int startIndex, ImageFloat8 trackingResults, int resultIndex) {

		final int jtj = startIndex + 7;
		final int info = startIndex + 28;

		Float8 value = trackingResults.get(resultIndex);
		final int result = (int) value.getS7();
		final float error = value.getS6();

		if (result < 1) {
			sums[info + 1] += (result == -4) ? 1 : 0;
			sums[info + 2] += (result == -5) ? 1 : 0;
			sums[info + 3] += (result > -4) ? 1 : 0;
			return;
		}

		// float base[0] += error^2
		sums[startIndex] += (error * error);

		// Float6 base(+1) += row.scale(error)
		// for (int i = 0; i < 6; i++) {
		// sums[startIndex + 0 + 1] += error * value.get(0);
		// sums[startIndex + i + 1] = value.get(i);
		// }

		sums[startIndex + 0 + 1] += error * value.getS0();
		sums[startIndex + 1 + 1] += error * value.getS1();
		sums[startIndex + 2 + 1] += error * value.getS2();
		sums[startIndex + 3 + 1] += error * value.getS3();
		sums[startIndex + 4 + 1] += error * value.getS4();
		sums[startIndex + 5 + 1] += error * value.getS5();

		// is this jacobian transpose jacobian?
		sums[jtj + 0] += (value.getS0() * value.getS0());
		sums[jtj + 1] += (value.getS0() * value.getS1());
		sums[jtj + 2] += (value.getS0() * value.getS2());
		sums[jtj + 3] += (value.getS0() * value.getS3());

		sums[jtj + 4] += (value.getS0() * value.getS4());
		sums[jtj + 5] += (value.getS0() * value.getS5());

		sums[jtj + 6] += (value.getS1() * value.getS1());
		sums[jtj + 7] += (value.getS1() * value.getS2());
		sums[jtj + 8] += (value.getS1() * value.getS3());
		sums[jtj + 9] += (value.getS1() * value.getS4());
		sums[jtj + 10] += (value.getS1() * value.getS5());

		sums[jtj + 11] += (value.getS2() * value.getS2());
		sums[jtj + 12] += (value.getS2() * value.getS3());
		sums[jtj + 13] += (value.getS2() * value.getS4());
		sums[jtj + 14] += (value.getS2() * value.getS5());

		sums[jtj + 15] += (value.getS3() * value.getS3());
		sums[jtj + 16] += (value.getS3() * value.getS4());
		sums[jtj + 17] += (value.getS3() * value.getS5());

		sums[jtj + 18] += (value.getS4() * value.getS4());
		sums[jtj + 19] += (value.getS4() * value.getS5());

		sums[jtj + 20] += (value.getS5() * value.getS5());

		sums[info]++;
	}

	public static void mapReduce(final float[] output, final ImageFloat8 input) {
		final int numThreads = output.length / 32;
		final int numElements = input.X() * input.Y();

		for (@Parallel int i = 0; i < numThreads; i++) {
			final int startIndex = i * 32;
			for (int j = 0; j < 32; j++) {
				output[startIndex + j] = 0f;
			}

			for (int j = i; j < numElements; j += numThreads) {
				reduceValues(output, startIndex, input, j);
			}
		}
	}

	public static void mapReduce2(float[] output, ImageFloat8 input) {
		int startIndex = 0 * 32;
		reduceValues(output, startIndex, input, 0);
	}

	public static void mapReduce3(VectorFloat4 output, final VectorFloat4 input) {
		for (@Parallel int i = 0; i < input.getLength(); i++) {
			Float4 f = input.get(i);
			Float4 ff = new Float4(f.get(3), f.get(2), f.get(1), f.get(0));
			output.set(i, ff);
			// f.get(0)));
			// output.set(i, Float4.add(input.get(i), input.get(i)));
		}
	}

	private Float8 createFloat8() {
		Random r = new Random();
		Float8 f = new Float8(random(r), random(r), random(r), random(r), random(r), random(r), random(r), random(r));
		return f;
	}

	private Float4 createFloat4() {
		Random r = new Random();
		Float4 f = new Float4(random(r), random(r), random(r), random(r));
		return f;
	}

	@Test
	public void testMapReduceSlam() {

		final int size = 16;
		float[] output = new float[size];

		ImageFloat8 image = new ImageFloat8(size, size);

		for (int i = 0; i < image.X(); i++) {
			for (int j = 0; j < image.X(); j++) {
				Float8 f = createFloat8();
				image.set(i, j, f);
			}
		}

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::mapReduce, output, image)
            .streamOut(output)
            .execute();        
        // @formatter:on
	}

	@Test
	public void testMapReduceSlam2() {

		final int size = 16;
		float[] output = new float[size];

		ImageFloat8 image = new ImageFloat8(size, size);
		for (int i = 0; i < image.X(); i++) {
			for (int j = 0; j < image.X(); j++) {
				Float8 f = createFloat8();
				image.set(i, j, f);
			}
		}

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::mapReduce2, output, image)
            .streamOut(output)
            .execute();        
        // @formatter:on
	}

	@Ignore
	public void testMapReduceSlam3() {

		final int size = 16;

		VectorFloat4 input = new VectorFloat4(size);
		VectorFloat4 output = new VectorFloat4(size);

		for (int i = 0; i < input.getLength(); i++) {
			Float4 f = createFloat4();
			input.set(i, f);
		}

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::mapReduce3, output, input)
            .streamOut(output)
            .execute();        
        // @formatter:on
	}

	public static void testVSKernel(int[] x, int[] y, int[] z, VolumeShort2 v, float[] output) {
		for (@Parallel int i = 0; i < x.length; i++) {
			output[i] = VolumeOps.vs1(x[i], y[i], z[i], v);
		}
	}

	@Test
	public void testVS() {

		int size = 256;
		int[] x = new int[size];
		int[] y = new int[size];
		int[] z = new int[size];

		float[] output = new float[size];
		float[] seq = new float[size];

		IntStream.range(0, size).parallel().forEach(i -> {
			x[i] = i;
			y[i] = i;
			z[i] = i;
		});

		VolumeShort2 volume = new VolumeShort2(size, size, size);

		for (int i = 0; i < volume.X(); i++) {
			for (int j = 0; j < volume.Y(); j++) {
				for (int k = 0; k < volume.Z(); k++) {
					volume.set(i, j, k, new Short2((short) 1, (short) 2));
				}
			}
		}

		testVSKernel(x, y, z, volume, seq);

		// @formatter:off
        new TaskSchedule("t0")
            .task("s0", GraphicsTests::testVSKernel, x, y, z, volume, output)
            .streamOut(output)
            .execute();        
        // @formatter:on

		for (int i = 0; i < output.length; i++) {
			assertEquals(seq[i], output[i], 0.001f);
		}
	}

}
