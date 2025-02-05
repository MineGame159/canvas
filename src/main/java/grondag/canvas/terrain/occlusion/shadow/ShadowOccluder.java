/*
 *  Copyright 2019, 2020 grondag
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.canvas.terrain.occlusion.shadow;

import static grondag.bitraster.Constants.DOWN;
import static grondag.bitraster.Constants.EAST;
import static grondag.bitraster.Constants.NORTH;
import static grondag.bitraster.Constants.SOUTH;
import static grondag.bitraster.Constants.UP;
import static grondag.bitraster.Constants.WEST;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.math.Vector4f;

import grondag.bitraster.OrthoRasterizer;
import grondag.bitraster.PackedBox;
import grondag.canvas.mixinterface.Matrix4fExt;
import grondag.canvas.render.frustum.TerrainFrustum;
import grondag.canvas.shader.data.ShadowMatrixData;
import grondag.canvas.terrain.occlusion.base.AbstractOccluder;
import grondag.canvas.terrain.region.RegionPosition;

public class ShadowOccluder extends AbstractOccluder {
	private final Matrix4f shadowViewMatrix = new Matrix4f();
	private final Matrix4fExt shadowViewMatrixExt = (Matrix4fExt) (Object) shadowViewMatrix;

	public final Matrix4f shadowProjMatrix = new Matrix4f();
	private final Matrix4fExt shadowProjMatrixExt = (Matrix4fExt) (Object) shadowProjMatrix;

	private float maxRegionExtent;
	private float r0, x0, y0, z0, r1, x1, y1, z1, r2, x2, y2, z2, r3, x3, y3, z3;
	private int lastViewVersion;
	private Vec3d lastCameraPos;
	private grondag.bitraster.BoxOccluder.BoxTest clearTest;
	private grondag.bitraster.BoxOccluder.BoxTest occludedTest;
	private grondag.bitraster.BoxOccluder.BoxDraw draw;

	public ShadowOccluder(String rasterName) {
		super(new OrthoRasterizer(), rasterName);
	}

	public void copyState(TerrainFrustum occlusionFrustum) {
		shadowViewMatrixExt.set(ShadowMatrixData.shadowViewMatrix);
		shadowProjMatrixExt.set(ShadowMatrixData.maxCascadeProjMatrix());
		maxRegionExtent = ShadowMatrixData.regionMaxExtent();
		final float[] cascadeCentersAndRadii = ShadowMatrixData.cascadeCentersAndRadii;
		x0 = cascadeCentersAndRadii[0];
		y0 = cascadeCentersAndRadii[1];
		z0 = cascadeCentersAndRadii[2];
		r0 = cascadeCentersAndRadii[3];

		x1 = cascadeCentersAndRadii[4];
		y1 = cascadeCentersAndRadii[5];
		z1 = cascadeCentersAndRadii[6];
		r1 = cascadeCentersAndRadii[7];

		x2 = cascadeCentersAndRadii[8];
		y2 = cascadeCentersAndRadii[9];
		z2 = cascadeCentersAndRadii[10];
		r2 = cascadeCentersAndRadii[11];

		x3 = cascadeCentersAndRadii[12];
		y3 = cascadeCentersAndRadii[13];
		z3 = cascadeCentersAndRadii[14];
		r3 = cascadeCentersAndRadii[15];

		lastCameraPos = occlusionFrustum.lastCameraPos();
		lastViewVersion = occlusionFrustum.viewVersion();
	}

	@Override
	public void prepareRegion(RegionPosition origin) {
		super.prepareRegion(origin.getX(), origin.getY(), origin.getZ(), PackedBox.RANGE_MID, origin.shadowDistanceRank());
	}

	/**
	 * Check if needs redrawn and prep for redraw if so.
	 * When false, regions should be drawn only if their occluder version is not current.
	 */
	@Override
	public boolean prepareScene() {
		return super.prepareScene(lastViewVersion, lastCameraPos.x, lastCameraPos.y, lastCameraPos.z, shadowViewMatrixExt::copyTo, shadowProjMatrixExt::copyTo);
	}

	/**
	 * Smallest cascade on which a region at given origin can cast a shadow.
	 * Returns -1 if not within shadow map.
	 */
	public int cascade(RegionPosition regionPosition) {
		// Compute center position in light space
		final Vector4f lightSpaceRegionCenter = new Vector4f();
		lightSpaceRegionCenter.set(regionPosition.cameraRelativeCenterX(), regionPosition.cameraRelativeCenterY(), regionPosition.cameraRelativeCenterZ(), 1.0f);
		lightSpaceRegionCenter.transform(ShadowMatrixData.shadowViewMatrix);

		final float centerX = lightSpaceRegionCenter.getX();
		final float centerY = lightSpaceRegionCenter.getY();
		final float centerZ = lightSpaceRegionCenter.getZ();
		final float extent = maxRegionExtent;

		// <= extent = at least partially in
		// < -extent = fully in
		// > extent not in

		final float dx0 = Math.abs(centerX - x0) - r0;
		final float dy0 = Math.abs(centerY - y0) - r0;
		final float dz0 = (centerZ - z0) + r0;

		//		if (dz0 < -extent) {
		//			System.out.println("regin behind zero cascade");
		//		}

		if (dx0 > extent || dy0 > extent || dz0 < -extent) {
			// not a shadow caster
			return -1;
		}

		final float dx3 = Math.abs(centerX - x3) - r3;
		final float dy3 = Math.abs(centerY - y3) - r3;
		final float dz3 = (centerZ - z3) + r3;

		//		if (dz3 < -extent) {
		//			System.out.println("regin behind 3 cascade");
		//		}

		if (dx3 <= extent && dy3 <= extent && dz3 >= -extent) {
			// At least partially in 3
			return 3;
		}

		final float dx2 = Math.abs(centerX - x2) - r2;
		final float dy2 = Math.abs(centerY - y2) - r2;
		final float dz2 = (centerZ - z2) + r2;

		if (dx2 <= extent && dy2 <= extent && dz2 >= -extent) {
			return 2;
		}

		final float dx1 = Math.abs(centerX - x1) - r1;
		final float dy1 = Math.abs(centerY - y1) - r1;
		final float dz1 = (centerZ - z1) + r1;

		if (dx1 <= extent && dy1 <= extent && dz1 >= -extent) {
			return 1;
		}

		return 0;
	}

	public float maxRegionExtent() {
		return maxRegionExtent;
	}

	/**
	 * Does not ever fuzz, unlike perspective.
	 * {@inheritDoc}
	 */
	@Override
	public boolean isBoxVisible(int packedBox, int fuzz) {
		final int x0 = PackedBox.x0(packedBox);
		final int y0 = PackedBox.y0(packedBox);
		final int z0 = PackedBox.z0(packedBox);
		final int x1 = PackedBox.x1(packedBox);
		final int y1 = PackedBox.y1(packedBox);
		final int z1 = PackedBox.z1(packedBox);

		return clearTest.apply(x0, y0, z0, x1, y1, z1);
	}

	/** True if box is partially or fully occluded. */
	public boolean isBoxOccluded(int packedBox) {
		final int x0 = PackedBox.x0(packedBox);
		final int y0 = PackedBox.y0(packedBox);
		final int z0 = PackedBox.z0(packedBox);
		final int x1 = PackedBox.x1(packedBox);
		final int y1 = PackedBox.y1(packedBox);
		final int z1 = PackedBox.z1(packedBox);

		return occludedTest.apply(x0, y0, z0, x1, y1, z1);
	}

	@Override
	public void occludeBox(int packedBox) {
		final int x0 = PackedBox.x0(packedBox);
		final int y0 = PackedBox.y0(packedBox);
		final int z0 = PackedBox.z0(packedBox);
		final int x1 = PackedBox.x1(packedBox);
		final int y1 = PackedBox.y1(packedBox);
		final int z1 = PackedBox.z1(packedBox);

		draw.apply(x0, y0, z0, x1, y1, z1);
	}

	public void setLightVector(Vec3f skylightVector) {
		int outcome = 0;

		if (!MathHelper.approximatelyEquals(skylightVector.getX(), 0)) {
			outcome |= skylightVector.getX() > 0 ? EAST : WEST;
		}

		if (!MathHelper.approximatelyEquals(skylightVector.getZ(), 0)) {
			outcome |= skylightVector.getZ() > 0 ? SOUTH : NORTH;
		}

		if (!MathHelper.approximatelyEquals(skylightVector.getY(), 0)) {
			outcome |= skylightVector.getY() > 0 ? UP : DOWN;
		}

		clearTest = partiallyClearTests[outcome];
		occludedTest = partiallyOccludedTests[outcome];
		draw = boxDraws[outcome];
	}
}

