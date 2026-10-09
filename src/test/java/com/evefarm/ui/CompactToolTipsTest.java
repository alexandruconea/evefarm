package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactToolTipsTest {

    @Test
    void shortTipsStayOnOneLine() {
        assertEquals("The ME of your component blueprints",
                CompactToolTips.compact("The ME of your component blueprints"));
    }

    @Test
    void longTipsWrapInANarrowBlock() {
        String compact = CompactToolTips.compact("Leave out what your characters already have: their assets as of "
                + "the last update, in the build system or anywhere <here> & there");

        assertTrue(compact.startsWith("<html><body style=\"width: "));
        assertTrue(compact.endsWith("anywhere &lt;here&gt; &amp; there</body></html>"));
    }

    @Test
    void lineBreaksAndHtmlAreKept() {
        assertEquals("<html><b>Already formatted</b> by its owner, so it is left exactly as it was written here</html>",
                CompactToolTips.compact("<html><b>Already formatted</b> by its owner, so it is left exactly as it "
                        + "was written here</html>"));
        assertTrue(CompactToolTips.compact("First line\nSecond line").contains("First line<br>Second line"));
    }
}
