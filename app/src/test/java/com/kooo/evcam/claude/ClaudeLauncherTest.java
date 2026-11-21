/*
 * tpl-zeekr — Claude mode tests. Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ClaudeLauncherTest {

    private Application app() {
        return RuntimeEnvironment.getApplication();
    }

    /** ทำเหมือนมีแอป Claude ทางการติดตั้งอยู่ (มีหน้า LAUNCHER) */
    private void installOfficial() {
        ComponentName main = new ComponentName(ClaudeLauncher.OFFICIAL_PACKAGE, ClaudeLauncher.OFFICIAL_PACKAGE + ".Main");
        PackageInfo info = new PackageInfo();
        info.packageName = ClaudeLauncher.OFFICIAL_PACKAGE;
        info.versionName = "1.0-test";
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = ClaudeLauncher.OFFICIAL_PACKAGE;
        shadowOf(app().getPackageManager()).installPackage(info);
        ActivityInfo activity = new ActivityInfo();
        activity.name = main.getClassName();
        activity.packageName = main.getPackageName();
        shadowOf(app().getPackageManager()).addOrUpdateActivity(activity);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        shadowOf(app().getPackageManager()).addIntentFilterForActivity(main, filter);
    }

    @Test
    public void defaultModeOpensInAppScreenAndListens() {
        ClaudeLauncher.ask(app());
        Intent started = shadowOf(app()).getNextStartedActivity();
        assertEquals(ClaudeActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(started.getBooleanExtra(ClaudeActivity.EXTRA_LISTEN, false));
        assertFalse(started.getBooleanExtra(ClaudeActivity.EXTRA_OFFICIAL_MISSING, false));
    }

    @Test
    public void officialModeWithoutAppFallsBackAndExplains() {
        new ClaudePrefs(app()).setUseOfficialApp(true);
        assertFalse(ClaudeLauncher.officialInstalled(app()));
        ClaudeLauncher.ask(app());
        Intent started = shadowOf(app()).getNextStartedActivity();
        assertEquals(ClaudeActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(started.getBooleanExtra(ClaudeActivity.EXTRA_OFFICIAL_MISSING, false));
        assertFalse(started.getBooleanExtra(ClaudeActivity.EXTRA_LISTEN, true));
    }

    @Test
    public void officialModeWithAppOpensOfficialApp() {
        installOfficial();
        new ClaudePrefs(app()).setUseOfficialApp(true);
        assertTrue(ClaudeLauncher.officialInstalled(app()));
        assertEquals("1.0-test", ClaudeLauncher.officialVersion(app()));
        ClaudeLauncher.ask(app());
        Intent started = shadowOf(app()).getNextStartedActivity();
        assertEquals(ClaudeLauncher.OFFICIAL_PACKAGE, started.getComponent().getPackageName());
    }

    @Test
    public void inAppModeIgnoresInstalledOfficialApp() {
        installOfficial();
        new ClaudePrefs(app()).setUseOfficialApp(false);
        ClaudeLauncher.ask(app());
        Intent started = shadowOf(app()).getNextStartedActivity();
        assertEquals(ClaudeActivity.class.getName(), started.getComponent().getClassName());
    }
}
