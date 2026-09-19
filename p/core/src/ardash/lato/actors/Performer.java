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

package ardash.lato.actors;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Pools;
import com.bitfire.postprocessing.effects.Zoomer;
import com.bitfire.postprocessing.filters.RadialBlur.Quality;

import ardash.gdx.graphics.g3d.ParticleEmitter;
import ardash.gdx.graphics.g3d.ParticleEmitter.ParticleEmitterType;
import ardash.gdx.scenes.scene3d.Actor3D;
import ardash.gdx.scenes.scene3d.Camera3D;
import ardash.gdx.scenes.scene3d.Group3D;
import ardash.gdx.scenes.scene3d.actions.Actions3D;
import ardash.gdx.scenes.scene3d.shape.Image3D;
import ardash.lato.A;
import ardash.lato.A.ARAsset;
import ardash.lato.A.MusicAsset;
import ardash.lato.A.SoundAsset;
import ardash.lato.actors3.Stone;
import ardash.lato.actors3.StoneDebris;
import ardash.lato.screens.GameOverDialog;
import ardash.lato.utils.SoundPlayer;
import ardash.lato.weather.AmbientColorChangeListener;

/**
 * The rider. Drives the whole Alto-like gameplay loop:
 * <ul>
 * <li>hold the screen in the air to backflip (flips accumulate, Alto-style point scale)</li>
 * <li>landed tricks grant a <b>sonic boost</b> (speed burst + force field)</li>
 * <li>while the force field is up, rocks are smashed instead of ending the run,
 *     and each smashed rock extends the boost</li>
 * <li>proximity backflips (close to the ground) are worth bonus points</li>
 * </ul>
 * Movement is fully frame-rate independent; rotation pivot is animated so
 * ground moves rotate around the board and air tricks rotate around the body
 * centre, which makes flipping look far smoother than the original fork base.
 */
public class Performer extends Group3D implements Disposable, AmbientColorChangeListener {

	public enum Pose {
		RIDE, DUCK, JUMP, CRASH_ASS, CRASH_NOSE//, ROLL, FLY, CRASHED, GRIND
	}

	public enum Demise {
		NONE, LAND_ON_ASS, LAND_ON_NOSE, LAND_ON_STONE, HIT_STONE, DROP_IN_CANYON;
		@Override
		public String toString() {
			switch (this) {
			case HIT_STONE:
				return "You hit a rock";
			case LAND_ON_ASS:
				return "You landed on your ass";
			case LAND_ON_NOSE:
				return "You landed on your nose";
			case LAND_ON_STONE:
				return "You landed on a rock";
			case DROP_IN_CANYON:
				return "You dropped into a canyon";
			case NONE:
				return "";
			default:
				return super.toString();
			}
		}
	}

	// ---- movement tuning -----------------------------------------------------
	private static final float PERFORMER_WIDTH = 1.85f;
	public static final float MIN_SPEED = 9.3f;
	private static final float MAX_SPEED = 29.3f;        // regular top riding speed (m/s)
	private static final float BOOST_MAX_SPEED = 38.5f;  // top speed while sonic-boosted
	private static final float MIN_CAM_SPOT_X = 10f;
	private static final float MAX_CAM_SPOT_X = 26f;
	private static final float JUMP_FORCE = 3.85f;       // vertical take-off speed (m/s)
	private static final float JUMP_SPEED_BONUS = 0.075f;// extra jump force per m/s of speed
	private static final float GRAVITY = 11.5f;          // slightly floaty, for trick airtime
	private static final float GROUND_ACCEL = 5.1f;      // downhill acceleration
	private static final float AIR_DRAG = 1.1f;

