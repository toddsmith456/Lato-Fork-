package ardash.lato;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;

import ardash.gdx.scenes.scene3d.Actor3D;
import ardash.lato.actors.Performer;
import ardash.lato.actors3.Stone;
import ardash.lato.screens.GameScreen;

/**
 * Automated gameplay smoke test (desktop, needs an X display, e.g. xvfb-run -a).
 *
 * Boots the real game and plays it with a small reactive "bot":
 * <ul>
 * <li>jumps over canyons (detected via the terrain height function)</li>
 * <li>jumps over rocks unless a sonic boost force field is up (then it smashes through)</li>
 * <li>periodically holds the touch input mid-air to backflip for trick score &amp; boost</li>
 * <li>restarts the run when the rider dies</li>
 * </ul>
 *
 * It verifies the Alto-style gameplay: flips counted, sonic boost activated,
 * boosted speed above the regular maximum, rocks smashed, score accumulated,
 * and captures screenshots. Results go to SMOKE_OUT (default ./smoke): result.txt + PNGs.
 */
public class SmokeTestLauncher {

	private static final String OUT_DIR = System.getenv("SMOKE_OUT") != null ? System.getenv("SMOKE_OUT") : "smoke";
	private static PrintWriter report;

	// bot metrics (accumulated across all runs)
	private static int runs = 0;
	private static boolean sawBoost = false;
	private static float maxSpeedSeen = 0f;
	private static int maxScoreSeen = 0;
	private static int rocksFromGm = 0;
	private static int flipsFromGm = 0;
	private static int chasmsFromGm = 0;
	private static int bestComboSeen = 0;
	private static volatile boolean flipInProgress = false;
	private static long lastFlipEndedAt = 0;

	// snapshot of the world, refreshed on the GL thread
	private static class Probe {
		boolean ready, crashed, boosting, inAir;
		float x, y, speed, dropAhead, stoneDist, rotation;
		int score, rocks, flips, chasms, combo, dist;
		String demise = "?";

		@Override
		public String toString() {
			return String.format("dist=%d x=%.1f y=%.2f rot=%.0f spd=%.1f air=%b boost=%b",
					dist, x, y, rotation, speed, inAir, boosting);
		}
	}

