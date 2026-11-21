/*
 * tpl-zeekr — Claude mode phase 2 tests. Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.kooo.evcam.button.ButtonAction;
import com.kooo.evcam.button.ClaudeCarBridge;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ClaudeCarKeysTest {

    /** ทุกคีย์ในรายการอนุญาตต้องมีจริงในคำสั่งของ dts88 — ถ้า dts88 เปลี่ยนชื่อ เทสต์นี้จะเตือน */
    @Test
    public void everyAllowedKeyExistsInButtonActions() {
        for (ClaudeCarPolicy.Entry e : ClaudeCarPolicy.allowed().values()) {
            ButtonAction a = ClaudeCarBridge.find(e.key);
            assertNotNull("ไม่มีคำสั่ง " + e.key + " ใน ButtonAction", a);
            assertEquals("stepwise ของ " + e.key + " ไม่ตรงกับ dts88", a.stepwise(), e.stepwise);
        }
    }

    @Test
    public void unknownKeyReportsUnknown() {
        String[] out = {null};
        ClaudeCarBridge.perform(org.robolectric.RuntimeEnvironment.getApplication(), "unlock_everything", 1,
                r -> out[0] = r);
        assertEquals(ClaudeCarBridge.UNKNOWN, out[0]);
    }

    @Test
    public void resultTextNeverClaimsSuccessWrongly() {
        assertTrue(ClaudeCarTools.resultText("DONE").startsWith("สำเร็จ"));
        for (String r : new String[]{"NO_CHANGE", "UNAVAILABLE", "POWER_SAVING", "MOVING", "NOT_FITTED", "TIMEOUT",
                "UNKNOWN", "SENT", null}) {
            assertTrue(r + "", !ClaudeCarTools.resultText(r).startsWith("สำเร็จ"));
        }
    }
}