	// ---- trick tuning --------------------------------------------------------
	private static final float FLIP_START_SPEED = 340f;  // deg/s at the beginning of a flip
	private static final float FLIP_MAX_SPEED = 560f;    // deg/s after ramp-up
	private static final float FLIP_RAMP_RATE = 6f;      // how fast the flip accelerates (1/s)
	private static final float AIR_ALIGN_RATE = 150f;    // deg/s the board seeks the slope in free air
	private static final float GROUND_ALIGN_RATE = 11f;  // exponential rate the board seeks the slope on ground
	private static final float SAFE_LANDING_ANGLE = 62f; // max angle difference to the slope that still lands
	private static final float PERFECT_LANDING_ANGLE = 12f;
	private static final float JUMP_BUFFER_TIME = 0.13f; // touching just before landing = instant re-jump
	/** Alto's Adventure point scale per completed flip in one air */
	private static final int[] FLIP_POINTS = {0, 10, 60, 200, 500};
	private static final String[] FLIP_NAMES = {"", "Backflip", "Double Backflip", "Triple Backflip", "Quadruple Backflip"};
	private static final int PROXIMITY_BONUS_POINTS = 300;
	private static final int ROCK_SMASH_POINTS = 50;
	private static final int CHASM_JUMP_POINTS = 50;
	/** while (partially) inverted, being lower than this above the slope counts as 'proximity' */
	private static final float PROXIMITY_HEIGHT = 1.45f;

	// ---- boost tuning --------------------------------------------------------
	private static final float BOOST_MAX_TIME = 5f;      // seconds of boost that can be banked
	private static final float BOOST_GROUND_ACCEL = 6.5f;// m/s gained per second while boosted (ground)
	private static final float BOOST_AIR_ACCEL = 3.2f;   // m/s gained per second while boosted (air)
	private static final float BOOST_ROCK_EXTEND = 0.85f;// extra seconds of boost per smashed rock
	private static final float SPEED_DECAY = 2.3f;       // m/s lost per second above MAX_SPEED when not boosted

	private float speed = 0f; // speed in m/s
	private float runtime = 0f; // lifetime starting after game started
	private boolean isUserInputDown = false;
	private Vector2 velocity = new Vector2(); // this is only here to safe new-calls
	protected ParticleEmitter spray = new ParticleEmitter(ParticleEmitterType.SNOW);
	private Image3D ambientColorContainer = new Image3D(1, 1, new Color(), new ModelBuilder());
	private List<PerformerListener> listeners = new ArrayList<Performer.PerformerListener>();
	private Map<Pose,Image3D> poses = new EnumMap<Pose, Image3D>(Pose.class);
	protected Pose pose = Pose.RIDE;
	public PlayerState state = PlayerState.INIT;
	private Demise causeOfDeath = Demise.NONE;

	/** the visible force field while a boost is active */
	private final BoostBubble boostBubble = new BoostBubble();

	private final Group3D scarfAttachPointGroup = new Group3D();
	private final Vector2 scarfAttachPoint = new Vector2(0,0);

	/** vertical speed, handled separately because ground movement is intentionally arcadish */
	private float vspeed = 0f; // speed in m/s

	// ---- trick state (reset every time the rider leaves the ground) -----------
	private float flipAccumDeg = 0f;       // degrees rotated while holding, this air
	private int flipsAnnounced = 0;        // full flips already counted this air
	private float flipSpeed = FLIP_START_SPEED;
	private float minClearanceDuringFlip = Float.MAX_VALUE;
	private boolean proximityCandidate = false;

	// ---- boost state ----------------------------------------------------------
	private float boostTimeLeft = 0f;

	// ---- cosmetic animation state ---------------------------------------------
	private float squashAmount = 0f;       // <0 squashed (landing), >0 stretched (jump)
	private float duckHoldTimer = 0f;      // how long the DUCK pose is held after landing
	private float pivotLerp = 0f;          // 0 = pivot on the board, 1 = pivot at body centre

	private float jumpBufferTimer = 0f;    // grace jump after touching just before touchdown

	/**
	 * A spot in front of the actor, where he wants the camera to look at. Usually a few meters in front of the actor.
	 */
	private Vector2 camSpot = new Vector2();
	private float timeInState;
	private float startedAtX = -1;
	public Rectangle bb;

	public interface PerformerListener{
		void onPositionChange(float newX, float newY);
		void onSpeedChanged(float newSpeed, float percentage);
	}

