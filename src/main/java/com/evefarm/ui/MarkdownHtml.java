package com.evefarm.ui;

import com.evefarm.util.Text;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownHtml {

    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^( *)([-*+]|\\d{1,9}[.)])\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^ {0,3}([-*_])(?:\\s*\\1){2,}\\s*$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(```|~~~)");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>\\s?(.*)$");
    private static final String URL_END_PUNCTUATION = ".,;:!?)";

    private record OpenList(String tag, int indent) {
    }

    private record Link(String text, String url, int end) {
    }

    private final StringBuilder html = new StringBuilder();
    private final Deque<OpenList> lists = new ArrayDeque<>();
    private final List<String> paragraph = new ArrayList<>();
    private final List<String> quote = new ArrayList<>();
    private boolean afterBlank = true;

    private MarkdownHtml() {
    }

    static String page(String markdown, Font font, Color link) {
        float size = font.getSize2D();
        StringBuilder css = new StringBuilder()
                .append("h1, h2, h3, h4, h5, h6 { margin-top: 10px; margin-bottom: 4px; }")
                .append(String.format(Locale.US, "h1 { font-size: %.0fpt; }", size * 1.6))
                .append(String.format(Locale.US, "h2 { font-size: %.0fpt; }", size * 1.4))
                .append(String.format(Locale.US, "h3 { font-size: %.0fpt; }", size * 1.2))
                .append(String.format(Locale.US, "h4, h5, h6 { font-size: %.0fpt; }", size))
                .append("p { margin-top: 2px; margin-bottom: 6px; }")
                .append("ul, ol { margin-top: 2px; margin-bottom: 6px; margin-left: 20px; }")
                .append("li { margin-top: 2px; }")
                .append("pre { margin-top: 2px; margin-bottom: 6px; }");
        if (link != null) {
            css.append(String.format("a { color: #%06x; }", link.getRGB() & 0xFFFFFF));
        }
        return "<html><head><style>" + css + "</style></head><body>" + toHtml(markdown) + "</body></html>";
    }

    static String toHtml(String markdown) {
        MarkdownHtml converter = new MarkdownHtml();
        converter.convert(markdown);
        return converter.html.toString();
    }

    private void convert(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            Matcher fence = FENCE.matcher(line);
            if (fence.find()) {
                closeBlocks();
                List<String> code = new ArrayList<>();
                index++;
                while (index < lines.length && !lines[index].trim().startsWith(fence.group(1))) {
                    code.add(lines[index]);
                    index++;
                }
                html.append("<pre>").append(Text.escapeHtml(String.join("\n", code))).append("</pre>");
                afterBlank = false;
                continue;
            }
            handle(line);
        }
        closeBlocks();
    }

    private void handle(String line) {
        if (line.isBlank()) {
            flushParagraph();
            flushQuote();
            afterBlank = true;
            return;
        }
        Matcher quoted = QUOTE.matcher(line);
        if (quoted.matches()) {
            flushParagraph();
            closeLists();
            quote.add(quoted.group(1));
            afterBlank = false;
            return;
        }
        flushQuote();
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            closeBlocks();
            int level = heading.group(1).length();
            html.append("<h").append(level).append('>').append(inline(heading.group(2))).append("</h")
                    .append(level).append('>');
        } else if (RULE.matcher(line).matches()) {
            closeBlocks();
            html.append("<hr>");
        } else {
            Matcher item = LIST_ITEM.matcher(line);
            if (item.matches()) {
                flushParagraph();
                listItem(item.group(1).length(), item.group(2), item.group(3));
            } else if (!lists.isEmpty() && (!afterBlank || line.startsWith("  "))) {
                html.append("<br>").append(inline(line.strip()));
            } else {
                closeLists();
                paragraph.add(line.strip());
            }
        }
        afterBlank = false;
    }

    private void listItem(int indent, String marker, String text) {
        String tag = Character.isDigit(marker.charAt(0)) ? "ol" : "ul";
        while (!lists.isEmpty() && lists.peek().indent() > indent + 1) {
            closeList();
        }
        if (!lists.isEmpty() && indent < lists.peek().indent() + 2 && !lists.peek().tag().equals(tag)) {
            closeList();
        }
        if (lists.isEmpty() || indent >= lists.peek().indent() + 2) {
            lists.push(new OpenList(tag, indent));
            html.append('<').append(tag).append('>');
        } else {
            html.append("</li>");
        }
        html.append("<li>").append(inline(text));
    }

    private void closeBlocks() {
        flushParagraph();
        flushQuote();
        closeLists();
    }

    private void closeLists() {
        while (!lists.isEmpty()) {
            closeList();
        }
    }

    private void closeList() {
        html.append("</li></").append(lists.pop().tag()).append('>');
    }

    private void flushParagraph() {
        if (paragraph.isEmpty()) {
            return;
        }
        html.append("<p>").append(String.join("<br>", paragraph.stream().map(MarkdownHtml::inline).toList()))
                .append("</p>");
        paragraph.clear();
    }

    private void flushQuote() {
        if (quote.isEmpty()) {
            return;
        }
        html.append("<blockquote>").append(toHtml(String.join("\n", quote))).append("</blockquote>");
        quote.clear();
    }

    static String inline(String text) {
        StringBuilder out = new StringBuilder();
        int length = text.length();
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < length && isPunctuation(text.charAt(i + 1))) {
                out.append(Text.escapeHtml(String.valueOf(text.charAt(i + 1))));
                i += 2;
                continue;
            }
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i + 1) {
                    out.append("<code>").append(Text.escapeHtml(text.substring(i + 1, end))).append("</code>");
                    i = end + 1;
                    continue;
                }
            }
            if (c == '!' && i + 1 < length && text.charAt(i + 1) == '[') {
                Link image = link(text, i + 1);
                if (image != null) {
                    out.append(inline(image.text()));
                    i = image.end();
                    continue;
                }
            }
            if (c == '[') {
                Link link = link(text, i);
                if (link != null) {
                    out.append(anchor(link.url(), inline(link.text())));
                    i = link.end();
                    continue;
                }
            }
            if (c == '<') {
                int end = text.indexOf('>', i + 1);
                if (end > i && isWebUrl(text.substring(i + 1, end))) {
                    String url = text.substring(i + 1, end);
                    out.append(anchor(url, Text.escapeHtml(url)));
                    i = end + 1;
                    continue;
                }
            }
            if ((c == 'h' || c == 'H') && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))
                    && startsWithWebScheme(text, i)) {
                int end = i;
                while (end < length && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != '<') {
                    end++;
                }
                while (end > i && URL_END_PUNCTUATION.indexOf(text.charAt(end - 1)) >= 0) {
                    end--;
                }
                String url = text.substring(i, end);
                out.append(anchor(url, Text.escapeHtml(url)));
                i = end;
                continue;
            }
            if (text.startsWith("**", i) || text.startsWith("__", i) || text.startsWith("~~", i)) {
                String marker = text.substring(i, i + 2);
                int end = text.indexOf(marker, i + 2);
                if (end > i + 2) {
                    String tag = marker.equals("~~") ? "s" : "b";
                    out.append('<').append(tag).append('>').append(inline(text.substring(i + 2, end)))
                            .append("</").append(tag).append('>');
                    i = end + 2;
                    continue;
                }
            }
            if (c == '*' || c == '_') {
                int end = closingEmphasis(text, i, c);
                if (end > 0) {
                    out.append("<i>").append(inline(text.substring(i + 1, end))).append("</i>");
                    i = end + 1;
                    continue;
                }
            }
            out.append(Text.escapeHtml(String.valueOf(c)));
            i++;
        }
        return out.toString();
    }

    private static int closingEmphasis(String text, int start, char marker) {
        int length = text.length();
        if (start + 1 >= length || Character.isWhitespace(text.charAt(start + 1))) {
            return -1;
        }
        if (marker == '_' && start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            return -1;
        }
        for (int j = start + 2; j < length; j++) {
            if (text.charAt(j) == marker && !Character.isWhitespace(text.charAt(j - 1))
                    && (marker != '_' || j + 1 >= length || !Character.isLetterOrDigit(text.charAt(j + 1)))) {
                return j;
            }
        }
        return -1;
    }

    private static Link link(String text, int open) {
        int depth = 0;
        for (int j = open; j < text.length(); j++) {
            char c = text.charAt(j);
            if (c == '[') {
                depth++;
            } else if (c == ']' && --depth == 0) {
                if (j + 1 >= text.length() || text.charAt(j + 1) != '(') {
                    return null;
                }
                int close = closingParenthesis(text, j + 2);
                if (close < 0) {
                    return null;
                }
                String target = text.substring(j + 2, close).strip();
                int space = target.indexOf(' ');
                return new Link(text.substring(open + 1, j), space < 0 ? target : target.substring(0, space),
                        close + 1);
            }
        }
        return null;
    }

    private static int closingParenthesis(String text, int start) {
        int depth = 1;
        for (int j = start; j < text.length(); j++) {
            char c = text.charAt(j);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return j;
            }
        }
        return -1;
    }

    private static String anchor(String url, String label) {
        return isWebUrl(url) ? "<a href=\"" + Text.escapeHtml(url) + "\">" + label + "</a>" : label;
    }

    static boolean isWebUrl(String url) {
        return startsWithWebScheme(url, 0) && url.chars().noneMatch(c -> Character.isWhitespace(c) || c == '"'
                || c == '<' || c == '>');
    }

    private static boolean startsWithWebScheme(String text, int offset) {
        return text.regionMatches(true, offset, "https://", 0, 8) || text.regionMatches(true, offset, "http://", 0, 7);
    }

    private static boolean isPunctuation(char c) {
        return c < 128 && !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }
}
