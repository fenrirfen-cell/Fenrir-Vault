package com.nuvio.app.launcher

import com.nuvio.app.MainActivity

class AppIconDefault : MainActivity()

class AppIconArcticBlue : MainActivity()

class AppIconEmerald : MainActivity()

class AppIconRoseGold : MainActivity()

class AppIconCopper : MainActivity()

class AppIconGraphite : MainActivity()

/**
 * Fenrir Vault's one home-screen entry. A new name the phone has never seen, so launchers that
 * cached Nuvio's icon for the old entries (AppIcon*) show the Fenrir icon instead.
 */
class FenrirLauncher : MainActivity()