	public Performer() {
		ModelBuilder mb = new ModelBuilder();
		setName("Performer");
		setTag(Tag.CENTER);
		for (Pose pose : Pose.values()) {
			String performer = "P1";
			final String posename = performer+"_"+pose.name().toUpperCase();
			final AtlasRegion poseTextureRegion = A.getTextureRegion(ARAsset.valueOf(posename));
			Image3D img = new Image3D(PERFORMER_WIDTH,PERFORMER_WIDTH,poseTextureRegion,mb);
			img.setName(posename);
			addActor(img);
			poses.put(pose, img);
		}
		// the force field is part of the rider group so it follows him everywhere
		addActor(boostBubble);
		boostBubble.centerOn(PERFORMER_WIDTH/2f, PERFORMER_WIDTH/2f);

		setPose(Pose.CRASH_ASS);
		// pivot at board-centre by default (ground riding)
		setOriginX(PERFORMER_WIDTH/2f);
		setOriginY(0f);
		camSpot.set(getX(), getY()+10f);
		setSpeed(MIN_SPEED);

		addActor(ambientColorContainer);
		ambientColorContainer.setVisible(false);

		scarfAttachPointGroup.setPosition(0f, 0f);

		this.bb = new Rectangle(getX(), getY(), PERFORMER_WIDTH*0.9f, PERFORMER_WIDTH*0.9f);
	}

	public void act(float delta) {
		super.act(delta);
		if (state.isStarted())
		{
			runtime += delta;
			timeInState += delta;
		}

		if (state == PlayerState.DROPPED) {
			return;
		}

		// decay the jump buffer regardless of state (it is a short grace window)
		jumpBufferTimer = Math.max(0f, jumpBufferTimer - delta);
		updateCosmetics(delta);

		// rotation for the forward movement (ignored when in air)
		final float rotation = getRotation() < 0f ? getRotation() + 360f : getRotation();

		if (state.isInAir()) {
			actAir(delta);
		} else {
			actGround(delta, rotation);
		}

		// boost bookkeeping: the bubble and the camera react to the current boost strength
		if (boostTimeLeft > 0f && !state.isCrashed()) {
			boostTimeLeft = Math.max(0f, boostTimeLeft - delta);
			if (boostTimeLeft <= 0f) {
				boostTimeLeft = 0f;
			}
		}
		boostBubble.setVisible(isBoosting());
		speed = MathUtils.clamp(speed, state.isCrashed() ? 0f : (state.isStarted() ? MIN_SPEED : 0f), BOOST_MAX_SPEED);

		if (! state.isCrashed()) {
			float newCamSpotX= MathUtils.lerp(MIN_CAM_SPOT_X, MAX_CAM_SPOT_X, getSpeedPercentage());
			newCamSpotX = MathUtils.clamp(newCamSpotX, MIN_CAM_SPOT_X, MAX_CAM_SPOT_X);
			final Vector2 newCamSpot = new Vector2(getX() + newCamSpotX, getY());
			if (getSpeed() == 0)
			{
				newCamSpot.y +=5f; // initially when standing, move cam above
			}

			// before applying the new camspot, check if the difference is too big and go there smoothly
			final Vector2 diff = newCamSpot.cpy().sub(camSpot);
			diff.clamp(0, getMaxCamSpeed());
			camSpot.add(diff);

		}

		// inform listeners about new position
		for (PerformerListener listener : listeners) {
			listener.onPositionChange(getX(), getY());

			if (!spray.hasParent())
				getStage().addActor(spray);
			spray.setPosition(getX(), getY());
			bb.setPosition(getX(), getY());
		}

	}

