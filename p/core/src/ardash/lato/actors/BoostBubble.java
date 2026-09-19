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

package ardash.lato.actors;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;

import ardash.gdx.scenes.scene3d.shape.Image3D;
import ardash.lato.A;
import ardash.lato.A.ARAsset;

/**
 * The glowing force field around the rider while a sonic boost is active
 * (the Alto-style 'smash through rocks' shield). It uses the soft glow sprite
 * with additive blending, pulses gently and follows the rider as a child actor.
 */
public class BoostBubble extends Image3D {

	private static final float SIZE = 3.1f;
	private float time = 0f;

	public BoostBubble() {
		super(SIZE, SIZE, A.getTextureRegion(ARAsset.GLOW), new ModelBuilder(),
				new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 1f));
		setName("BoostBubble");
		setTag(Tag.CENTER);
		// icy-blue force field colour
		setColor(new com.badlogic.gdx.graphics.Color(0.55f, 0.85f, 1f, 0.5f));
		setVisible(false);
	}

	/** positions the bubble so that its centre sits at the given local point */
	public void centerOn(float localX, float localY) {
		setPosition(localX - SIZE/2f, localY - SIZE/2f);
	}

	@Override
	public void act(float delta) {
		super.act(delta);
		if (!isVisible())
			return;
		time += delta;
		// gentle breathing of the field
		final float pulse = 0.42f + 0.18f*MathUtils.sin(time*11f) + 0.06f*MathUtils.sin(time*29f);
		getColor().a = MathUtils.clamp(pulse, 0.25f, 0.7f);
		final float s = 1f + 0.05f*MathUtils.sin(time*9f);
		setScaleX(s);
		setScaleY(s);
	}
}
