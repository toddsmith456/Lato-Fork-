# Lato Boosted - A downhill snowboarding game for Android (Alto's-Adventure-style fork)

> **This is a fork of [Lato](https://gitlab.com/ar-/lato) (by Andreas Redmer) with a much richer,
> Alto's-Adventure-style gameplay loop: trick scoring, combos and the sonic boost / rock-smash
> mechanics, plus a drastically smoother rider. Free and open source (GPL-3.0), no ads, no
> in-app purchases.**

![screenshot](metadata/en-GB/images/featureGraphic.png)

# What's new in this fork (v2.0.0)

Everything that makes Alto's Adventure fun, rebuilt into Lato:

## Tricks, combos & score
* **Backflips** – hold the touch in the air to backflip. Chained flips on one air score
  Alto-style: Backflip 10, **Double 60**, **Triple 200**, **Quadruple 500** points.
* **Proximity backflips** – flip close to the ground for a **+300** bonus.
* **Combo multiplier** – chained tricks on a single air multiply each other (`x2 Combo!`).
* **Chasm Jump!** – survive a canyon for **+50**.
* **Trick score HUD & floating popups** – "Backflip! +10", "Sonic Boost!", "Rock Smash! +50"…
* **Game-over breakdown** – distance, coins, trick score, total score, flips, rocks smashed,
  best combo, and your persistent **best distance / best score** records.

## The sonic boost (the heart of Alto)
* Landing a trick charges a **sonic boost**: a speed burst **plus a glowing force field**.
* While the force field is up, **rocks shatter on contact** instead of ending your run –
  and every smashed rock **extends the boost** and scores **Rock Smash! +50**.
* Rock-shatter debris effect, smash/power-up/whoosh sound effects (self-made, GPL).
* Boosted top speed rises from 29.3 to 38.5 m/s; the camera pulls back with the speed.

## Much smoother rider
* Rotation pivot eases between board (ground) and rider centre (air) – flips look right.
* Board smoothly aligns with the slope on landing instead of snapping to it.
* **Jump buffering**: touching the screen a split second before touchdown still jumps.
* Squash & stretch animation on take-off and landing; speed changes ease instead of jumping.
* Rocks appear earlier (after 150 m) and slightly denser, so the smash mechanic matters.

## Development
* New automated gameplay smoke test (a bot that plays the game on the desktop build and
  verifies flips / boost / rock smashing / chasm bonuses):

```
  xvfb-run -a ./gradlew :desktop:smokeTest
```

# License
Lato Boosted - downhill snowboarding game for Android

Copyright (C) 2020-2023 Andreas Redmer
Copyright (C) 2026 The Lato fork contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

This full license is also in [LICENCE](LICENCE)
(Same license as the original Lato project: GNU GPL v3 or later.)

# Screenshots
<p float="left">
  <img src="metadata/en-GB/images/phoneScreenshots/lato_dawn.png" width="49%" />
  <img src="metadata/en-GB/images/phoneScreenshots/lato_day.png" width="49%" /> 
</p>

<p float="left">
  <img src="metadata/en-GB/images/phoneScreenshots/lato_dusk.png" width="49%" />
  <img src="metadata/en-GB/images/phoneScreenshots/lato_night.png" width="49%" /> 
</p>

# controls
* touch the screen: start the game
* touch the screen: jump
* hold the screen in the air: backflip (release in time to land on your board)
* land tricks: earn a sonic boost force field that smashes through rocks

# international age ratings

*    ACB: G (general)
*    ClassInd: L
*    ESRB: E (everyone)
*    PEGI:3
*    USK: 0
*    IARC: 3

# Quickstart

Option 1: [![Get it on F-Droid](https://f-droid.org/wiki/images/3/31/F-Droid-button_get-it-on_smaller.png)](https://f-droid.org/packages/ardash.lato/)
(the F-Droid build is the *original* Lato; this fork's ready-to-install APK is in [apk/](apk))

Option 2: Build it from source

	git clone <this-repo>
	cd lato/p
	./gradlew assembleDebug
	# signed debug APK:  ./android/build/outputs/apk/debug/android-debug.apk
	./gradlew assembleRelease
	# unsigned release APK: ./android/build/outputs/apk/android-release-unsigned.apk

# History

This is a 2020 remake of the Game Alto's Adventure (2015), which was a remake of Ski Safari (2012). The first release of this game is in 2023.

'Lato' is the name of the main character and describes someone who is always 'late' and that's why he has to hurry.

Unfortunately none of the previous endless runner games was FLOSS. There was TredGamerZ/Legendary (2017), it was open source but still based on Unity. Unity is not open source. The corona SDK engine has been made open source in Jan 2019 and they got a show case example called Endless Sk8boarder, which looks like crap and doesn't do good marketing for the engine.

The day and night cycles of Alto's adventure have also been remade a few times - most promitently in the game Fishing Life in 2019. However Fishing Life was also made in Unity. Lato is the first release of these day and night cycles into the open source world, to be reused in other projects.

The only suitable way around the restictions of closed source games and engines was to use a well established and well supported FLOSS engine (LibGDX) to make a complete new endless runner game. Unfortunately many problems are also not resolved yet in LibGDX and yet have to be pioneered. That's what this project attempts. Bring a showcase app to the FLOSS community that provides an example implementation for:
* endless procedural random terrain
* 2D and 3D mixture
* realistic weather and day night cycles
* physics without physics engine (Box2D was possible over overkill, so it was removed in 8e059919b01a148cc3303734567100079ae2bb18)
* chained shaders
* Scene3D - likely the frist working version at all (only implemented to the necessary extend)

in LIBGDX.

If you are a game maker too, please feel free to copy everything you need from this project. If you copy the code and art: obey the license. If you copy the idea: no problem. If you are a gamer, please enjoy this game for free, no ads, no optional payments, pure fun.

All parts of this project are meant to be developed with FLOSS software.

Engine: LibGDX

Target Platforms: Android, Linux

Dev PC: Ubuntu Linux

IDE: Eclipse

Sprite drawing: Inkscape

Animations and rigging: Synfig Studio

Pixeling: Gimp

# Music

Music: Traveler (2019) by Alexander Nakarada (www.serpentsoundstudios.com)

Licensed under Creative Commons: By Attribution 4.0 License

http://creativecommons.org/licenses/by/4.0/

# Donate

Cash donations are not accepted. You can buy the author of this app a coffee if you have some spare cryptocurrencies.

* BTC/BCH/BTG/etc: 1J2bbhJYksSjeynGGhuSPN9aTEaxiGm4nR
* BTC Bech32: bc1qgshj3mtju02sg9ymse9cksfjdjh5gp0204w3zj
* DASH: XbLRt5imEHc72KmhvC7V9v8f9NmYrmvweS
* FIRO: a4tAW5vp8rzjFrAxhRaq24m6vFZ2AmHUYs
* ETH: 0x0a6604dc5000c57e80f824601535db216e77482f
* XMR: 4AffoFbFhfGZdBeMaQYSCMTURacM3qZYxKHQeLx8xkiLUjzk2GPzjCrNU5uquXEsEL6wcN8b5ULg5JdDaQfuQRkUJs6xx3f

*Note: These addresses are taken from the original autors website. They are cryptographically signed, with the same key, that signed the git commits of this software project. Feel free to verify the GPG signatures so you can be sure, that you donation goes to the person, who actually commited the code of this software.*

How much to donate? 🙂

1. Go to your nearest coffee shop (or bar [or cafe in the Netherlands]).
2. Get the price for a regular coffee (or beer). No sugar.
3. Optionally multiply by 2. Thanks.
4. Convert the price into a crypto currency mentioned above. 
5. Donate the resulting value.

✌️

# Contact / Community
### Matrix room
https://matrix.to/#/#lato:abga.be

### email the developer
ar-lato-26ab0d@abga.be

### email to create a gitlab ticket (if you don't have a gitlab account)
incoming+ar-/lato@gitlab.com
