package android.example.judgement.domain;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class NamesTest {
    @Test
    public void trimsUnicodeOuterWhitespaceWithoutChangingInternalText() {
        assertEquals("A  B", Names.displayName("\u0085\u00a0\u2003A  B\u202f\u3000\n"));
        assertEquals("A\tB", Names.displayName("  A\tB  "));
    }

    @Test
    public void namesWithDifferentCaseAndCompositionShareOneKey() {
        assertEquals(Names.key("  JOSÉ "), Names.key("Jose\u0301"));
        assertEquals("Jose\u0301", Names.displayName(" Jose\u0301 "));
    }

    @Test
    public void uniquenessDoesNotDependOnTurkishDeviceLocale() {
        Locale prior = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("niraj", Names.key("NIRAJ"));
            assertEquals("i\u0307", Names.key("İ"));
        } finally {
            Locale.setDefault(prior);
        }
    }

    @Test
    public void rejectsEmptyWhitespaceControlAndInvisibleOnlyNames() {
        String[] invalid = {null, "", " \n\t", "\u00a0\u2003\u3000", "\u0000\u0007",
                "\u200b\u200d\ufeff", "\u0301\ufe0f", "\ud800"};
        for (String value : invalid) {
            try {
                Names.displayName(value);
                fail("Accepted a blank-looking name");
            } catch (IllegalArgumentException expected) {
                // Every entry is rejected through the same policy used by profiles and games.
            }
        }
    }

    @Test
    public void preservesLongUnicodeNamesAndSqlWildcardsLiterally() {
        StringBuilder longName = new StringBuilder("निरज 🙂 %_ ");
        for (int i = 0; i < 10_000; i++) {
            longName.append('x');
        }
        assertEquals(longName.toString(), Names.displayName(longName.toString()));
        assertEquals(longName.toString(), Names.key(longName.toString()));
        assertEquals("محمد", Names.displayName("محمد"));
        assertEquals("👩‍👩‍👧‍👦", Names.displayName("👩‍👩‍👧‍👦"));
    }

    @Test
    public void comparisonDoesNotCollapseInternalWhitespaceOrPrefixes() {
        assertEquals("alex  smith", Names.key("Alex  Smith"));
        assertEquals("alex smith", Names.key("Alex Smith"));
        assertEquals("alex smith jr", Names.key("Alex Smith Jr"));
    }
}
