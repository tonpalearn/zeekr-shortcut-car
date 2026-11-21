/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * บทสนทนากับ Claude แบบมีเครื่องมือ (tool use) — วนรอบ: ส่ง → Claude ขอใช้เครื่องมือ → ทำ → ส่งผลกลับ → จน Claude ตอบจบ.
 *
 * <p>ไม่แตะ Android: ตัวส่ง ({@link Sender}) กับตัวทำเครื่องมือ ({@link ToolRunner}) ฉีดเข้ามา จึงทดสอบด้วย Claude ปลอมได้
 * (ClaudeConversationTest). เรียกจาก thread พื้นหลังเท่านั้น</p>
 */
public final class ClaudeConversation {

    /** Claude ขอใช้เครื่องมือต่อกันได้กี่รอบในคำถามเดียว */
    public static final int MAX_ROUNDS = 4;
    /** เก็บข้อความย้อนหลังได้กี่ชิ้น (นับทั้ง user และ assistant) */
    public static final int MAX_MESSAGES = 30;

    public interface Sender {
        ClaudeClient.Response send(JsonArray messages) throws ClaudeClient.Failure;
    }

    public interface ToolRunner {
        /** @return ผลเป็นข้อความสั้น ๆ ที่ส่งกลับให้ Claude */
        String run(String name, JsonObject input);
    }

    public interface Listener {
        /** Claude พูดอะไรออกมาระหว่างทาง (ก่อน/หลังใช้เครื่องมือ) */
        void onText(String text);
    }

    private final List<JsonObject> messages = new ArrayList<>();

    /**
     * ถามหนึ่งครั้ง (อาจมีหลายรอบเครื่องมือข้างใน)
     *
     * @return ข้อความสุดท้ายของ Claude
     */
    public synchronized String ask(String userText, Sender sender, ToolRunner tools, Listener listener)
            throws ClaudeClient.Failure {
        int mark = messages.size();
        messages.add(plainUser(userText));
        String last = "";
        try {
            for (int round = 0; round <= MAX_ROUNDS; round++) {
                ClaudeClient.Response r = sender.send(snapshot());
                messages.add(message("assistant", r.content));
                String text = r.text();
                if (!text.isEmpty()) {
                    last = text;
                    if (listener != null) {
                        listener.onText(text);
                    }
                }
                if (!r.wantsTools()) {
                    trim();
                    return last;
                }
                if (round == MAX_ROUNDS) {
                    break;
                }
                messages.add(message("user", runTools(r.content, tools)));
            }
            // ขอเครื่องมือไม่หยุด: ถอนรอบนี้ทิ้ง ให้ประวัติถูกรูปแบบเสมอ
            rollback(mark);
            return last.isEmpty() ? "ขอโทษครับ ทำคำสั่งนี้ไม่จบ ลองพูดใหม่ให้สั้นลง" : last;
        } catch (ClaudeClient.Failure f) {
            rollback(mark);
            throw f;
        }
    }

    public synchronized void clear() {
        messages.clear();
    }

    public synchronized int size() {
        return messages.size();
    }

    /** สำเนาประวัติในรูป API (ทดสอบ/ส่ง) */
    public synchronized JsonArray snapshot() {
        JsonArray arr = new JsonArray();
        for (JsonObject m : messages) {
            arr.add(m.deepCopy());
        }
        return arr;
    }

    private static JsonArray runTools(JsonArray content, ToolRunner tools) {
        JsonArray results = new JsonArray();
        for (JsonElement el : content) {
            JsonObject b = el.getAsJsonObject();
            if (!b.has("type") || !"tool_use".equals(b.get("type").getAsString())) {
                continue;
            }
            String id = b.get("id").getAsString();
            String name = b.has("name") ? b.get("name").getAsString() : "";
            JsonObject input = b.has("input") && b.get("input").isJsonObject()
                    ? b.getAsJsonObject("input") : new JsonObject();
            String out;
            try {
                out = tools == null ? "ไม่มีเครื่องมือนี้" : tools.run(name, input);
            } catch (RuntimeException e) {
                out = "ทำไม่สำเร็จ: " + e.getMessage();
            }
            JsonObject result = new JsonObject();
            result.addProperty("type", "tool_result");
            result.addProperty("tool_use_id", id);
            result.addProperty("content", out == null ? "" : out);
            results.add(result);
        }
        return results;
    }

    private void rollback(int mark) {
        while (messages.size() > mark) {
            messages.remove(messages.size() - 1);
        }
    }

    /** ตัดของเก่าทิ้ง โดยให้ข้อความแรกเป็นคำถามธรรมดาของผู้ใช้เสมอ (ไม่ตัดกลางคู่ tool_use/tool_result) */
    private void trim() {
        if (messages.size() <= MAX_MESSAGES) {
            return;
        }
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
        while (!messages.isEmpty() && !isPlainUser(messages.get(0))) {
            messages.remove(0);
        }
    }

    static boolean isPlainUser(JsonObject m) {
        return "user".equals(m.get("role").getAsString()) && m.get("content").isJsonPrimitive();
    }

    private static JsonObject plainUser(String text) {
        JsonObject m = new JsonObject();
        m.addProperty("role", "user");
        m.addProperty("content", text);
        return m;
    }

    private static JsonObject message(String role, JsonArray content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.add("content", content);
        return m;
    }
}
