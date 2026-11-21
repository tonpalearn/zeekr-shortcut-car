/*
 * tpl-zeekr — Claude mode tests. Copyright (C) 2026 TONPALEARN
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kooo.evcam.claude;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Arrays;

public class ClaudeClientTest {

    @Test
    public void buildBodyHasModelSystemAndMessagesInOrder() {
        String json = ClaudeClient.buildBody("claude-sonnet-5-5", "sys", Arrays.asList(
                new ClaudeClient.Turn("user", "สวัสดี"),
                new ClaudeClient.Turn("assistant", "สวัสดีครับ"),
                new ClaudeClient.Turn("user", "วันนี้วันอะไร")), 400);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("claude-sonnet-5-5", root.get("model").getAsString());
        assertEquals(400, root.get("max_tokens").getAsInt());
        assertEquals("sys", root.get("system").getAsString());
        assertEquals(3, root.getAsJsonArray("messages").size());
        assertEquals("assistant", root.getAsJsonArray("messages").get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("วันนี้วันอะไร",
                root.getAsJsonArray("messages").get(2).getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void parseReplyJoinsTextBlocksAndSkipsOthers() throws Exception {
        String json = "{\"content\":[{\"type\":\"text\",\"text\":\"บรรทัดแรก\"},"
                + "{\"type\":\"tool_use\",\"id\":\"x\"},{\"type\":\"text\",\"text\":\"บรรทัดสอง\"}]}";
        assertEquals("บรรทัดแรก\nบรรทัดสอง", ClaudeClient.parseReply(json));
    }

    @Test
    public void parseReplyEmptyIsFailure() {
        try {
            ClaudeClient.parseReply("{\"content\":[]}");
            fail();
        } catch (ClaudeClient.Failure f) {
            assertEquals(200, f.httpCode);
        }
    }

    @Test
    public void parseErrorMapsCodesToThai() {
        ClaudeClient.Failure f = ClaudeClient.parseError(401,
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");
        assertEquals(401, f.httpCode);
        assertEquals("กุญแจ Claude API ไม่ถูกต้อง", f.thaiMessage);
        assertTrue(f.getMessage().contains("invalid x-api-key"));
        assertEquals("Claude คนใช้เยอะอยู่ ลองใหม่อีกครั้ง", ClaudeClient.parseError(529, "not json").thaiMessage);
    }

    @Test
    public void missingKeyFailsWithoutNetwork() {
        try {
            ClaudeClient.ask("  ", "m", "s", Arrays.asList(new ClaudeClient.Turn("user", "x")), 10);
            fail();
        } catch (ClaudeClient.Failure f) {
            assertEquals(401, f.httpCode);
        }
    }

    @Test
    public void movingPromptIsShortAndParkedIsNot() {
        assertTrue(ClaudePrompt.isMoving(60f));
        assertFalse(ClaudePrompt.isMoving(0f));
        assertFalse(ClaudePrompt.isMoving(null));
        assertTrue(ClaudePrompt.system(60f, "10:00").contains("ไม่เกิน 2 ถึง 3 ประโยค"));
        assertFalse(ClaudePrompt.system(0f, "10:00").contains("ไม่เกิน 2 ถึง 3 ประโยค"));
    }

    @Test
    public void speechStripsMarkdown() {
        assertEquals("หัวข้อ ตัวหนา ลิงก์", ClaudePrompt.forSpeech("# หัวข้อ\n**ตัวหนา** [ลิงก์](https://x.y)"));
    }

    @Test
    public void maskedKeepsLastFour() {
        assertEquals("••••wxyz", ClaudePrefs.masked("sk-ant-abcwxyz"));
        assertEquals("", ClaudePrefs.masked(" "));
    }
}
