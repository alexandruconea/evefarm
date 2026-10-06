package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownHtmlTest {

    @Test
    void releaseNotesBecomeHeadingsAndLists() {
        String markdown = """
                ## What's new

                ### Industry Calculator
                - New **Chain** tab: the order to build in.
                - Everything is made in *one* job.

                ### Other
                - PowerShell is always started from `System32`.
                """;

        assertEquals("<h2>What's new</h2>"
                + "<h3>Industry Calculator</h3>"
                + "<ul><li>New <b>Chain</b> tab: the order to build in.</li>"
                + "<li>Everything is made in <i>one</i> job.</li></ul>"
                + "<h3>Other</h3>"
                + "<ul><li>PowerShell is always started from <code>System32</code>.</li></ul>",
                MarkdownHtml.toHtml(markdown));
    }

    @Test
    void nestedAndNumberedListsAndParagraphs() {
        String markdown = "Intro line\nsecond line\n\n1. First\n   - inner\n2. Second\ncontinued\n\nAfter";

        assertEquals("<p>Intro line<br>second line</p>"
                + "<ol><li>First<ul><li>inner</li></ul></li><li>Second<br>continued</li></ol>"
                + "<p>After</p>", MarkdownHtml.toHtml(markdown));
    }

    @Test
    void htmlInTheNotesIsShownAsText() {
        assertEquals("<p>&lt;img src=&quot;x&quot;&gt; &amp; &lt;b&gt;bold&lt;/b&gt;</p>",
                MarkdownHtml.toHtml("<img src=\"x\"> & <b>bold</b>"));
    }

    @Test
    void onlyWebLinksAreClickable() {
        assertEquals("<p><a href=\"https://github.com/a?b=1&amp;c=2\">the page</a></p>",
                MarkdownHtml.toHtml("[the page](https://github.com/a?b=1&c=2)"));
        assertEquals("<p>bad</p>", MarkdownHtml.toHtml("[bad](javascript:alert(1))"));
        assertEquals("<p><b>Full Changelog</b>: <a href=\"https://github.com/x/compare/v1...v2\">"
                + "https://github.com/x/compare/v1...v2</a>.</p>",
                MarkdownHtml.toHtml("**Full Changelog**: https://github.com/x/compare/v1...v2."));
        assertEquals("<p>a picture</p>", MarkdownHtml.toHtml("![a picture](https://example.com/a.png)"));
        assertTrue(MarkdownHtml.isWebUrl("https://github.com"));
        assertFalse(MarkdownHtml.isWebUrl("file:///C:/Windows"));
    }

    @Test
    void wordsWithUnderscoresAndCodeStayAsTheyAre() {
        assertEquals("<p>snake_case_name and <code>a*b*c</code> and 2 * 3 * 4</p>",
                MarkdownHtml.toHtml("snake_case_name and `a*b*c` and 2 * 3 * 4"));
    }

    @Test
    void codeBlocksRulesAndQuotes() {
        assertEquals("<pre>x &lt; y\n  z</pre><hr><blockquote><p>Note</p></blockquote>",
                MarkdownHtml.toHtml("```\nx < y\n  z\n```\n---\n> Note"));
    }

    @Test
    void thePageCarriesItsStyle() {
        String page = MarkdownHtml.page("# Title", new Font("Dialog", Font.PLAIN, 12), new Color(0x5fc9c2));

        assertTrue(page.contains("h1 { font-size: 19pt; }"));
        assertTrue(page.contains("a { color: #5fc9c2; }"));
        assertTrue(page.endsWith("<body><h1>Title</h1></body></html>"));
    }
}