	/** ground physics: follow the terrain, smooth-align the board to the slope */
	private void actGround(float delta, final float rotation) {
		// apply the speed into a direction of movement, which is the direction of the terrain
		velocity.set(1,1).setLength(speed).setAngle(rotation);
		final float deltaX = velocity.x;
		moveBy(deltaX*delta, 0);

		// pivot eases back to the board while grounded
		pivotLerp = Math.max(0f, pivotLerp - delta*4f);
		setOriginY(MathUtils.lerp(0f, PERFORMER_WIDTH/2f, pivotLerp));

		// restore the RIDE pose shortly after a landing
		if (duckHoldTimer > 0f) {
			duckHoldTimer -= delta;
			if (duckHoldTimer <= 0f && (state == PlayerState.DUCKING || state == PlayerState.SLIDING)) {
				setPose(Pose.RIDE);
				if (state == PlayerState.DUCKING)
					setState(PlayerState.SLIDING);
			}
		}

		// set the height of the terrain under the actor
		float heightUnderActor = getGameScreen().waveDrawer.getHeightAt(getX()+(PERFORMER_WIDTH/2f));

		// check if the offset is very high, if the actor would suddenly fall, make him 0-jump : airborne + gravity
		final float heightDelta = getY() - heightUnderActor;

		if ((heightDelta >=-1.729992 && heightDelta < 1.729992f) || ! state.isStarted()) {
			// all good, just put him on the ground
			setPosition(getX(), heightUnderActor);
			// smoothly align the board with the terrain instead of snapping to it
			final float slope = getGameScreen().waveDrawer.getAngleAtX(getX()+(PERFORMER_WIDTH/2f));

			// apply only reasonable values 275-360 and 0-85
			if ((slope > 0 && slope < 89) || (slope > 271 && slope < 360))  {
				final float arc = shortestArcDeg(getRotation(), slope);
				setRotation(getRotation() + arc * Math.min(1f, GROUND_ALIGN_RATE*delta));
			}
		} else if (heightDelta < 0) {
			// Terrain goes suddenly up (inside canyon) - we don't put walls like this on the terrain
			// this cannot happen anymore with the AbyssCollider
		} else {
			// Terrain goes suddenly down (canyon, ramp): become airborne
			startAir(0f);
		}

		// accelerate on ground
		final float angleToGround = 360f - velocity.angle(); // 0 or 360 is horizontal, 90 is downward

		if (! state.isCrashed()) {

			if (isBoosting()) {
				// sonic boost: pull the speed up to the boosted maximum
				setSpeed(Math.min(BOOST_MAX_SPEED, speed + BOOST_GROUND_ACCEL*delta));
				spray.startEmitting(); // boosted riding throws extra snow
			} else {
				if (angleToGround > 0)
				{
					if (angleToGround < 8f)
					{
						setSpeed(speed-(1.1f*delta));
					}
					else if (angleToGround < 90f)
					{
						// Alto-feel: every real downslope pushes you; gentle slopes push gently,
						// from 20 degrees on it is the classic full acceleration
						final float slopeScale = MathUtils.clamp((angleToGround - 8f) / 12f, 0f, 1f);
						setSpeed(speed + (GROUND_ACCEL*slopeScale*delta));
					}
					else
					{
						setSpeed(speed-(1.1f*delta));
					}
				}
				// ease back to the regular top speed after a boost expired
				if (speed > MAX_SPEED)
					setSpeed(Math.max(MAX_SPEED, speed - SPEED_DECAY*delta));
			}

			// buffered jump: a fresh touch on the ground lifts off immediately
			if (jumpBufferTimer > 0f) {
				jumpBufferTimer = 0f;
				jump(JUMP_FORCE);
			}
		} else {
			// if crashed: break hard
			setSpeed(speed-(15.1f*delta));
		}
	}

