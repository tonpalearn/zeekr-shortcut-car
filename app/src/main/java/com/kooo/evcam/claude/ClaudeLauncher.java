/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

/**
 * ตัดสินว่าปุ่ม "ถาม Claude" จะเปิดอะไร:
 * <ul>
 *   <li>โหมดในแอป (ค่าเริ่มต้น): หน้า {@link ClaudeActivity} เรียก Claude API ด้วยกุญแจของผู้ใช้</li>
 *   <li>โหมดแอปทางการ: เปิดแอป Claude ของ Anthropic ที่ติดตั้งบนจอรถ — ล็อกอินด้วยบัญชี Pro/Max
 *       ในแอปนั้นเอง ไม่ต้องใช้กุญแจ API. แอปเราแค่ "เปิด" ให้ ไม่แตะ token หรือบัญชีใด ๆ</li>
 * </ul>
 * ถ้าเลือกโหมดแอปทางการแต่ไม่ได้ติดตั้ง จะเด้งกลับมาหน้าในแอปพร้อมบอกเหตุผล
 */
public final class ClaudeLauncher {

    /**
     * ชื่อแพ็กเกจแอป Claude สำหรับ Android ของ Anthropic.
     * ยืนยันบนรถได้ด้วยปุ่ม ✓ ตรวจความพร้อม (บอกว่าเจอหรือไม่เจอ)
     */
    public static final String OFFICIAL_PACKAGE = "com.anthropic.claude";

    private ClaudeLauncher() {
    }

    public static boolean officialInstalled(Context context) {
        return officialIntent(context) != null;
    }

    static Intent officialIntent(Context context) {
        try {
            return context.getPackageManager().getLaunchIntentForPackage(OFFICIAL_PACKAGE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** เวอร์ชันของแอปทางการ (ไว้โชว์ในหน้าตรวจ) หรือ null ถ้าไม่มี */
    static String officialVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(OFFICIAL_PACKAGE, 0).versionName;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /** ปุ่ม "ถาม Claude": ทำตามโหมดที่ตั้งไว้ */
    public static void ask(Context context) {
        if (new ClaudePrefs(context).useOfficialApp() && openOfficial(context)) {
            return;
        }
        Intent claude = new Intent(context, ClaudeActivity.class);
        claude.putExtra(ClaudeActivity.EXTRA_LISTEN, true);
        if (new ClaudePrefs(context).useOfficialApp()) {
            // ตั้งให้ใช้แอปทางการ แต่เปิดไม่ได้: บอกในหน้าในแอปแทนการเงียบ
            claude.putExtra(ClaudeActivity.EXTRA_OFFICIAL_MISSING, true);
            claude.putExtra(ClaudeActivity.EXTRA_LISTEN, false);
        }
        claude.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        context.startActivity(claude);
    }

    /** เปิดแอป Claude ทางการ — @return false ถ้าไม่ได้ติดตั้งหรือเปิดไม่ได้ */
    public static boolean openOfficial(Context context) {
        Intent intent = officialIntent(context);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(context, "เปิดแอป Claude ไม่ได้", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
