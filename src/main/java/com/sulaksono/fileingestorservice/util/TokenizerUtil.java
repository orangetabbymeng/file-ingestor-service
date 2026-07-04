package com.sulaksono.fileingestorservice.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

/**
 * Thin wrapper around jtokkit's cl100k_base encoding.
 */
public final class TokenizerUtil {

    private static final Encoding ENC = Encodings
            .newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    private TokenizerUtil() {
    }

    /**
     * Token count using JTokkit's fast path.
     */
    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        return ENC.countTokens(text);
    }

    /**
     * Returns the decoded text of tokens[beginInclusive, endExclusive).
     * - null or empty text returns empty string
     * - negative begin is treated as 0
     * - end greater than token count is capped
     * - begin greater than token count returns empty string
     * - end less than or equal to begin returns empty string
     */
    public static String slice(String text,
                               int beginInclusive,
                               int endExclusive) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        IntArrayList allTokens = ENC.encode(text);
        int tokenCount = allTokens.size();

        int from = Math.max(0, beginInclusive);
        int to = Math.min(endExclusive, tokenCount);

        if (from >= tokenCount || to <= from) {
            return "";
        }

        IntArrayList sub = new IntArrayList(to - from);

        for (int i = from; i < to; i++) {
            sub.add(allTokens.get(i));
        }

        return ENC.decode(sub);
    }
}