	/** air physics: manual gravity integration, flips, trick bookkeeping */
	private void actAir(float delta) {
		// forward movement at constant-ish speed
		moveBy(speed*delta, 0);

		// gravity
		vspeed -= GRAVITY*delta;
		moveBy(0, vspeed*delta);

		// pivot eases to the body centre so flips rotate around the rider
		pivotLerp = Math.min(1f, pivotLerp + delta*5f);
		setOriginY(MathUtils.lerp(0f, PERFORMER_WIDTH/2f, pivotLerp));

		// boost also works in the air (but weaker than on the ground)
		if (isBoosting()) {
			setSpeed(Math.min(BOOST_MAX_SPEED, speed + BOOST_AIR_ACCEL*delta));
			spray.startEmitting();
		} else {
			// linear damping in air, otherwise the player floats too far forward
			setSpeed(speed-(AIR_DRAG*delta));
		}

		// rotation of the rider
		if (isUserInputDown && !state.isCrashed())
		{
			// holding the screen = backflip, speeding up the longer it is held (Alto-style tuck)
			setPose(Pose.RIDE);
			flipSpeed += (FLIP_MAX_SPEED - flipSpeed) * Math.min(1f, FLIP_RAMP_RATE*delta);
			final float deltaDeg = flipSpeed*delta;
			rotateBy(deltaDeg);
			flipAccumDeg += deltaDeg;

			// count newly completed flips and celebrate them mid-air
			final int completedFlips = (int)Math.floor(flipAccumDeg / 360f);
			if (completedFlips > flipsAnnounced) {
				flipsAnnounced = completedFlips;
				final String name = FLIP_NAMES[Math.min(completedFlips, 4)];
				getGameManager().addTrickPopup(name + "!");
				SoundPlayer.playSound(A.getSound(SoundAsset.WHOOSH), 0.6f);
			}
		}
		else
		{
			// free air: the board gently seeks the slope below, so landings line up smoothly
			setPose(Pose.JUMP);
			final float slope = getGameScreen().waveDrawer.getAngleAtX(getX()+(PERFORMER_WIDTH/2f));
			float arc = shortestArcDeg(getRotation(), slope);
			arc = MathUtils.clamp(arc, -AIR_ALIGN_RATE*delta, AIR_ALIGN_RATE*delta);
			setRotation(getRotation() + arc);
		}

		// trick bookkeeping: distance to the slope below (for proximity bonuses)
		final float groundBelow = getGameScreen().waveDrawer.getHeightAt(getX()+(PERFORMER_WIDTH/2f));
		final float clearance = getY() - groundBelow;
		final float rotInFlip = flipAccumDeg % 360f;
		if (flipAccumDeg > 30f && (rotInFlip > 60f && rotInFlip < 300f || flipAccumDeg >= 330f)) {
			// rider is (or was, at least once this air) substantially rotated:
			// measure the closest approach to the ground to detect proximity flips
			if (clearance < minClearanceDuringFlip)
				minClearanceDuringFlip = clearance;
			if (clearance < PROXIMITY_HEIGHT)
				proximityCandidate = true;
		}

		// landing check (only when falling)
		final float heightUnderActor = getGameScreen().waveDrawer.getHeightAt(getX()+(PERFORMER_WIDTH/2f));
		if (vspeed < 0f && getY() < heightUnderActor)
		{
			// land only if in air since longer time
			if (timeInState >= 0.1f)
			{
				land();
			}
		}
	}

	/** cosmetic animation: squash & stretch, makes landings and jump-offs look alive */
	private void updateCosmetics(float delta) {
		squashAmount += (0f - squashAmount) * Math.min(1f, delta*9f);
		setScaleY(1f + squashAmount);
		setScaleX(1f - squashAmount*0.55f);
	}

	private float getMaxCamSpeed() {
		return state.isStarted() ? (runtime > 1f ? 3.3f : 03.3f) : 0.03f;
	}

	public void setPose(Pose pose) {
		this.pose = pose;
		for (Actor3D a : getChildren()) {
			a.setVisible(false);
		}
		poses.get(pose).setVisible(true);
		boostBubble.setVisible(isBoosting()); // setPose hides everything; restore the bubble
	}

	public Pose getPose() {
		return pose;
	}

	public float getSpeed() {
		return speed;
	}

	public void setSpeed(float speed) {
		if (!state.isStarted())
			return;

		if (speed <=0f && state.isCrashed()) {
			this.speed = 0f;
			return;
		}

		if (speed < MIN_SPEED && !state.isCrashed())
			speed = MIN_SPEED;
		if (speed > BOOST_MAX_SPEED)
			speed = BOOST_MAX_SPEED;
		this.speed = speed;

		// inform listeners
		for (PerformerListener l : listeners) {
			l.onSpeedChanged(speed, getSpeedPercentage());
		}
	}

	public float getSpeedPercentage() {
		// percentage in the full possible speed range (also used for camera zoom)
		final float max = BOOST_MAX_SPEED - MIN_SPEED;
		final float cur = speed - MIN_SPEED;

		if (cur <=0)
			return 0f;

		return MathUtils.clamp(cur/max, 0f, 1f);
	}

	public Vector2 getCamSpot() {
		return camSpot;
	}

	@Override
	public void onAmbientColorChangeTriggered(Color target, float seconds) {
	}

