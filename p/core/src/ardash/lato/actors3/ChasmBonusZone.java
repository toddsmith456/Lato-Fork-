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
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Pool.Poolable;

import ardash.gdx.scenes.scene3d.shape.Image3D;
import ardash.lato.A;
import ardash.lato.A.ARAsset;
import ardash.lato.actors.Performer;

/**
 * An invisible trigger zone at the far edge of a canyon. When the rider passes
 * it alive, the 'Chasm Jump!' bonus (Alto's Adventure) is awarded.
 */
public class ChasmBonusZone extends Image3D implements TerrainItem , Poolable{

	public static final int CHASM_JUMP_POINTS = 50;

	private boolean hasCollided;

	public ChasmBonusZone(float x, float y, float width, float height) {
		super(width,height, getTextureRegion(),getModelBuilder());
		setName("ChasmBonusZone");
		setTag(Tag.MEGAFRONT);
		setPosition(x, y, 5f);
		reset();
		setVisible(false);
	}

	@Override
	public void draw(ModelBatch modelBatch, Environment environment) {
		// invisible
	}

	@Override
	public void reset() {
		hasCollided = false;
	}

	private static AtlasRegion getTextureRegion() {
		return A.getTextureRegion(ARAsset.FOG_PIX);
	}

	private static ModelBuilder getModelBuilder() {
		return new ModelBuilder();
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		detectCollision();
	}

	private void detectCollision() {
		if (hasCollided)
			return;

		final Performer performer = getGameScreen().performer;

		// only a surviving rider can score
		if (!performer.getState().isStarted() || performer.getState().isCrashed())
			return;

		final float pX = performer.getX();
		final float pY = performer.getY();

		// rider must be within the zone rectangle
		if (pX < getX() || pX > getX()+getWidth())
			return;
		if (pY < getY() || pY > getY()+getHeight())
			return;
		// and must still be above the chasm lip: a rider who dropped into the pit
		// passes through this zone too, but falling in is not a chasm jump
		if (pY < getY() + 1.0f)
			return;

		hasCollided = true;
		getGameManager().onChasmJumped(CHASM_JUMP_POINTS);
	}
}
