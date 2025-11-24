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

    private TokenizerUtil() { }

    /** Token count using JTokkit’s fast path. */
    public static int countTokens(String text) {
        return ENC.countTokens(text);
    }

    /**
     * Returns the decoded text of {@code tokens[begin, end)}.
     */
    public static String slice(String text,
                               int beginInclusive,
                               int endExclusive) {

        IntArrayList allTokens = ENC.encode(text);
        int to = Math.min(endExclusive, allTokens.size());

        /* Copy the required range into a new IntArrayList (decode needs that) */
        IntArrayList sub = new IntArrayList(to - beginInclusive);
        for (int i = beginInclusive; i < to; i++) {
            sub.add(allTokens.get(i));
        }
        return ENC.decode(sub);
    }
}