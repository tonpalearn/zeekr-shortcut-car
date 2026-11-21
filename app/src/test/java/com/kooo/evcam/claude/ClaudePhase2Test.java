/*
 * tpl-zeekr — Claude mode phase 2 tests. Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ClaudePhase2Test {

    // ------------------------------------------------------------ ด่านคุมคำสั่ง

    @Test
    public void safeActionAllowedEvenWhileMoving() {
        assertEquals(ClaudeCarPolicy.Verdict.ALLOW, ClaudeCarPolicy.decide("climate_power", true, true));
        assertEquals(ClaudeCarPolicy.Verdict.ALLOW, ClaudeCarPolicy.decide("seat_heat_driver", false, true));
    }

    @Test
    public void riskyActionNeedsParkAndConfirm() {
        assertEquals(ClaudeCarPolicy.Verdict.REJECT_MOVING, ClaudeCarPolicy.decide("close_all_doors", true, true));
        assertEquals(ClaudeCarPolicy.Verdict.NEEDS_CONFIRM, ClaudeCarPolicy.decide("close_all_doors", false, true));
        assertEquals(ClaudeCarPolicy.Verdict.NEEDS_CONFIRM, ClaudeCarPolicy.decide("central_lock", false, true));
        assertEquals(ClaudeCarPolicy.Verdict.REJECT_MOVING, ClaudeCarPolicy.decide("sentry", true, true));
    }

    @Test
    public void unknownAndExcludedActionsRejected() {
        for (String k : new String[]{"driving_light", "rear_fog_flash", "gear_up", "voice_assistant",
                "toggle_recording", "unlock", "", null}) {
            assertEquals(k, ClaudeCarPolicy.Verdict.REJECT_UNKNOWN, ClaudeCarPolicy.decide(k, false, true));
        }
    }

    @Test
    public void switchOffBlocksEverything() {
        assertEquals(ClaudeCarPolicy.Verdict.REJECT_DISABLED, ClaudeCarPolicy.decide("climate_power", false, false));
    }

    @Test
    public void stepsClampedOnlyForStepwise() {
        assertEquals(5, ClaudeCarPolicy.clampSteps(ClaudeCarPolicy.entry("volume_up"), 99));
        assertEquals(1, ClaudeCarPolicy.clampSteps(ClaudeCarPolicy.entry("volume_up"), -3));
        assertEquals(1, ClaudeCarPolicy.clampSteps(ClaudeCarPolicy.entry("climate_power"), 4));
        assertNull(ClaudeCarPolicy.entry("driving_light"));
    }

    @Test
    public void toolDefinitionEnumMatchesAllowList() {
        JsonObject tool = ClaudeCarPolicy.toolDefinition();
        assertEquals("car_action", tool.get("name").getAsString());
        JsonArray keys = tool.getAsJsonObject("input_schema").getAsJsonObject("properties")
                .getAsJsonObject("action").getAsJsonArray("enum");
        assertEquals(ClaudeCarPolicy.allowed().size(), keys.size());
        assertFalse(keys.toString().contains("driving_light"));
        assertTrue(tool.get("description").getAsString().contains("close_all_doors: ปิดประตูทุกบาน [ยืนยัน]"));
    }

    // ------------------------------------------------------------ คำตอบแบบมีเครื่องมือ

    @Test
    public void parseResponseWithToolUse() throws Exception {
        ClaudeClient.Response r = ClaudeClient.parseResponse("{\"stop_reason\":\"tool_use\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"เปิดแอร์ให้นะครับ\"},"
                + "{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"car_action\",\"input\":{\"action\":\"climate_power\"}}]}");
        assertTrue(r.wantsTools());
        assertEquals("เปิดแอร์ให้นะครับ", r.text());
    }

    @Test
    public void buildRequestIncludesToolsOnlyWhenGiven() {
        JsonArray msgs = new JsonArray();
        JsonArray tools = new JsonArray();
        tools.add(ClaudeCarPolicy.toolDefinition());
        JsonObject with = JsonParser.parseString(ClaudeClient.buildRequest("m", "s", msgs, tools, 100)).getAsJsonObject();
        JsonObject without = JsonParser.parseString(ClaudeClient.buildRequest("m", "s", msgs, null, 100)).getAsJsonObject();
        assertTrue(with.has("tools"));
        assertFalse(without.has("tools"));
    }

    // ------------------------------------------------------------ วงรอบสนทนา

    private static ClaudeClient.Response toolUse(String id, String action) {
        JsonArray c = new JsonArray();
        JsonObject b = new JsonObject();
        b.addProperty("type", "tool_use");
        b.addProperty("id", id);
        b.addProperty("name", "car_action");
        JsonObject in = new JsonObject();
        in.addProperty("action", action);
        b.add("input", in);
        c.add(b);
        return new ClaudeClient.Response("tool_use", c);
    }

    private static ClaudeClient.Response text(String t) {
        JsonArray c = new JsonArray();
        JsonObject b = new JsonObject();
        b.addProperty("type", "text");
        b.addProperty("text", t);
        c.add(b);
        return new ClaudeClient.Response("end_turn", c);
    }

    /** Claude ปลอม: ตอบตามคิว และจดว่าได้รับอะไร */
    private static final class FakeClaude implements ClaudeConversation.Sender {
        final Deque<Object> script = new ArrayDeque<>();
        final List<JsonArray> received = new ArrayList<>();

        @Override
        public ClaudeClient.Response send(JsonArray messages) throws ClaudeClient.Failure {
            received.add(messages);
            Object next = script.poll();
            if (next instanceof ClaudeClient.Failure) {
                throw (ClaudeClient.Failure) next;
            }
            return (ClaudeClient.Response) next;
        }
    }

    @Test
    public void toolLoopRunsActionAndSendsResultBack() throws Exception {
        FakeClaude claude = new FakeClaude();
        claude.script.add(toolUse("t1", "climate_power"));
        claude.script.add(text("เปิดแอร์แล้วครับ"));
        List<String> ran = new ArrayList<>();
        ClaudeConversation conv = new ClaudeConversation();
        String out = conv.ask("เปิดแอร์", claude, (name, input) -> {
            ran.add(input.get("action").getAsString());
            return "สำเร็จ";
        }, null);
        assertEquals("เปิดแอร์แล้วครับ", out);
        assertEquals(List.of("climate_power"), ran);
        // รอบสองต้องมี tool_result ที่ id ตรงกัน
        JsonArray second = claude.received.get(1);
        JsonObject toolResult = second.get(2).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("tool_result", toolResult.get("type").getAsString());
        assertEquals("t1", toolResult.get("tool_use_id").getAsString());
        assertEquals("สำเร็จ", toolResult.get("content").getAsString());
        assertEquals(4, conv.size()); // user, assistant(tool_use), user(tool_result), assistant(text)
    }

    @Test
    public void failureRollsBackSoHistoryStaysValid() throws Exception {
        FakeClaude claude = new FakeClaude();
        claude.script.add(text("สวัสดีครับ"));
        ClaudeConversation conv = new ClaudeConversation();
        conv.ask("สวัสดี", claude, null, null);
        claude.script.add(toolUse("t2", "climate_power"));
        claude.script.add(new ClaudeClient.Failure(0, "ต่ออินเทอร์เน็ตไม่ได้", "x"));
        try {
            conv.ask("เปิดแอร์", claude, (n, i) -> "สำเร็จ", null);
            fail();
        } catch (ClaudeClient.Failure expected) {
            assertEquals(2, conv.size());
        }
    }

    @Test
    public void endlessToolRequestsStopAndRollBack() throws Exception {
        FakeClaude claude = new FakeClaude();
        for (int i = 0; i <= ClaudeConversation.MAX_ROUNDS; i++) {
            claude.script.add(toolUse("t" + i, "volume_up"));
        }
        int[] runs = {0};
        ClaudeConversation conv = new ClaudeConversation();
        String out = conv.ask("เพิ่มเสียง", claude, (n, i) -> {
            runs[0]++;
            return "สำเร็จ";
        }, null);
        assertEquals(ClaudeConversation.MAX_ROUNDS, runs[0]);
        assertEquals(0, conv.size());
        assertTrue(out.contains("ไม่จบ"));
    }

    @Test
    public void trimKeepsHistoryStartingWithPlainUser() throws Exception {
        FakeClaude claude = new FakeClaude();
        ClaudeConversation conv = new ClaudeConversation();
        for (int i = 0; i < 12; i++) {
            claude.script.add(toolUse("a" + i, "media_next"));
            claude.script.add(text("ข้ามเพลงแล้ว"));
            conv.ask("ข้ามเพลง " + i, claude, (n, in) -> "สำเร็จ", null);
        }
        assertTrue(conv.size() <= ClaudeConversation.MAX_MESSAGES);
        assertTrue(ClaudeConversation.isPlainUser(conv.snapshot().get(0).getAsJsonObject()));
    }
}
