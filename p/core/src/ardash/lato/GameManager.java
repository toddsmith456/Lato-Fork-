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

package ardash.lato;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.PerformanceCounters;

import ardash.lato.A.SoundAsset;
import ardash.lato.screens.GameScreen;
import ardash.lato.terrain.TerrainManager;
import ardash.lato.utils.SoundPlayer;
import ardash.lato.weather.EnvColors;
import ardash.lato.weather.SODChangeListener;

public class GameManager implements SODChangeListener {

	public static final boolean DEBUG_VIEW = false;
	public static final boolean DEBUG_GUI = false;
	public static final boolean DEBUG_RUNTIME_VALIDATION = false;
	public static final boolean DEBUG_WEATHER_FASTMODE = false;
//	public static final boolean DEBUG_WEATHER_FASTMODE = true;
	public static final boolean DEBUG_ZOOM_OUT_TO_MAX_SPEED = false;
	public static final boolean DEBUG_PRINT_PERFORMANCE_STATS = false;
	public static final boolean DEBUG_PRINT_POOL_STATS = false;

	public final LatoGame game;
	public TerrainManager tm;
	public PerformanceCounters performanceCounters = new PerformanceCounters();

	/**
	 * Indicates if forward movement is going on. User must tap initially to start and movement will end after crash.
	 */
	private boolean started;
	private float lastHourOfDay = -1;
	private EnvColors lastKnownColorScheme = EnvColors.DAY;
	private int coinsPickedUpThisRound;

	// ---- trick & score state (Alto's Adventure style) -------------------------

	private static final String PREFS_NAME = "ardash.lato.stats";
	private static final String PREF_BEST_DISTANCE = "bestDistance";
	private static final String PREF_BEST_SCORE = "bestScore";
	private static final String PREF_TOTAL_COINS = "totalCoins";
	private static final String PREF_TOTAL_RUNS = "totalRuns";
	/** coins are worth this many trick points in the total score */
	private static final int COIN_POINTS = 5;

	private int trickScoreThisRound;
	private int flipsLandedThisRound;
	private int rocksSmashedThisRound;
	private int chasmsJumpedThisRound;
	private int bestComboThisRound;

	/** texts of score popups ('Backflip!', 'Rock Smash! +50') that the GUI displays */
	private final Array<String> pendingPopups = new Array<String>();

	private Preferences prefs;
	private boolean newBestDistanceThisRound = false;
	private boolean newBestScoreThisRound = false;

	public GameManager(LatoGame game) {
		this.game = game;
		this.tm = new TerrainManager();
		reset();
	}

	public void reset() {
		this.tm = new TerrainManager();
		started = false;
		coinsPickedUpThisRound = 0;
		trickScoreThisRound = 0;
		flipsLandedThisRound = 0;
		rocksSmashedThisRound = 0;
		chasmsJumpedThisRound = 0;
		bestComboThisRound = 0;
		newBestDistanceThisRound = false;
		newBestScoreThisRound = false;
		pendingPopups.clear();
		System.gc();
	}

	public Screen getScreen() {
		return game.getScreen();
	}

	public GameScreen getGameScreen() {
		return (GameScreen)game.getScreen();
	}

	public void setStarted(boolean started) {
		this.started = started;
	}

	private boolean isStarted()
	{
		return started;
	}

	@Override
	public void onSODChange(float newSOD, float hourOfDay, float delta, float percentOfDayOver, EnvColors currentColorSchema) {
		this.lastHourOfDay = hourOfDay;
		this.lastKnownColorScheme = currentColorSchema;
	}

	public float getLastHourOfDay() {
		return lastHourOfDay;
	}

	public void pickUpCoin() {
		SoundPlayer.playSound(A.getSound(SoundAsset.COINDROP));
		coinsPickedUpThisRound++;
	}

	public int getCoinsPickedUpThisRound() {
		return coinsPickedUpThisRound;
	}

	public EnvColors getLastKnownColorScheme() {
		return lastKnownColorScheme;
	}

	// ---- tricks, combos & score ------------------------------------------------

