/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import android.content.Context;

import com.kooo.evcam.telemetry.Readings;
import com.kooo.evcam.telemetry.Signal;
import com.kooo.evcam.telemetry.SignalText;
import com.kooo.evcam.telemetry.Telemetry;

import java.util.Locale;

/**
 * "สภาพรถตอนนี้" แบบข้อความ ส่งให้ Claude ไปกับทุกคำถาม — อ่านจาก telemetry ของ dts88 (อ่านอย่างเดียว).
 * เอาเฉพาะสัญญาณที่ dts88 ยืนยันแล้ว (CONFIRMED / PROVISIONAL) และมีค่าอยู่จริง
 */
final class ClaudeCarStatus {

    private ClaudeCarStatus() {
    }

    /** @return ข้อความหลายบรรทัด หรือ "" ถ้าอ่านรถไม่ได้ (เช่น ไม่ได้อยู่บนรถ) */
    static String describe(Context context) {
        StringBuilder sb = new StringBuilder();
        Float kmh = CarSpeed.kmh();
        if (kmh != null) {
            sb.append(String.format(Locale.US, "- ความเร็ว: %.0f กม./ชม.\n", kmh));
        }
        try {
            Readings readings = Telemetry.get().readings();
            for (Signal s : Signal.values()) {
                if (s.trust == Signal.Trust.UNVERIFIED || s == Signal.SPEED) {
                    continue;
                }
                Object v = readings.get(s);
                if (v == null) {
                    continue;
                }
                String text = SignalText.text(context, s, v);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                sb.append("- ").append(context.getString(s.labelRes)).append(": ").append(text).append('\n');
            }
        } catch (Throwable t) {
            // อ่านไม่ได้ก็ส่งเท่าที่มี
        }
        return sb.toString().trim();
    }
}
