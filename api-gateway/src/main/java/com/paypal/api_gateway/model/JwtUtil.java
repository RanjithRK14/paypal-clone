package com.paypal.api_gateway.model;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Manual JWT validator using standard Java crypto only.
 * No jjwt, no Jackson, no external dependencies — zero chance of UnsupportedOperationException.
 */
public class JwtUtil {

    private static final String SECRET =
            "secret123secret123secret123secret123secret123secret123";

    /**
     * Validates signature + expiry, returns claims map.
     * Throws IllegalArgumentException on any failure.
     */
    public static Map<String, Object> validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is empty");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Bad JWT structure");
        }

        // 1. Verify HMAC-SHA256 signature
        byte[] expectedSig = hmacSha256(
                (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8),
                SECRET.getBytes(StandardCharsets.UTF_8));
        byte[] actualSig = base64UrlDecode(parts[2]);

        if (!slowEquals(expectedSig, actualSig)) {
            throw new IllegalArgumentException("JWT signature mismatch");
        }

        // 2. Parse payload (pure manual JSON - no Jackson)
        byte[] payloadBytes = base64UrlDecode(parts[1]);
        String json = new String(payloadBytes, StandardCharsets.UTF_8);
        Map<String, Object> claims = parseJson(json);

        // 3. Check expiry
        Object exp = claims.get("exp");
        if (exp instanceof Number) {
            long expSec = ((Number) exp).longValue();
            if (System.currentTimeMillis() / 1000 > expSec) {
                throw new IllegalArgumentException("JWT expired");
            }
        }

        return claims;
    }

    // ── HMAC-SHA256 ──────────────────────────────────────────────────────

    private static byte[] hmacSha256(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    private static byte[] base64UrlDecode(String s) {
        int pad = (4 - s.length() % 4) % 4;
        return Base64.getUrlDecoder().decode(s + "=".repeat(pad));
    }

    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= a[i] ^ b[i];
        return r == 0;
    }

    // ── Minimal JSON parser (handles flat objects with string/number values) ──

    /**
     * Parses a flat JSON object like {"sub":"x","userId":1,"role":"R","iat":0,"exp":0}.
     * Handles strings, integers, and longs. No nesting needed for JWT claims.
     */
    static Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new HashMap<>();
        // Strip outer braces
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}"))   s = s.substring(0, s.length() - 1);

        // Split on commas that are NOT inside strings
        // Simple approach: iterate char by char
        boolean inString = false;
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            char c = i < s.length() ? s.charAt(i) : ',';
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            if (c == ',' && !inString) {
                parsePair(s.substring(start, i).trim(), map);
                start = i + 1;
            }
        }
        return map;
    }

    private static void parsePair(String pair, Map<String, Object> map) {
        int colon = pair.indexOf(':');
        if (colon < 0) return;
        String rawKey = pair.substring(0, colon).trim();
        String rawVal = pair.substring(colon + 1).trim();
        // Remove quotes from key
        String key = rawKey.replaceAll("^\"|\"$", "");
        // Parse value
        if (rawVal.startsWith("\"")) {
            // String value
            map.put(key, rawVal.substring(1, rawVal.length() - 1));
        } else if (rawVal.equals("true"))  {
            map.put(key, Boolean.TRUE);
        } else if (rawVal.equals("false")) {
            map.put(key, Boolean.FALSE);
        } else if (rawVal.equals("null"))  {
            map.put(key, null);
        } else {
            // Number
            try {
                if (rawVal.contains(".")) map.put(key, Double.parseDouble(rawVal));
                else                      map.put(key, Long.parseLong(rawVal));
            } catch (NumberFormatException e) {
                map.put(key, rawVal);
            }
        }
    }
}
