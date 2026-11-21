/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * รายการคำสั่งรถที่ Claude เรียกได้ (เฟส 2) — <b>ด่านเดียวที่ตัดสินว่าทำได้ไหม</b>, ไม่แตะ Android จึงทดสอบได้.
 *
 * <p>คีย์ตรงกับ {@code button.ButtonAction.key} ของ dts88. อะไรที่ไม่อยู่ที่นี่ Claude มองไม่เห็นและเรียกไม่ได้</p>
 */
public final class ClaudeCarPolicy {

    public static final String TOOL_NAME = "car_action";
    public static final int MAX_STEPS = 5;

    /** ระดับของคำสั่ง */
    public enum Level {
        /** ทำทันที */
        NOW,
        /** ต้องจอด + ผู้ใช้กดยืนยันบนจอเอง */
        CONFIRM_PARKED
    }

    /** ผลการตัดสิน */
    public enum Verdict {
        ALLOW, NEEDS_CONFIRM, REJECT_UNKNOWN, REJECT_MOVING, REJECT_DISABLED
    }

    public static final class Entry {
        public final String key;
        public final Level level;
        public final String thai;
        /** ขึ้น/ลงทีละขั้น (steps ใช้ได้) */
        public final boolean stepwise;

        Entry(String key, Level level, String thai, boolean stepwise) {
            this.key = key;
            this.level = level;
            this.thai = thai;
            this.stepwise = stepwise;
        }
    }

    private static final Map<String, Entry> ALLOWED = build();

    private ClaudeCarPolicy() {
    }

    private static Map<String, Entry> build() {
        Map<String, Entry> m = new LinkedHashMap<>();
        // ---- ทำทันที
        now(m, "drive_mode_comfort", "โหมดขับ Comfort");
        now(m, "drive_mode_standard", "โหมดขับ Standard");
        now(m, "drive_mode_sport", "โหมดขับ Sport");
        step(m, "drive_mode_up", "โหมดขับขึ้นทีละระดับ");
        step(m, "drive_mode_down", "โหมดขับลงทีละระดับ");
        now(m, "climate_power", "เปิด/ปิดแอร์ (สลับ)");
        step(m, "climate_temp_up", "อุณหภูมิแอร์ขึ้นทีละขั้น");
        step(m, "climate_temp_down", "อุณหภูมิแอร์ลงทีละขั้น");
        now(m, "climate_circulation", "สลับโหมดหมุนเวียนอากาศ");
        now(m, "defrost_front", "ไล่ฝ้ากระจกหน้า (สลับ)");
        now(m, "defrost_rear", "ไล่ฝ้ากระจกหลัง (สลับ)");
        now(m, "steering_wheel_heat", "อุ่นพวงมาลัย (สลับ)");
        now(m, "seat_vent_driver", "ระบายอากาศเบาะคนขับ (ไล่ระดับ)");
        now(m, "seat_vent_passenger", "ระบายอากาศเบาะผู้โดยสารหน้า (ไล่ระดับ)");
        now(m, "seat_heat_driver", "อุ่นเบาะคนขับ (ไล่ระดับ)");
        now(m, "seat_heat_passenger", "อุ่นเบาะผู้โดยสารหน้า (ไล่ระดับ)");
        now(m, "seat_heat_rear_left", "อุ่นเบาะหลังซ้าย (ไล่ระดับ)");
        now(m, "seat_heat_rear_right", "อุ่นเบาะหลังขวา (ไล่ระดับ)");
        now(m, "seat_heat_rear", "อุ่นเบาะหลังทั้งแถว");
        now(m, "seat_massage_driver", "นวดเบาะคนขับ (สลับ)");
        now(m, "seat_massage_passenger", "นวดเบาะผู้โดยสารหน้า (สลับ)");
        now(m, "media_play_pause", "เล่น/หยุดเพลง");
        step(m, "media_previous", "ย้อนเพลงทีละเพลง");
        step(m, "media_next", "ข้ามเพลงทีละเพลง");
        now(m, "media_mute", "ปิด/เปิดเสียง");
        step(m, "volume_up", "เพิ่มเสียงทีละขั้น");
        step(m, "volume_down", "ลดเสียงทีละขั้น");
        now(m, "fragrance", "น้ำหอมในรถ (สลับ)");
        now(m, "view_360", "เปิดกล้อง 360 ของรถ");
        now(m, "welcome_light", "ไฟต้อนรับ (สลับ)");
        now(m, "charging_light", "ไฟชาร์จ (สลับ)");
        now(m, "take_photo", "ถ่ายรูปจากกล้องทุกตัว (แอป tpl-zeekr)");
        now(m, "lock_footage", "ล็อกคลิปเหตุการณ์ 10 วินาทีก่อน-หลัง (แอป tpl-zeekr)");
        // ---- ต้องจอด + กดยืนยันบนจอ
        confirm(m, "central_lock", "ล็อกรถ");
        confirm(m, "sentry", "โหมดเฝ้ารถ Sentry (สลับ)");
        confirm(m, "camping", "โหมดแคมปิ้ง");
        confirm(m, "theater_driver", "โหมดโรงหนังฝั่งคนขับ");
        confirm(m, "theater_passenger", "โหมดโรงหนังฝั่งผู้โดยสาร");
        confirm(m, "seat_restore", "คืนตำแหน่งเบาะ");
        confirm(m, "close_all_doors", "ปิดประตูทุกบาน");
        confirm(m, "close_driver_door", "ปิดประตูคนขับ");
        confirm(m, "close_passenger_door", "ปิดประตูผู้โดยสารหน้า");
        confirm(m, "close_rear_left_door", "ปิดประตูหลังซ้าย");
        confirm(m, "close_rear_right_door", "ปิดประตูหลังขวา");
        confirm(m, "close_tailgate", "ปิดฝาท้าย");
        confirm(m, "mirror_fold", "พับ/กางกระจกมองข้าง");
        confirm(m, "charging_cap", "เปิด/ปิดฝาช่องชาร์จ");
        return Collections.unmodifiableMap(m);
    }

