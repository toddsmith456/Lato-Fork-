/*******************************************************************************
 * Copyright (C) 2020-2023 Andreas Redmer <ar-lato@abga.be>
 * Modifications Copyright (C) 2026 The Lato fork contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package ardash.lato.actors3;

import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.badlogic.gdx.utils.Pools;

import ardash.gdx.scenes.scene3d.shape.Image3D;
import ardash.lato.A;
import ardash.lato.A.SpriteGroupAsset;
import ardash.lato.actors.Performer;
import ardash.lato.actors.Performer.Demise;
import ardash.lato.actors.Performer.Pose;
import ardash.lato.terrain.CollidingTerrainItem;

public class Stone extends Image3D implements CollidingTerrainItem , Poolable{

	private boolean hasCollided;
	private Rectangle bb;
	private final AtlasRegion region;
	public Stone() {
		this(-1);
	}

	public Stone(int stoneIndex) {
		this(getTextureRegion(stoneIndex));
	}

	private Stone(AtlasRegion region) {
		super(region,getModelBuilder());
		this.region = region;
		setName("Stone");
		setTag(Tag.CENTER); // stones are always on center, not in background of foreground
		setScale(0.02f, 0.02f, 1);
		reset();
		this.bb = new Rectangle(getX(), getY(), 1, 1);
	}

	/** the texture this stone was built with (used for the smash debris) */
	public AtlasRegion getRegion() {
		return region;
	}

	@Override
	public void reset() {
		hasCollided = false;
	}

	/**
	 * -1 for random
	 */
	private static AtlasRegion getTextureRegion(int stoneIndex) {
		if (stoneIndex == -1) {
			return A.getRandomAtlasRegion(SpriteGroupAsset.STONE);
		}
		return A.getTextureRegions("stone").get(stoneIndex);
	}

	private static ModelBuilder getModelBuilder() {
		return new ModelBuilder(); // TODO Pool or reuse a static instance
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		CollidingTerrainItem.super.act(delta);
	}

	public void detectCollision() {
		if (hasCollided)
			return;

		// TODO update bb only when position changes (and in init())
		bb.setPosition(getX(), getY());
		if (getGameScreen().performer.bb.overlaps(bb)) {
			onCollision();
		}
	}

	public void onCollision() {
		final Performer performer = getGameScreen().performer;

		// Alto-style rock smash: while the force field from a trick is up,
		// the rock shatters instead of ending the run
		if (!performer.getState().isCrashed() && performer.isBoosting()) {
			if (performer.smashRock(this)) {
				hasCollided = true;
				remove();
				Pools.get(Stone.class).free(this);
				return;
			}
		}

		if (performer.getState().isInAir()) {
			performer.setCauseOfDeath(Demise.LAND_ON_STONE);
		} else {
			performer.setCauseOfDeath(Demise.HIT_STONE);
		}
		performer.crash(Pose.CRASH_NOSE);
		hasCollided = true;
	}
}
