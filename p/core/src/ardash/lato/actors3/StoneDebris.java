/*******************************************************************************
 * Copyright (C) 2026 The Lato fork contributors
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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Pool.Poolable;

import ardash.gdx.scenes.scene3d.Group3D;
import ardash.gdx.scenes.scene3d.shape.Image3D;

/**
 * The remains of a rock that was smashed with a sonic boost:
 * a handful of shards (using the rock's own texture) that fly outwards,
 * tumble with gravity and shrink away. Pooled, one actor per smashed rock.
 */
public class StoneDebris extends Group3D implements Poolable {

	private static final int SHARD_COUNT = 6;
	private static final float LIFE_TIME = 0.75f;

	private static class Shard extends Image3D {
		float vx, vy, omega;
		Shard(float size, AtlasRegion region, ModelBuilder mb) {
			super(size, size, region, mb);
		}
	}

	private final Shard[] shards = new Shard[SHARD_COUNT];
	private float lifeTime;
	private boolean initialised = false;

	/** pool constructor */
	public StoneDebris() {
		setName("StoneDebris");
		setTag(Tag.CENTER);
	}

	/**
	 * (re)initialises the debris with the texture of the smashed stone
	 * @param region texture region of the stone that was smashed
	 * @param x centre of the smashed stone
	 * @param y centre of the smashed stone
	 */
	public void init(AtlasRegion region, float x, float y) {
		// the group sits on the stone centre, shards are positioned relative to it,
		// so the shrink scale applies around the smash point
		setPosition(x, y);
		setScaleX(1f);
		setScaleY(1f);
		final ModelBuilder mb = new ModelBuilder();
		for (int i = 0; i < SHARD_COUNT; i++) {
			final float size = MathUtils.random(0.35f, 0.85f);
			final Shard shard = new Shard(size, region, mb);
			shard.setPosition(MathUtils.random(-0.5f, 0.5f), MathUtils.random(-0.3f, 0.4f));
			shard.vx = MathUtils.random(-1.5f, 6.5f);
			shard.vy = MathUtils.random(3f, 8.5f);
			shard.omega = MathUtils.random(-540f, 540f);
			addActor(shard);
			shards[i] = shard;
		}
		lifeTime = 0f;
		initialised = true;
	}

	@Override
	public void act(float delta) {
		if (!initialised)
			return;
		lifeTime += delta;
		final float lifePercent = lifeTime / LIFE_TIME;
		if (lifePercent >= 1f) {
			// clean up: remove from stage and back into the pool
			this.remove();
			com.badlogic.gdx.utils.Pools.get(StoneDebris.class).free(this);
			return;
		}
		// shards fly, tumble and shrink
		for (Shard shard : shards) {
			if (shard == null)
				continue;
			shard.vy -= 22f*delta; // gravity for the shards
			shard.moveBy(shard.vx*delta, shard.vy*delta);
			shard.rotateBy(shard.omega*delta);
		}
		final float s = 1f - lifePercent*0.75f;
		setScaleX(s);
		setScaleY(s);
		super.act(delta);
	}

	@Override
	public void reset() {
		initialised = false;
		lifeTime = 0f;
		clearChildren();
		for (int i = 0; i < shards.length; i++) {
			shards[i] = null;
		}
	}
}
