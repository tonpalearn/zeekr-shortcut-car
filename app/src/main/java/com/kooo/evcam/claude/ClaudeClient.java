/*
 * tpl-zeekr — modified version of Zeekr Shortcut (https://github.com/dts88/zeekr-shortcut-car)
 * Claude mode: Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * เรียก Claude Messages API ตรง ๆ ด้วย HttpURLConnection (ไม่เพิ่มไลบรารี).
 *
 * <p>ส่วนสร้างคำขอ ({@link #buildBody}) กับส่วนอ่านคำตอบ ({@link #parseReply}, {@link #parseError})
 * ไม่แตะเน็ตและไม่แตะ Android จึงทดสอบแยกได้ (ClaudeClientTest)</p>
 *
 * <p>ต้องเรียกจาก thread พื้นหลังเท่านั้น</p>
 */
public final class ClaudeClient {

    public static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String API_VERSION = "2023-06-01";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 90000;
    /** กันคำตอบยาวไม่จบจนกินหน่วยความจำ */
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    /** ข้อความหนึ่งรอบในบทสนทนา: role เป็น "user" หรือ "assistant" */
    public static final class Turn {
        public final String role;
        public final String text;

        public Turn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    /** เรียกไม่สำเร็จ: เก็บรหัส HTTP (0 = ต่อเน็ตไม่ได้) กับข้อความภาษาไทยที่จะบอกคนขับ */
    public static final class Failure extends IOException {
        public final int httpCode;
        public final String thaiMessage;

        public Failure(int httpCode, String thaiMessage, String detail) {
            super("HTTP " + httpCode + ": " + detail);
            this.httpCode = httpCode;
            this.thaiMessage = thaiMessage;
        }
    }

    private ClaudeClient() {
    }

    /** ส่งบทสนทนาทั้งหมด แล้วคืนข้อความตอบของ Claude */
    public static String ask(String apiKey, String model, String system, List<Turn> history, int maxTokens)
            throws Failure {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Failure(401, "ยังไม่ได้ใส่กุญแจ Claude API", "missing key");
        }
        byte[] body = buildBody(model, system, history, maxTokens).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("content-type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey.trim());
            conn.setRequestProperty("anthropic-version", API_VERSION);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = in == null ? "" : readAll(in);
            if (code >= 200 && code < 300) {
                return parseReply(text);
            }
            throw parseError(code, text);
        } catch (Failure f) {
            throw f;
        } catch (IOException e) {
            throw new Failure(0, "ต่ออินเทอร์เน็ตไม่ได้ ลองใหม่อีกครั้ง", String.valueOf(e.getMessage()));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** JSON ที่ส่งให้ API */
    public static String buildBody(String model, String system, List<Turn> history, int maxTokens) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("max_tokens", maxTokens);
        if (system != null && !system.isEmpty()) {
            root.addProperty("system", system);
        }
        JsonArray messages = new JsonArray();
        for (Turn turn : history) {
            JsonObject m = new JsonObject();
            m.addProperty("role", turn.role);
            m.addProperty("content", turn.text);
            messages.add(m);
        }
        root.add("messages", messages);
        return root.toString();
    }

    /** ต่อทุกบล็อก type=text ในคำตอบเข้าด้วยกัน */
    public static String parseReply(String json) throws Failure {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            StringBuilder sb = new StringBuilder();
            if (content != null) {
                for (JsonElement el : content) {
                    JsonObject block = el.getAsJsonObject();
                    if (block.has("type") && "text".equals(block.get("type").getAsString()) && block.has("text")) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(block.get("text").getAsString());
                    }
                }
            }
            String text = sb.toString().trim();
            if (text.isEmpty()) {
                throw new Failure(200, "Claude ไม่ได้ตอบอะไรกลับมา", "empty content");
            }
            return text;
        } catch (Failure f) {
            throw f;
        } catch (RuntimeException e) {
            throw new Failure(200, "อ่านคำตอบของ Claude ไม่ได้", String.valueOf(e.getMessage()));
        }
    }

    /** แปลงรหัส HTTP + เนื้อ error เป็นข้อความไทยสั้น ๆ ที่พูดออกเสียงได้ */
    public static Failure parseError(int code, String json) {
        String detail = json == null ? "" : json;
        try {
            JsonObject err = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("error");
            if (err != null && err.has("message")) {
                detail = err.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // เนื้อไม่ใช่ JSON ก็ใช้ข้อความดิบ
        }
        String thai;
        switch (code) {
            case 400:
                thai = "คำขอไม่ถูกต้อง อาจเลือกรุ่น Claude ที่ใช้ไม่ได้";
                break;
            case 401:
                thai = "กุญแจ Claude API ไม่ถูกต้อง";
                break;
            case 403:
                thai = "กุญแจนี้ไม่มีสิทธิ์ใช้งาน";
                break;
            case 404:
                thai = "ไม่พบรุ่น Claude ที่เลือก";
                break;
            case 429:
                thai = "เรียกถี่เกินหรือเครดิตหมด รอสักครู่แล้วลองใหม่";
                break;
            case 529:
                thai = "Claude คนใช้เยอะอยู่ ลองใหม่อีกครั้ง";
                break;
            default:
                thai = code >= 500 ? "ระบบของ Claude ขัดข้อง ลองใหม่อีกครั้ง" : "เรียก Claude ไม่สำเร็จ";
        }
        return new Failure(code, thai, detail);
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY_BYTES) {
                    throw new IOException("response too large");
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