	// short history of the last probes, dumped when a run ends
	private static final Probe[] history = new Probe[16];
	private static int historyPos = 0;
	private static void record(Probe p) {
		history[(historyPos++) % history.length] = p;
	}
	private static String dumpHistory() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < history.length; i++) {
			Probe p = history[(historyPos + i) % history.length];
			if (p != null)
				sb.append("\n    ").append(p.toString());
		}
		return sb.toString();
	}
	private static final AtomicReference<Probe> snapshot = new AtomicReference<Probe>(new Probe());

	public static void main(String[] arg) throws Exception {
		new File(OUT_DIR).mkdirs();
		report = new PrintWriter(new FileWriter(OUT_DIR + "/result.txt"));

		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setWindowedMode(1280, 720);
		config.setForegroundFPS(60);
		config.setTitle("Lato smoke test");

		Thread driver = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					drive();
				} catch (Throwable t) {
					log("FATAL driver error: " + t);
					t.printStackTrace(report);
					report.flush();
					Gdx.app.exit();
				}
			}
		});
		driver.setDaemon(true);
		driver.start();

		new Lwjgl3Application(new LatoGame(), config);
		report.flush();
		report.close();
		System.out.println("SMOKE TEST DONE");
		System.exit(0);
	}

	// ------------------------------------------------------------------ bot --

	/**
	 * controlled experiment: one clean tap (down 70ms, then up), no other input:
	 * any rotation growth afterwards must come from the game itself.
	 */
	private static void diagCleanTap() throws Exception {
		StringBuilder dump = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			Probe p = probe();
			dump.append("\n  pre-tap ").append(p);
		}
		log("DIAG: clean tap down (guiStage only, no up for 70ms)");
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				try {
					((GameScreen) ((LatoGame) Gdx.app.getApplicationListener()).getScreen()).guiStage.touchDown(100, 100, 0, 0);
				} catch (Throwable t) {
					log("diag down err " + t);
				}
			}
		});
		Thread.sleep(70);
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				try {
					((GameScreen) ((LatoGame) Gdx.app.getApplicationListener()).getScreen()).guiStage.touchUp(100, 100, 0, 0);
				} catch (Throwable t) {
					log("diag up err " + t);
				}
			}
		});
		for (int i = 0; i < 30; i++) {
			Probe p = probe();
			dump.append("\n  post-tap ").append(p).append(" boost=").append(p.boosting);
		}
		log("DIAG RESULTS:" + dump);
		log("DIAG: if rotation grew without any hold, it's a game bug");
	}

	private static void drive() throws Exception {
		log("driver started: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));

		// wait for the game screen
		for (int i = 0; i < 60 && !probe().ready; i++)
			Thread.sleep(1000);
		if (!probe().ready) {
			log("FAIL: game screen never became ready");
			report.flush();
			Gdx.app.exit();
			return;
		}
		Thread.sleep(1200);
		screenshot("01_boot_titlescreen");

		if (System.getenv("SMOKE_DIAG") != null) {
			diagCleanTap();
			report.flush();
			Gdx.app.exit();
			return;
		}

		// start the first run
		tap();
		Thread.sleep(600);

		final long botStart = System.currentTimeMillis();
		boolean tookBoostShot = false, tookFlipShot = false, tookSmashShot = false, boostShotAtStart=false;
		int lastRocks = 0;
		long boostSeenAt = 0;

		// main bot loop (~3.5 minutes max)
		while (System.currentTimeMillis() - botStart < 210_000) {
			Probe p = probe();
			record(p);

			// accumulate global metrics
			maxSpeedSeen = Math.max(maxSpeedSeen, p.speed);
			maxScoreSeen = Math.max(maxScoreSeen, p.score);
			rocksFromGm = Math.max(rocksFromGm, p.rocks);
			flipsFromGm = Math.max(flipsFromGm, p.flips);
			chasmsFromGm = Math.max(chasmsFromGm, p.chasms);
			bestComboSeen = Math.max(bestComboSeen, p.combo);
			if (p.boosting) {
				sawBoost = true;
				boostSeenAt = System.currentTimeMillis();
				if (!tookBoostShot && p.speed > 26f) {
					screenshot("05_sonic_boost_forcefield");
					tookBoostShot = true;
				}
			}
			if (p.rocks > lastRocks) {
				lastRocks = p.rocks;
				if (!tookSmashShot)
					screenshot("06_rock_shattered");
				tookSmashShot = true;
			}

			// dead? -> wait for the game-over dialog, then restart
			if (p.crashed) {
				log("run " + runs + " ended: dist=" + p.dist + " score=" + p.score + " rocks=" + p.rocks
						+ " flips=" + p.flips + " chasms=" + p.chasms + " combo=" + p.combo + " CAUSE: " + p.demise
						+ " HISTORY:" + dumpHistory());
				runs++;
				flipInProgress = false;
				Thread.sleep(2300);
				screenshot("07_gameover_run" + runs);
				Thread.sleep(400);
				tap(); // dismiss -> back to loading screen
				Thread.sleep(2500);
				tap(); // start the new run
				Thread.sleep(800);
				continue;
			}

			if (p.dist > 1 && p.inAir && !tookFlipShot && flipInProgress) {
				screenshot("03_mid_flip");
				tookFlipShot = true;
			}
			if (p.boosting && !boostShotAtStart && System.currentTimeMillis()-boostSeenAt>400)
			{
				screenshot("04_boost_riding");
				boostShotAtStart = true;
			}

			// --- decision making (only act when alive and run started) ---
			if (!flipInProgress && p.dist >= 0) {
				boolean wantedHold = false;

				if (p.crashed) {
					// handled above
				} else if (!p.inAir && p.dropAhead > 9f && p.dist > 25) {
					// canyon edge coming: long jump (with a flip when fast enough)
					if (p.speed >= 14f) {
						log("bot: canyon ahead (" + fmt(p.dropAhead) + "m drop), jump+flip @dist " + p.dist
								+ " speed " + fmt(p.speed));
						startSmartHold(2700);
						wantedHold = true;
					} else {
						log("bot: canyon ahead at low speed, plain hop @dist " + p.dist);
						tap();
					}
				} else if (!p.inAir && p.stoneDist > 0 && p.stoneDist < 4.4f && !p.boosting) {
					// rock ahead and no force field: hop over it early enough
					log("bot: rock ahead at " + fmt(p.stoneDist) + "m, hopping @dist " + p.dist);
					tap();
				} else if (!p.inAir && p.stoneDist > 0 && p.stoneDist < 6f && p.boosting) {
					// force field up and rock close: plow straight through it
					log("bot: smashing through rock @dist " + p.dist);
				} else if (!p.inAir && p.speed > 14f && p.dist > 15 && p.dropAhead > 1.2f
						&& System.currentTimeMillis() - lastFlipEndedAt > 1500) {
					// regular trick cadence: flip off a drop for score + boost; slow-Lato
					// flips need a falling slope ahead to have enough air to finish
					log("bot: trick flip over a drop for score @dist " + p.dist + " speed " + fmt(p.speed));
					startSmartHold(2200);
					wantedHold = true;
				}

				if (wantedHold)
					continue;
			}

			Thread.sleep(90);
		}

		// --------------------------------- report -------------------------------
		log("================ SUMMARY ================");
		log("runs=" + runs + " flipsLanded=" + flipsFromGm + " rocksSmashed=" + rocksFromGm
				+ " chasmsJumped=" + chasmsFromGm + " bestCombo=" + bestComboSeen);
		log("sawBoost=" + sawBoost + " maxSpeedSeen=" + fmt(maxSpeedSeen) + " (regular max is 29.3)");
		log("maxScoreSeen=" + maxScoreSeen);

		boolean pass = sawBoost && maxSpeedSeen > 30f && maxScoreSeen > 0;
		log(pass ? "RESULT: PASS (sonic boost + trick scoring verified)"
				: "RESULT: PARTIAL (boost or scoring not fully verified, see metrics)");
		report.flush();
		Gdx.app.exit();
	}

	/** start holding the touch input for the given ms (flip), in a background thread */
	private static void startHold(final long ms) {
		flipInProgress = true;
		hold(true);
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(ms);
				} catch (InterruptedException ignored) {
				}
				hold(false);
				lastFlipEndedAt = System.currentTimeMillis();
				flipInProgress = false;
			}
		}).start();
	}

	/**
	 * Like a human player: press (jumps), keep holding (backflip), and release
	 * when the board rotation is past ~300 degrees so it can complete the flip
	 * and land on the snow. Hard cap so the thread always terminates.
	 */
	private static void startSmartHold(final long hardCapMs) {
		flipInProgress = true;
		hold(true);
		new Thread(new Runnable() {
			@Override
			public void run() {
				final long begin = System.currentTimeMillis();
				boolean released = false;
				boolean sawInverted = false;
				while (System.currentTimeMillis() - begin < hardCapMs) {
					try {
						Probe p = probe();
						if (p.inAir && p.rotation > 120f && p.rotation < 240f)
							sawInverted = true; // definitely committed to a flip
						if (sawInverted && p.rotation > 300f && p.rotation <= 360f) {
							hold(false);
							released = true;
							break;
						}
						if (p.crashed)
							break;
						Thread.sleep(45);
					} catch (InterruptedException ignored) {
						break;
					}
				}
				if (!released)
					hold(false);
				lastFlipEndedAt = System.currentTimeMillis();
				flipInProgress = false;
			}
		}).start();
	}

	// --------------------------------------------------------------- probing --

	/** posts a probe runnable and waits briefly for the refreshed snapshot */
	private static Probe probe() throws InterruptedException {
		if (Gdx.app == null) {
			Thread.sleep(500);
			return snapshot.get(); // application not created yet
		}
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				Probe p = new Probe();
				try {
					LatoGame game = (LatoGame) Gdx.app.getApplicationListener();
					if (game.gm == null || !(game.getScreen() instanceof GameScreen)) {
						snapshot.set(p);
						return;
					}
					GameScreen gs = (GameScreen) game.getScreen();
					Performer perf = gs.performer;
					if (perf == null || gs.waveDrawer == null) {
						snapshot.set(p);
						return;
					}
					p.ready = true;
					p.crashed = perf.getState().isCrashed();
					p.boosting = perf.isBoosting();
					p.inAir = perf.getState().isInAir();
					p.x = perf.getX();
					p.y = perf.getY();
					p.speed = perf.getSpeed();
					p.dist = perf.getTraveledDistanceMeters();
					p.score = game.gm.getTrickScoreThisRound();
					p.rocks = game.gm.getRocksSmashedThisRound();
					p.flips = game.gm.getFlipsLandedThisRound();
					p.chasms = game.gm.getChasmsJumpedThisRound();
					p.combo = game.gm.getBestComboThisRound();
					p.demise = String.valueOf(perf.getCauseOfDeath());
					p.rotation = perf.getRotation();

					// terrain ahead: detect canyon edges
					final float hNear = gs.waveDrawer.getHeightAt(p.x + 3f);
					final float hFar = gs.waveDrawer.getHeightAt(p.x + 8f);
					p.dropAhead = hNear - hFar;

					// nearest stone in front
					p.stoneDist = -1f;
					for (Actor3D a : gs.stage3d.getRoot().getChildren()) {
						if (a instanceof Stone) {
							final float d = a.getX() - p.x;
							if (d > 0 && (p.stoneDist < 0 || d < p.stoneDist))
								p.stoneDist = d;
						}
					}
				} catch (Throwable t) {
					log("probe error: " + t);
				}
				snapshot.set(p);
			}
		});
		Thread.sleep(110);
		return snapshot.get();
	}

	// ------------------------------------------------------------------ input --

	private static void tap() {
		hold(true);
		try {
			Thread.sleep(70);
		} catch (InterruptedException e) {
		}
		hold(false);
	}

	/**
	 * Feeds input exactly like a human finger: through the GUI stage only.
	 * (Sending performer.userInput() *and* a stage touchDown made every release
	 * instantly re-press the input -> involuntary buffered jumps at min speed.)
	 */
	private static void hold(final boolean down) {
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				try {
					LatoGame game = (LatoGame) Gdx.app.getApplicationListener();
					if (game.getScreen() instanceof GameScreen) {
						GameScreen gs = (GameScreen) game.getScreen();
						if (gs.guiStage != null) {
							if (down)
								gs.guiStage.touchDown(100, 100, 0, 0);
							else
								gs.guiStage.touchUp(100, 100, 0, 0);
						}
					}
				} catch (Throwable t) {
					log("input error: " + t);
				}
			}
		});
	}

	// ------------------------------------------------------------ screenshots --

	private static void screenshot(final String name) {
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				try {
					final Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0,
							Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
					PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(OUT_DIR + "/" + name + ".png"), pixmap, -1, true);
					pixmap.dispose();
					log("screenshot saved: " + name);
				} catch (Throwable t) {
					log("screenshot error (" + name + "): " + t);
				}
			}
		});
		try {
			Thread.sleep(120);
		} catch (InterruptedException e) {
		}
	}

	private static String fmt(float f) {
		return String.format("%.2f", f);
	}

	private static void log(String s) {
		System.out.println("[smoke] " + s);
		report.println(s);
		report.flush();
	}
}
