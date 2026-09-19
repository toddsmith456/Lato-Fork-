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

package ardash.lato.screens;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

import ardash.gdx.scenes.scene2d.ui.AdvancedDialog;
import ardash.lato.A;
import ardash.lato.GameManager;
import ardash.lato.actors.StageAccessor;

public class GameOverDialog extends AdvancedDialog implements StageAccessor{

	public GameOverDialog(String causeOfDeath, int distance) {
		super();
		setTouchable(Touchable.disabled);

		final GameManager gm = getGameManager();
		// commit the run to the persistent records
		gm.submitRunResults(distance);

		Label lblGameOver = new Label("Game Over", A.LabelStyleAsset.DISTANCE_LABEL.style);
		text(lblGameOver);
		getContentTable().row();
		text(new Label(causeOfDeath, A.LabelStyleAsset.SMALL_TEXT.style));
		getContentTable().row();
		text(new Label("You travelled "+distance+" meters", A.LabelStyleAsset.SMALL_TEXT.style));
		getContentTable().row();
		text(new Label("You collected "+gm.getCoinsPickedUpThisRound()+" coins", A.LabelStyleAsset.SMALL_TEXT.style));
		getContentTable().row();

		// Alto-style run summary: trick score and total score
		text(new Label("Trick score: "+gm.getTrickScoreThisRound(), A.LabelStyleAsset.SMALL_TEXT.style));
		getContentTable().row();

		if (gm.getFlipsLandedThisRound() > 0 || gm.getRocksSmashedThisRound() > 0) {
			final String trickSummary = "Backflips: "+gm.getFlipsLandedThisRound()
					+"   Rocks smashed: "+gm.getRocksSmashedThisRound()
					+"   Best combo: x"+gm.getBestComboThisRound();
			text(new Label(trickSummary, A.LabelStyleAsset.SMALL_TEXT.style));
			getContentTable().row();
		}

		text(new Label("Total score: "+gm.getTotalScoreThisRound(distance), A.LabelStyleAsset.DISTANCE_LABEL.style));
		getContentTable().row();

		if (gm.isNewBestDistanceThisRound() || gm.isNewBestScoreThisRound()) {
			final String newBest = gm.isNewBestDistanceThisRound() && gm.isNewBestScoreThisRound()
					? "NEW BEST DISTANCE & SCORE!"
					: gm.isNewBestDistanceThisRound() ? "NEW BEST DISTANCE!" : "NEW BEST SCORE!";
			text(new Label(newBest, A.LabelStyleAsset.DISTANCE_LABEL.style));
			getContentTable().row();
		} else {
			text(new Label("Best: "+gm.getBestDistanceMeters()+"m  |  "+gm.getBestTotalScore()+" points", A.LabelStyleAsset.SMALL_TEXT.style));
			getContentTable().row();
		}

		text(new Label("Touch the screen to restart", A.LabelStyleAsset.SMALL_TEXT.style));
	}
}
