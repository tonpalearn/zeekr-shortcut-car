/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import java.util.Locale;

/**
 * System prompt ของ Claude บนจอรถ + กติกาตอนรถวิ่ง. ไม่แตะ Android จึงทดสอบได้
 */
public final class ClaudePrompt {

    /** เกินความเร็วนี้ (กม./ชม.) ถือว่ารถวิ่ง: ตอบสั้น ปิดแป้นพิมพ์ */
    public static final float MOVING_KMH = 5f;

    public static final int MAX_TOKENS_MOVING = 400;
    public static final int MAX_TOKENS_PARKED = 1500;

    private ClaudePrompt() {
    }

    /** speedKmh = null แปลว่าอ่านความเร็วไม่ได้ */
    public static boolean isMoving(Float speedKmh) {
        return speedKmh != null && speedKmh > MOVING_KMH;
    }

    public static String system(Float speedKmh, String localTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("คุณคือ Claude ผู้ช่วยบนจอกลางรถยนต์ไฟฟ้า Zeekr 7X ของผู้ใช้ (แอป tpl-zeekr). ");
        sb.append("ผู้ใช้มักพูดถามด้วยเสียง และคำตอบของคุณจะถูกอ่านออกเสียง ");
        sb.append("จึงต้องตอบเป็นภาษาพูดธรรมดา ห้ามใช้ markdown ตาราง หัวข้อ สัญลักษณ์ * # หรืออีโมจิ ");
        sb.append("ตอบภาษาเดียวกับที่ผู้ใช้ถาม (ปกติคือภาษาไทย). ");
        sb.append("ถ้าไม่แน่ใจหรือไม่มีข้อมูลสด เช่น สภาพจราจรตอนนี้ ให้บอกตรง ๆ ว่าไม่รู้ อย่าเดา. ");
        sb.append("ตอนนี้คุณยังสั่งงานรถ เปิดแอร์ หรือนำทางให้ไม่ได้ ถ้าถูกขอให้บอกสั้น ๆ ว่ายังทำไม่ได้. ");
        if (localTime != null && !localTime.isEmpty()) {
            sb.append("เวลาตอนนี้ ").append(localTime).append(". ");
        }
        if (isMoving(speedKmh)) {
            sb.append(String.format(Locale.US, "รถกำลังวิ่งอยู่ที่ประมาณ %.0f กม./ชม. ", speedKmh));
            sb.append("ตอบให้สั้นที่สุด ไม่เกิน 2 ถึง 3 ประโยค และห้ามแนะนำให้คนขับมองจอ พิมพ์ หรืออ่านอะไรยาว ๆ ระหว่างขับ.");
        } else if (speedKmh != null) {
            sb.append("รถจอดอยู่ ตอบละเอียดได้ตามที่จำเป็น แต่ยังควรกระชับ.");
        } else {
            sb.append("ไม่รู้ว่ารถวิ่งหรือจอด ให้ตอบกระชับไว้ก่อน.");
        }
        return sb.toString();
    }

    /** ตัดสัญลักษณ์ markdown ก่อนส่งให้เสียงอ่าน (เผื่อ Claude เผลอใส่มา) */
    public static String forSpeech(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[*#`_>|]", "")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
