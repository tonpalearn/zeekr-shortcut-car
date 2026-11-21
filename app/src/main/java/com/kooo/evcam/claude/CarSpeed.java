/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import com.kooo.evcam.telemetry.CarAccess;
import com.kooo.evcam.telemetry.Signal;
import com.kooo.evcam.telemetry.Telemetry;

/** อ่านความเร็วรถ (อ่านอย่างเดียว) ผ่าน telemetry ของ dts88 — อ่านไม่ได้คืน null ไม่โยน error */
final class CarSpeed {

    private CarSpeed() {
    }

    static Float kmh() {
        try {
            CarAccess access = Telemetry.get().carAccess();
            if (access == null) {
                return null;
            }
            Float mps = access.sensor(Signal.SPEED.id);
            return mps == null ? null : Math.abs(mps) * 3.6f;
        } catch (Throwable t) {
            return null;
        }
    }
}