	/**
	 * handle the only possible user input (on the game stage): touch anywhere on the screen
	 * @param touchDown touch up or touch down
	 */
	public void userInput(boolean touchDown) {
		if (!state.isStarted())
		{
			startedAtX  = getX();
			setState(PlayerState.SLIDING);
			setPose(Pose.RIDE);
			return; // don't jump or rotate if game not started yet
		}

		isUserInputDown = touchDown;
		if (touchDown && !state.isCrashed()) {
			// buffered input: a touch fractions of a second before landing still jumps
			jumpBufferTimer = JUMP_BUFFER_TIME;
		}
	}

	@Override
	public float getRotation() {
		float rotation = super.getRotation();
		rotation %= 360f;
		if (rotation <0f)
			rotation = 360 + rotation;
		return rotation;
	}

	public void jump(float jumpforce) {
		startAir(jumpforce);
		if (jumpforce > 0f) {
			// stretch a little when lifting off (cosmetic)
			squashAmount = 0.09f;
			SoundPlayer.playSound(A.getSound(SoundAsset.WHOOSH), 0.25f);
		}
	}

	/** enter the airborne state with an initial upwards speed */
	private void startAir(float initialVSpeed) {
		if (state == PlayerState.INIT)
			state = PlayerState.SLIDING; // moveTo would refuse INIT->INAIR on the very first frame
		clearActions();
		setState(PlayerState.INAIR);
		// faster riding = higher jumps (Alto ramps/kickers feel), capped for sanity
		this.vspeed = initialVSpeed <= 0f ? 0f : initialVSpeed + (getSpeed()-MIN_SPEED)*JUMP_SPEED_BONUS;
		flipAccumDeg = 0f;
		flipsAnnounced = 0;
		flipSpeed = FLIP_START_SPEED;
		minClearanceDuringFlip = Float.MAX_VALUE;
		proximityCandidate = false;
		setPose(Pose.JUMP);
	}

	/** touching down after a jump or fall */
	private void land() {
		clearActions();
		final float impactSpeed = -vspeed; // positive when falling fast
		vspeed = 0f;
		final float groundAngle = getGameScreen().waveDrawer.getAngleAtX(getX()+(PERFORMER_WIDTH/2f));
		final float diff = shortestArcDeg(getRotation(), groundAngle);
		final float absDiff = Math.abs(diff);

		if (absDiff > SAFE_LANDING_ANGLE) {
			// landed too rotated: crash (front or back first, depending on the rotation direction)
			if (diff > 0) {
				crash(Pose.CRASH_ASS);
				setCauseOfDeath(Demise.LAND_ON_ASS);
			} else {
				crash(Pose.CRASH_NOSE);
				setCauseOfDeath(Demise.LAND_ON_NOSE);
			}
			return;
		}

		// --- successful landing ---
		setState(PlayerState.DUCKING);
		setPose(Pose.DUCK);
		duckHoldTimer = 0.23f;
		// landing squash, stronger for harder impacts
		squashAmount = -MathUtils.clamp(impactSpeed*0.022f, 0.10f, 0.28f);

		final boolean perfect = absDiff <= PERFECT_LANDING_ANGLE;
		final int flips = flipsAnnounced;

		if (flips > 0) {
			// Alto-style trick scoring: chained tricks multiply each other
			final int comboLen = flips + (proximityCandidate ? 1 : 0);
			int basePoints = FLIP_POINTS[Math.min(flips, 4)];
			if (proximityCandidate)
				basePoints += PROXIMITY_BONUS_POINTS;
			final int totalPoints = basePoints * comboLen;
			getGameManager().onTrickLanded(FLIP_NAMES[Math.min(flips, 4)], proximityCandidate, totalPoints, comboLen);

			// the sonic boost: more combos and a proximity flip give more boost
			float boostSeconds = 1.3f + 0.85f*comboLen;
			if (perfect)
				boostSeconds *= 1.18f;
			addBoost(boostSeconds);
		}

		// when landing, a still-held touch must not be registered as a new touch:
		// the next jump requires a new touch-down (no bouncing when the screen is held)
		isUserInputDown = false;
	}

	/** adds (or extends) a sonic boost */
	public void addBoost(float seconds) {
		final boolean wasBoosting = isBoosting();
		boostTimeLeft = Math.min(BOOST_MAX_TIME, boostTimeLeft + seconds);
		if (!wasBoosting && isBoosting()) {
			SoundPlayer.playSound(A.getSound(SoundAsset.POWERUP), 0.8f);
			getGameManager().addTrickPopup("Sonic Boost!");
		}
	}

