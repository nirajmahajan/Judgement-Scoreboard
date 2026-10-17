package android.example.judgement.domain;

import java.text.Normalizer;
import java.util.Locale;

/** The shared name policy for saved profiles and game participants. */
public final class Names {
    private Names() {
    }

    /** Keeps the full entered name, removing Unicode whitespace at its outer edges. */
    public static String displayName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Enter a player name.");
        }
        int start = 0;
        int end = raw.length();
        while (start < end) {
            int codePoint = raw.codePointAt(start);
            if (!isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = raw.codePointBefore(end);
            if (!isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        String display = raw.substring(start, end);
        if (!containsVisibleCharacter(display)) {
            throw new IllegalArgumentException("Enter a player name with a visible character.");
        }
        return display;
    }

    /** Names compare consistently regardless of the device's language or Unicode composition. */
    public static String key(String raw) {
        return Normalizer.normalize(displayName(raw), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                || codePoint == 0x0085;
    }

    private static boolean containsVisibleCharacter(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (!isWhitespace(codePoint)
                    && !Character.isISOControl(codePoint)
                    && type != Character.FORMAT
                    && type != Character.SURROGATE
                    && type != Character.NON_SPACING_MARK
                    && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }
}
