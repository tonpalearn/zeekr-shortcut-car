/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import android.content.Context;
import android.os.Handler;

import com.google.gson.JsonObject;
import com.kooo.evcam.AppLog;
import com.kooo.evcam.button.ClaudeCarBridge;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ทำเครื่องมือ {@code car_action} ที่ Claude ขอ: ผ่านด่าน {@link ClaudeCarPolicy} → สั่งรถผ่าน {@link ClaudeCarBridge}
 * → รอผลจริง (ไม่เกิน {@link #WAIT_SECONDS} วินาที) → คืนข้อความไทยให้ Claude.
 *
 * <p>เรียก {@link #run} จาก thread พื้นหลัง (ClaudeConversation); การสั่งรถเกิดบนเธรดหลัก</p>
 */
final class ClaudeCarTools implements ClaudeConversation.ToolRunner {

    static final int WAIT_SECONDS = 10;
    private static final String TAG = "ClaudeCarTools";

    /** ขึ้นการ์ดยืนยันบนจอ (เรียกบนเธรดหลัก) */
    interface Confirmer {
        void ask(ClaudeCarPolicy.Entry entry, int steps);
    }

    private final Context app;
    private final Handler main;
    private final ClaudePrefs prefs;
    private final Confirmer confirmer;

    ClaudeCarTools(Context context, Handler main, ClaudePrefs prefs, Confirmer confirmer) {
        this.app = context.getApplicationContext();
        this.main = main;
        this.prefs = prefs;
        this.confirmer = confirmer;
    }

    @Override
    public String run(String name, JsonObject input) {
        if (!ClaudeCarPolicy.TOOL_NAME.equals(name)) {
            return "ไม่มีเครื่องมือชื่อนี้";
        }
        String key = input.has("action") && input.get("action").isJsonPrimitive()
                ? input.get("action").getAsString() : null;
        int steps = input.has("steps") && input.get("steps").isJsonPrimitive() ? safeInt(input, "steps") : 1;
        ClaudeCarPolicy.Entry entry = ClaudeCarPolicy.entry(key);
        Float kmh = CarSpeed.kmh();
        // คำสั่งระดับยืนยัน: อ่านความเร็วไม่ได้ = ถือว่ายังไม่แน่ใจว่าจอด → ไม่ทำ
        boolean moving = ClaudePrompt.isMoving(kmh)
                || (entry != null && entry.level == ClaudeCarPolicy.Level.CONFIRM_PARKED && kmh == null);
        ClaudeCarPolicy.Verdict verdict = ClaudeCarPolicy.decide(key, moving, prefs.carControl());
        AppLog.i(TAG, "car_action " + key + " steps=" + steps + " -> " + verdict);
        switch (verdict) {
            case REJECT_DISABLED:
                return "ทำไม่ได้: ผู้ใช้ปิดสวิตช์ \"ให้ Claude สั่งรถได้\" ไว้ในตั้งค่า";
            case REJECT_UNKNOWN:
                return "ทำไม่ได้: ไม่มีคำสั่งนี้ในรายการที่อนุญาต";
            case REJECT_MOVING:
                return kmh == null
                        ? "ทำไม่ได้: คำสั่งนี้ต้องจอดรถนิ่งก่อน แต่ตอนนี้อ่านความเร็วรถไม่ได้"
                        : "ทำไม่ได้: คำสั่งนี้ต้องจอดรถนิ่งก่อน";
            case NEEDS_CONFIRM: {
                int n = ClaudeCarPolicy.clampSteps(entry, steps);
                main.post(() -> confirmer.ask(entry, n));
                return "ยังไม่ได้ทำ: แอปขึ้นปุ่มยืนยัน \"" + entry.thai
                        + "\" บนจอแล้ว ให้บอกผู้ใช้สั้น ๆ ว่ากดยืนยันบนจอได้เลย (คุณกดแทนไม่ได้)";
            }
            case ALLOW:
            default:
                return resultText(performBlocking(key, ClaudeCarPolicy.clampSteps(entry, steps)));
        }
    }

    private String performBlocking(String key, int steps) {
        String[] out = {null};
        CountDownLatch latch = new CountDownLatch(1);
        main.post(() -> {
            try {
                ClaudeCarBridge.perform(app, key, steps, r -> {
                    out[0] = r;
                    latch.countDown();
                });
            } catch (RuntimeException e) {
                AppLog.w(TAG, "perform " + key + " failed", e);
                out[0] = "UNAVAILABLE";
                latch.countDown();
            }
        });
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                return "TIMEOUT";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "TIMEOUT";
        }
        return out[0];
    }

    private static int safeInt(JsonObject input, String field) {
        try {
            return input.get(field).getAsInt();
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** ผลจากรถ → ข้อความไทย (ส่งให้ Claude และโชว์ในการ์ดยืนยัน) */
    static String resultText(String result) {
        if (result == null) {
            return "ไม่ทราบผล";
        }
        switch (result) {
            case "DONE":
                return "สำเร็จ: รถรายงานว่าเปลี่ยนแล้ว";
            case "NO_CHANGE":
                return "รถรับคำสั่งแต่ค่าไม่เปลี่ยน (อาจเป็นค่านั้นอยู่แล้ว)";
            case "UNAVAILABLE":
                return "ทำไม่ได้: รถไม่รองรับคำสั่งนี้ หรือเชื่อมระบบรถไม่ได้";
            case "POWER_SAVING":
                return "ทำไม่ได้: รถอยู่ในโหมดประหยัดพลังงานสูงสุด";
            case "MOVING":
                return "ทำไม่ได้: ต้องจอดรถนิ่งก่อน";
            case "NOT_FITTED":
                return "ทำไม่ได้: รถคันนี้ไม่มีอุปกรณ์นี้";
            case ClaudeCarBridge.SENT:
                return "ส่งคำสั่งแล้ว (คำสั่งนี้ไม่มีผลยืนยันกลับจากรถ)";
            case "TIMEOUT":
                return "ไม่ทราบผล: รถไม่ตอบภายใน " + WAIT_SECONDS + " วินาที";
            default:
                return "ทำไม่ได้: ไม่รู้จักคำสั่งนี้";
        }
    }
}