	public boolean isBoosting() {
		return boostTimeLeft > 0f && !state.isCrashed();
	}

	/** remaining boost seconds, e.g. for HUD effects */
	public float getBoostTimeLeft() {
		return boostTimeLeft;
	}

	/**
	 * called by a stone when the rider runs into it while the force field is up:
	 * the rock is smashed, the boost is extended and points are awarded
	 * @return true if the stone was smashed (rider survives), false if not boosted
	 */
	public boolean smashRock(Stone stone) {
		if (!isBoosting())
			return false;

		getGameManager().onRockSmashed(ROCK_SMASH_POINTS);
		addBoost(BOOST_ROCK_EXTEND);
		speed = Math.min(BOOST_MAX_SPEED, speed + 1.6f);
		SoundPlayer.playSound(A.getSound(SoundAsset.SMASH));

		// throw rock shards into the air
		final StoneDebris debris = Pools.get(StoneDebris.class).obtain();
		debris.init(stone.getRegion(), stone.getX()+1.5f, stone.getY()+1.2f);
		getStage().addActor(debris);
		spray.startEmitting();
		return true;
	}

	/**
	 * This must be public. A crash can be triggered internally (ie. by landing not on feet) and externally (ie. by hitting a stone)
	 * @param crashPose
	 */
	public void crash(Pose crashPose) {
		isUserInputDown = false;
		jumpBufferTimer = 0f;
		boostTimeLeft = 0f;
		setState(PlayerState.CRASHED);
		setPose(crashPose);
		addAction(Actions3D.sequence(
				Actions3D.delay(2f),
				Actions3D.run(new Runnable() {
					@Override
					public void run() {
						new GameOverDialog(getCauseOfDeath().toString(), getTraveledDistanceMeters()).show(getGameScreen().guiStage);

						//blurr background behind dialog
				        Zoomer sb = new Zoomer((int)(Gdx.graphics.getWidth() * 0.25f), (int)(Gdx.graphics.getHeight() * 0.25f) , Quality.VeryHigh);
				        sb.setBlurStrength(2);
						getGameScreen().postProcessor.addEffect( sb );

						// play sad music
				        MusicProvider.getInstance().fadeToMusic(A.getMusic(MusicAsset.SAD));

					}
				})
				));
	}

	public void drop() {
		crash(Pose.CRASH_ASS);
		setState(PlayerState.DROPPED);
	}

	public PlayerState getState() {
		return state;
	}

	public void setState(PlayerState state) {
		if (this.state.equals(state))
			return;
		timeInState =0f;
		this.state = this.state.moveTo(state);
		if (state == PlayerState.SLIDING ||state == PlayerState.DUCKING) {
			spray.startEmitting();
		} else {
			spray.stopEmitting();
		}
	}

	public float getTimeInState() {
		return timeInState;
	}

	public void addListener (PerformerListener listener)
	{
		listeners.add(listener);
	}

	@Override
	public boolean isCulled(Camera3D cam) {
		return false;
	}

	@Override
	public void dispose() {
	}

	@Override
	public float getWidth() {
		return PERFORMER_WIDTH;
	}

	@Override
	public float getHeight() {
		return PERFORMER_WIDTH;
	}

	public Vector2 getScarfAttachPointInStageCoords() {
		scarfAttachPoint.set(0.5f, 0.f);
		this.localToParentCoordinates(scarfAttachPoint);
		return scarfAttachPoint;
	}

	public int getTraveledDistanceMeters() {
		if (!state.isStarted())
			return 0;
		float dist = getX()-startedAtX;
		return (int)dist;
	}

	public void setCauseOfDeath(Demise causeOfDeath) {
		this.causeOfDeath = causeOfDeath;
	}

	public Demise getCauseOfDeath() {
		return causeOfDeath;
	}

	/**
	 * shortest signed arc in degrees that rotates from 'from' towards 'to'
	 * (positive = counter-clockwise)
	 */
	public static float shortestArcDeg(float from, float to) {
		float d = (to - from) % 360f;
		if (d > 180f) d -= 360f;
		if (d < -180f) d += 360f;
		return d;
	}
}
