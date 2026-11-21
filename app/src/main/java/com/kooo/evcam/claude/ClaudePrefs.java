/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * ค่าตั้งของโหมด Claude อยู่ในไฟล์แยก {@code tpl_claude.xml}.
 *
 * <p>ตั้งใจให้อยู่นอกรายการ STORES ของ {@code SettingsBackup} จึง<b>ไม่ถูกสำรองลง USB</b>
 * และถูกตัดออกจากการสำรองของ Android ใน backup_rules / data_extraction_rules —
 * กุญแจ API ต้องไม่หลุดไปกับแฟลชไดรฟ์</p>
 */
public final class ClaudePrefs {

    public static final String STORE = "tpl_claude";

    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_SPEAK = "speak";
    private static final String KEY_USE_OFFICIAL_APP = "use_official_app";

    /** รุ่นที่เลือกได้: ตัวแรกคือค่าเริ่มต้น */
    public static final String[] MODELS = {"claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5"};
    public static final String[] MODEL_LABELS = {
            "Sonnet 5.5 — สมดุล (แนะนำ)",
            "Haiku 5.5 — เร็วสุด ถูกสุด",
            "Opus 5.5 — เก่งสุด ช้ากว่า แพงกว่า"};

    private final SharedPreferences prefs;

    public ClaudePrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    public String apiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public boolean hasKey() {
        return !apiKey().trim().isEmpty();
    }

    public void setApiKey(String key) {
        prefs.edit().putString(KEY_API_KEY, key == null ? "" : key.trim()).apply();
    }

    public String model() {
        String m = prefs.getString(KEY_MODEL, MODELS[0]);
        for (String known : MODELS) {
            if (known.equals(m)) {
                return m;
            }
        }
        return MODELS[0];
    }

    public int modelIndex() {
        String m = model();
        for (int i = 0; i < MODELS.length; i++) {
            if (MODELS[i].equals(m)) {
                return i;
            }
        }
        return 0;
    }

    public void setModel(String model) {
        prefs.edit().putString(KEY_MODEL, model).apply();
    }

    /** อ่านคำตอบออกเสียงไหม */
    public boolean speak() {
        return prefs.getBoolean(KEY_SPEAK, true);
    }

    public void setSpeak(boolean speak) {
        prefs.edit().putBoolean(KEY_SPEAK, speak).apply();
    }

    /** ปุ่ม "ถาม Claude" เปิดแอป Claude ทางการ (ใช้บัญชี subscription) แทนหน้าในแอป */
    public boolean useOfficialApp() {
        return prefs.getBoolean(KEY_USE_OFFICIAL_APP, false);
    }

    public void setUseOfficialApp(boolean use) {
        prefs.edit().putBoolean(KEY_USE_OFFICIAL_APP, use).apply();
    }

    /** โชว์กุญแจแบบปิดไว้ เหลือ 4 ตัวท้าย */
    public static String masked(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "";
        }
        String k = key.trim();
        return "••••" + (k.length() > 4 ? k.substring(k.length() - 4) : "");
    }
}