    private static void now(Map<String, Entry> m, String key, String thai) {
        m.put(key, new Entry(key, Level.NOW, thai, false));
    }

    private static void step(Map<String, Entry> m, String key, String thai) {
        m.put(key, new Entry(key, Level.NOW, thai, true));
    }

    private static void confirm(Map<String, Entry> m, String key, String thai) {
        m.put(key, new Entry(key, Level.CONFIRM_PARKED, thai, false));
    }

    public static Map<String, Entry> allowed() {
        return ALLOWED;
    }

    public static Entry entry(String key) {
        return key == null ? null : ALLOWED.get(key);
    }

    /**
     * @param moving  true = รถวิ่ง (หรือไม่แน่ใจว่าจอด — ผู้เรียกส่ง true เมื่ออ่านความเร็วไม่ได้สำหรับคำสั่งยืนยัน)
     * @param enabled สวิตช์ "ให้ Claude สั่งรถได้"
     */
    public static Verdict decide(String key, boolean moving, boolean enabled) {
        if (!enabled) {
            return Verdict.REJECT_DISABLED;
        }
        Entry e = entry(key);
        if (e == null) {
            return Verdict.REJECT_UNKNOWN;
        }
        if (e.level == Level.CONFIRM_PARKED) {
            return moving ? Verdict.REJECT_MOVING : Verdict.NEEDS_CONFIRM;
        }
        return Verdict.ALLOW;
    }

    public static int clampSteps(Entry e, int steps) {
        if (e == null || !e.stepwise) {
            return 1;
        }
        return Math.max(1, Math.min(MAX_STEPS, steps));
    }

    /** นิยามเครื่องมือส่งให้ Claude API (enum = รายการอนุญาตเท่านั้น) */
    public static JsonObject toolDefinition() {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", TOOL_NAME);
        StringBuilder desc = new StringBuilder();
        desc.append("สั่งงานรถ Zeekr 7X ของผู้ใช้หนึ่งคำสั่ง ใช้เฉพาะเมื่อผู้ใช้ขอชัดเจน ห้ามสั่งเองโดยไม่ได้ขอ. ");
        desc.append("ผลที่ได้คือผลจริงจากรถ ให้บอกผู้ใช้ตามนั้น อย่าอ้างว่าสำเร็จถ้าผลไม่ใช่สำเร็จ. ");
        desc.append("คำสั่งที่ลงท้ายด้วย [ยืนยัน] ทำได้เฉพาะตอนจอด และแอปจะขึ้นปุ่มให้ผู้ใช้กดยืนยันบนจอเอง. ");
        desc.append("คำสั่งที่มีอยู่:\n");
        JsonArray keys = new JsonArray();
        for (Entry e : ALLOWED.values()) {
            keys.add(e.key);
            desc.append("- ").append(e.key).append(": ").append(e.thai)
                    .append(e.level == Level.CONFIRM_PARKED ? " [ยืนยัน]" : "")
                    .append(e.stepwise ? " (ใส่ steps 1-" + MAX_STEPS + " ได้)" : "")
                    .append('\n');
        }
        tool.addProperty("description", desc.toString().trim());

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        action.add("enum", keys);
        action.addProperty("description", "คีย์ของคำสั่ง");
        props.add("action", action);
        JsonObject steps = new JsonObject();
        steps.addProperty("type", "integer");
        steps.addProperty("minimum", 1);
        steps.addProperty("maximum", MAX_STEPS);
        steps.addProperty("description", "จำนวนขั้น ใช้กับคำสั่งทีละขั้นเท่านั้น (ค่าเริ่มต้น 1)");
        props.add("steps", steps);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("action");
        schema.add("required", required);
        tool.add("input_schema", schema);
        return tool;
    }
}