	/**
	 * player landed a trick (e.g. backflips). The points are already multiplied by the combo.
	 * @param trickName e.g. 'Backflip'
	 * @param proximity true if it was a proximity trick (close to the ground)
	 * @param points total points awarded (base points x combo multiplier)
	 * @param comboLength number of tricks chained in this combo
	 */
	public void onTrickLanded(String trickName, boolean proximity, int points, int comboLength) {
		trickScoreThisRound += points;
		flipsLandedThisRound++;
		if (comboLength > bestComboThisRound)
			bestComboThisRound = comboLength;

		String popupText = trickName;
		if (proximity)
			popupText += " Proximity!";
		popupText += " +" + points;
		if (comboLength > 1)
			popupText += " (x" + comboLength + " Combo)";
		addTrickPopup(popupText);
	}

	public void onRockSmashed(int points) {
		trickScoreThisRound += points;
		rocksSmashedThisRound++;
		addTrickPopup("Rock Smash! +" + points);
	}

	public void onChasmJumped(int points) {
		trickScoreThisRound += points;
		chasmsJumpedThisRound++;
		addTrickPopup("Chasm Jump! +" + points);
	}

	/** queue a popup that the GUI will float above the scene */
	public void addTrickPopup(String text) {
		pendingPopups.add(text);
	}

	public Array<String> getPendingPopups() {
		return pendingPopups;
	}

	public int getTrickScoreThisRound() {
		return trickScoreThisRound;
	}

	public int getFlipsLandedThisRound() {
		return flipsLandedThisRound;
	}

	public int getRocksSmashedThisRound() {
		return rocksSmashedThisRound;
	}

	public int getChasmsJumpedThisRound() {
		return chasmsJumpedThisRound;
	}

	public int getBestComboThisRound() {
		return bestComboThisRound;
	}

	/**
	 * the total score of the current run, Alto-style:
	 * trick points + coins + 1 point per travelled meter
	 */
	public int getTotalScoreThisRound(int distanceMeters) {
		return trickScoreThisRound + coinsPickedUpThisRound*COIN_POINTS + distanceMeters;
	}

	// ---- persistent records -----------------------------------------------------

	private Preferences getPrefs() {
		if (prefs == null)
			prefs = Gdx.app.getPreferences(PREFS_NAME);
		return prefs;
	}

	public int getBestDistanceMeters() {
		return getPrefs().getInteger(PREF_BEST_DISTANCE, 0);
	}

	public int getBestTotalScore() {
		return getPrefs().getInteger(PREF_BEST_SCORE, 0);
	}

	public int getTotalCoinsLifetime() {
		return getPrefs().getInteger(PREF_TOTAL_COINS, 0);
	}

	public boolean isNewBestDistanceThisRound() {
		return newBestDistanceThisRound;
	}

	public boolean isNewBestScoreThisRound() {
		return newBestScoreThisRound;
	}

	/**
	 * called once at the end of a run: updates the persistent records
	 * @return true if a new best distance or score was reached
	 */
	public boolean submitRunResults(int distanceMeters) {
		final int totalScore = getTotalScoreThisRound(distanceMeters);
		final Preferences p = getPrefs();

		newBestDistanceThisRound = distanceMeters > p.getInteger(PREF_BEST_DISTANCE, 0) && distanceMeters > 0;
		if (newBestDistanceThisRound)
			p.putInteger(PREF_BEST_DISTANCE, distanceMeters);

		newBestScoreThisRound = totalScore > p.getInteger(PREF_BEST_SCORE, 0) && totalScore > 0;
		if (newBestScoreThisRound)
			p.putInteger(PREF_BEST_SCORE, totalScore);

		p.putInteger(PREF_TOTAL_COINS, p.getInteger(PREF_TOTAL_COINS, 0) + coinsPickedUpThisRound);
		p.putInteger(PREF_TOTAL_RUNS, p.getInteger(PREF_TOTAL_RUNS, 0) + 1);
		p.flush();
		return newBestDistanceThisRound || newBestScoreThisRound;
	}
}
