/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.button;

import android.content.Context;

import com.kooo.evcam.camera.CameraKeeper;

import java.util.function.Consumer;

/**
 * ทางเข้าคำสั่งรถของ dts88 สำหรับโหมด Claude (อยู่ในแพ็กเกจ button เพื่อเข้าถึงงานของ {@link ButtonAction} ได้
 * โดยไม่ต้องแก้ไฟล์ของ dts88).
 *
 * <p><b>ไม่ตัดสินว่าทำได้ไหม</b> — ด่านนั้นคือ {@code claude.ClaudeCarPolicy}. ที่นี่แค่ทำแล้วคืนผลจริง:
 * ชื่อของ {@link CarMoves.Result} (DONE, NO_CHANGE, UNAVAILABLE, POWER_SAVING, MOVING, NOT_FITTED)
 * หรือ SENT (ส่งคำสั่งแล้วแต่ไม่มีผลอ่านกลับ: โหมดขับ, ถ่ายรูป) หรือ UNKNOWN.
 * กล่องดำของแอปจดเป็น "灵动贴动作 claude:&lt;key&gt;"</p>
 */
public final class ClaudeCarBridge {

    public static final String SENT = "SENT";
    public static final String UNKNOWN = "UNKNOWN";

    private ClaudeCarBridge() {
    }

    public static ButtonAction find(String key) {
        if (key == null) {
            return null;
        }
        for (ButtonAction a : ButtonAction.all()) {
            if (key.equals(a.key)) {
                return a;
            }
        }
        return null;
    }

    /** เรียกบนเธรดหลัก; {@code done} ถูกเรียกบนเธรดหลักเช่นกัน */
    public static void perform(Context context, String key, int steps, Consumer<String> done) {
        Context app = context.getApplicationContext();
        ButtonAction a = find(key);
        if (a == null) {
            done.accept(UNKNOWN);
            return;
        }
        int n = a.stepwise() ? Math.max(1, Math.min(ButtonActions.MAX_STEPS, steps)) : 1;
        String logKey = "claude:" + key;
        if (a.stepCar != null) {
            CarMoves.run(app, CameraKeeper.Why.BUTTON, logKey, a.stepCar.apply(n), r -> done.accept(r.name()));
        } else if (a.car != null) {
            CarMoves.run(app, CameraKeeper.Why.BUTTON, logKey, a.car, r -> done.accept(r.name()));
        } else if (a.media != null) {
            for (int i = 0; i < n; i++) {
                if (!a.media.perform(app)) {
                    done.accept(CarMoves.Result.UNAVAILABLE.name());
                    return;
                }
            }
            done.accept(CarMoves.Result.DONE.name());
        } else {
            // โหมดขับ, งานของแอป (ถ่ายรูป ล็อกคลิป): ใช้ทางเดียวกับปุ่ม ไม่มีผลอ่านกลับ
            ButtonActions.perform(app, a, CameraKeeper.Why.BUTTON, n);
            done.accept(SENT);
        }
    }
}
