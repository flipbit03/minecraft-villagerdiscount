/*
 * Derived from Adventure's MiniMessage 4.26.1 (https://github.com/KyoriPowered/adventure),
 * licensed under the MIT License:
 *
 * Copyright (c) 2017-2025 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.cadu.villagerdiscount;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renders MiniMessage strings as legacy-formatted text, so messages written for the
 * original Paper/Adventure build keep working on Spigot.
 *
 * Tokenizing, escapes, quoting, case-insensitive tag names and tag closing follow
 * MiniMessage's own parser. Supported tags: named and hex colors (also via
 * {@code color:}/{@code colour:}/{@code c:}), the five decorations with their aliases,
 * {@code !} negation and {@code :false}, {@code reset}, {@code newline}/{@code br}, and
 * caller-supplied placeholders inserted as plain text. Any other tag stays as literal
 * text, which is what MiniMessage does with tags it does not know.
 */
public final class MiniMessageLegacy {

    private static final Pattern TAG_NAME = Pattern.compile("[!?#]?[a-z0-9_-]*");

    private static final Map<String, ChatColor> COLORS = Map.ofEntries(
            Map.entry("black", ChatColor.BLACK),
            Map.entry("dark_blue", ChatColor.DARK_BLUE),
            Map.entry("dark_green", ChatColor.DARK_GREEN),
            Map.entry("dark_aqua", ChatColor.DARK_AQUA),
            Map.entry("dark_red", ChatColor.DARK_RED),
            Map.entry("dark_purple", ChatColor.DARK_PURPLE),
            Map.entry("gold", ChatColor.GOLD),
            Map.entry("gray", ChatColor.GRAY),
            Map.entry("grey", ChatColor.GRAY),
            Map.entry("dark_gray", ChatColor.DARK_GRAY),
            Map.entry("dark_grey", ChatColor.DARK_GRAY),
            Map.entry("blue", ChatColor.BLUE),
            Map.entry("green", ChatColor.GREEN),
            Map.entry("aqua", ChatColor.AQUA),
            Map.entry("red", ChatColor.RED),
            Map.entry("light_purple", ChatColor.LIGHT_PURPLE),
            Map.entry("yellow", ChatColor.YELLOW),
            Map.entry("white", ChatColor.WHITE));

    private static final Map<String, ChatColor> DECORATIONS = Map.ofEntries(
            Map.entry("obfuscated", ChatColor.MAGIC),
            Map.entry("obf", ChatColor.MAGIC),
            Map.entry("bold", ChatColor.BOLD),
            Map.entry("b", ChatColor.BOLD),
            Map.entry("strikethrough", ChatColor.STRIKETHROUGH),
            Map.entry("st", ChatColor.STRIKETHROUGH),
            Map.entry("underlined", ChatColor.UNDERLINE),
            Map.entry("u", ChatColor.UNDERLINE),
            Map.entry("italic", ChatColor.ITALIC),
            Map.entry("em", ChatColor.ITALIC),
            Map.entry("i", ChatColor.ITALIC));

    private static final List<ChatColor> DECORATION_ORDER = List.of(
            ChatColor.MAGIC, ChatColor.BOLD, ChatColor.STRIKETHROUGH, ChatColor.UNDERLINE, ChatColor.ITALIC);

    private enum TokenType { TEXT, OPEN, CLOSE, OPEN_CLOSE }

    private record Token(int start, int end, TokenType type) {}

    /** One open styling tag: its parts (for matching the close tag) and what it sets. */
    private record Frame(List<String> parts, String color, ChatColor decoration, boolean state) {}

    private MiniMessageLegacy() {
    }

    /**
     * @param placeholders lowercase tag name -> text inserted without parsing, like
     *                     Adventure's {@code Placeholder.unparsed}; section signs are
     *                     dropped so a value can never inject legacy formatting
     */
    public static String toLegacy(String message, Map<String, String> placeholders) {
        Output out = new Output();

        for (Token token : tokenize(message)) {
            if (token.type() == TokenType.TEXT) {
                out.append(unescape(message, token.start(), token.end(), '<'));
                continue;
            }
            List<String> parts = tagParts(message, token);
            String name = parts.get(0).toLowerCase(Locale.ROOT);

            if (token.type() == TokenType.CLOSE) {
                if (!isKnown(name, placeholders)) {
                    out.append(literal(message, token));
                } else if (!name.equals("reset") && !close(out.open, parts)) {
                    out.append(literal(message, token));
                }
                continue;
            }

            // MiniMessage validates the raw name, so a quoted one is never a tag.
            char first = message.charAt(token.start() + 1);
            if (first == '\'' || first == '"' || !TAG_NAME.matcher(name).matches()) {
                out.append(literal(message, token));
            } else if ((placeholders.containsKey(name) || name.equals("reset")) && parts.size() > 1) {
                // These take no arguments; given any, MiniMessage leaves the tag as text.
                out.append(literal(message, token));
            } else if (placeholders.containsKey(name)) {
                out.append(placeholders.get(name).replace(String.valueOf(ChatColor.COLOR_CHAR), ""));
            } else if (name.equals("newline") || name.equals("br")) {
                out.append("\n");
            } else if (name.equals("reset")) {
                out.open.clear();
            } else {
                Frame frame = styleFrame(name, parts);
                if (frame == null) {
                    out.append(literal(message, token));
                } else if (token.type() == TokenType.OPEN) {
                    out.open.add(frame);
                }
                // A self-closing styling tag (<green/>) styles nothing.
            }
        }
        return out.text.toString();
    }

    private static boolean isKnown(String name, Map<String, String> placeholders) {
        return placeholders.containsKey(name)
                || name.equals("newline") || name.equals("br") || name.equals("reset")
                || name.equals("color") || name.equals("colour") || name.equals("c")
                || resolveColor(name) != null
                || DECORATIONS.containsKey(name)
                || (name.startsWith("!") && DECORATIONS.containsKey(name.substring(1)));
    }

    /** Closes the innermost open tag matching {@code closeParts} and everything inside it. */
    private static boolean close(List<Frame> open, List<String> closeParts) {
        for (int i = open.size() - 1; i >= 0; i--) {
            if (closes(closeParts, open.get(i).parts())) {
                open.subList(i, open.size()).clear();
                return true;
            }
        }
        return false;
    }

    private static boolean closes(List<String> closeParts, List<String> openParts) {
        if (closeParts.size() > openParts.size()) {
            return false;
        }
        // The tag name is case-insensitive, the arguments are not.
        if (!closeParts.get(0).equalsIgnoreCase(openParts.get(0))) {
            return false;
        }
        for (int i = 1; i < closeParts.size(); i++) {
            if (!closeParts.get(i).equals(openParts.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static Frame styleFrame(String name, List<String> parts) {
        ChatColor decoration = DECORATIONS.get(name);
        if (decoration != null) {
            boolean state = parts.size() < 2 || !(parts.get(1).equals("false") || parts.get(1).equals("off"));
            return new Frame(parts, null, decoration, state);
        }
        if (name.startsWith("!") && DECORATIONS.containsKey(name.substring(1))) {
            return new Frame(parts, null, DECORATIONS.get(name.substring(1)), false);
        }
        String color;
        if (name.equals("color") || name.equals("colour") || name.equals("c")) {
            color = parts.size() < 2 ? null : resolveColor(parts.get(1).toLowerCase(Locale.ROOT));
        } else {
            color = resolveColor(name);
        }
        return color == null ? null : new Frame(parts, color, null, false);
    }

    /** Legacy code for a MiniMessage color name or #hex, or null if it is neither. */
    private static String resolveColor(String name) {
        ChatColor named = COLORS.get(name);
        if (named != null) {
            return named.toString();
        }
        if (name.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(name.substring(1), 16) & 0xFFFFFF;
                return net.md_5.bungee.api.ChatColor.of(String.format("#%06X", rgb)).toString();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** The legacy text being built, the open tags, and the style last written into it. */
    private static final class Output {
        final StringBuilder text = new StringBuilder();
        final List<Frame> open = new ArrayList<>();
        String emitted = "";

        /** Appends in the style of the open tags, writing codes only when the style changes. */
        void append(String value) {
            if (value.isEmpty()) {
                return;
            }
            String color = null;
            Map<ChatColor, Boolean> decorations = new EnumMap<>(ChatColor.class);
            for (Frame frame : open) {
                if (frame.color() != null) {
                    color = frame.color();
                } else {
                    decorations.put(frame.decoration(), frame.state());
                }
            }
            StringBuilder style = new StringBuilder(color != null ? color : "");
            for (ChatColor decoration : DECORATION_ORDER) {
                if (decorations.getOrDefault(decoration, false)) {
                    style.append(decoration);
                }
            }
            if (!style.toString().equals(emitted)) {
                // A color code resets decorations by itself; without one, reset explicitly.
                if (color == null && !emitted.isEmpty()) {
                    text.append(ChatColor.RESET);
                }
                text.append(style);
                emitted = style.toString();
            }
            text.append(value);
        }
    }

    private static String literal(String message, Token token) {
        return unescape(message, token.start(), token.end(), '<');
    }

    /** First pass of MiniMessage's TokenParser: split into text and tag tokens. */
    private static List<Token> tokenize(String message) {
        final int normal = 0;
        final int tag = 1;
        final int string = 2;
        List<Token> tokens = new ArrayList<>();
        int state = normal;
        boolean escaped = false;
        int currentTokenEnd = 0;
        int marker = -1;
        char currentStringChar = 0;
        int length = message.length();

        for (int i = 0; i < length; i++) {
            char c = message.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < length) {
                char next = Character.toLowerCase(message.charAt(i + 1));
                if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f') || next == 'r'
                        || (next >= 'k' && next <= 'o')) {
                    throw new IllegalArgumentException(
                            "Legacy formatting codes are not supported in MiniMessage strings: " + message);
                }
            }
            if (!escaped) {
                if (c == '\\' && i + 1 < length) {
                    char next = message.charAt(i + 1);
                    if (state == normal) {
                        escaped = next == '<' || next == '\\';
                    } else if (state == string) {
                        escaped = next == currentStringChar || next == '\\';
                    } else if (next == '<') {
                        escaped = true;
                        state = normal;
                    }
                    if (escaped) {
                        continue;
                    }
                }
            } else {
                escaped = false;
                continue;
            }

            if (state == normal) {
                if (c == '<') {
                    marker = i;
                    state = tag;
                }
            } else if (state == tag) {
                if (c == '>') {
                    if (i == marker + 1) {
                        // <> is not a tag
                        state = normal;
                    } else {
                        if (currentTokenEnd != marker) {
                            tokens.add(new Token(currentTokenEnd, marker, TokenType.TEXT));
                        }
                        currentTokenEnd = i + 1;
                        TokenType type = TokenType.OPEN;
                        if (marker + 1 < length && message.charAt(marker + 1) == '/') {
                            type = TokenType.CLOSE;
                        } else if (marker + 2 < length && message.charAt(i - 1) == '/') {
                            type = TokenType.OPEN_CLOSE;
                        }
                        tokens.add(new Token(marker, currentTokenEnd, type));
                        state = normal;
                    }
                } else if (c == '<') {
                    marker = i;
                } else if (c == '\'' || c == '"') {
                    currentStringChar = c;
                    if (message.indexOf(c, i + 1) != -1) {
                        state = string;
                    }
                }
            } else if (c == currentStringChar) {
                state = tag;
            }

            if (i == length - 1 && state == tag) {
                // An unterminated '<': rescan everything after it as text.
                i = marker;
                state = normal;
            }
        }

        int end = tokens.isEmpty() ? -1 : tokens.get(tokens.size() - 1).end();
        if (end == -1) {
            tokens.add(new Token(0, length, TokenType.TEXT));
        } else if (end != length) {
            tokens.add(new Token(end, length, TokenType.TEXT));
        }
        return tokens;
    }

    /** Second pass of MiniMessage's TokenParser: split a tag into its ':'-separated parts. */
    private static List<String> tagParts(String message, Token token) {
        int startIndex = token.type() == TokenType.CLOSE ? token.start() + 2 : token.start() + 1;
        int endIndex = token.type() == TokenType.OPEN_CLOSE ? token.end() - 2 : token.end() - 1;
        List<int[]> bounds = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        char currentStringChar = 0;
        int marker = startIndex;

        for (int i = startIndex; i < endIndex; i++) {
            char c = message.charAt(i);
            if (!escaped) {
                if (c == '\\' && i + 1 < message.length()) {
                    char next = message.charAt(i + 1);
                    escaped = inString
                            ? next == currentStringChar || next == '\\'
                            : next == '<' || next == '\\';
                    if (escaped) {
                        continue;
                    }
                }
            } else {
                escaped = false;
                continue;
            }
            if (!inString) {
                if (c == ':') {
                    // Values are split by ':' unless it's in a URL
                    if (i + 2 < message.length() && message.charAt(i + 1) == '/' && message.charAt(i + 2) == '/') {
                        continue;
                    }
                    if (marker == i) {
                        bounds.add(new int[] {i, i});
                        marker++;
                    } else {
                        bounds.add(new int[] {marker, i});
                        marker = i + 1;
                    }
                } else if (c == '\'' || c == '"') {
                    inString = true;
                    currentStringChar = c;
                }
            } else if (c == currentStringChar) {
                inString = false;
            }
        }
        if (bounds.isEmpty()) {
            bounds.add(new int[] {startIndex, endIndex});
        } else {
            int end = bounds.get(bounds.size() - 1)[1];
            if (end != endIndex) {
                bounds.add(new int[] {end + 1, endIndex});
            }
        }

        List<String> parts = new ArrayList<>(bounds.size());
        for (int[] b : bounds) {
            parts.add(unquoteAndEscape(message, b[0], b[1]));
        }
        return parts;
    }

    private static String unquoteAndEscape(String text, int start, int end) {
        if (start == end) {
            return "";
        }
        int startIndex = start;
        int endIndex = end;
        char firstChar = text.charAt(startIndex);
        char lastChar = text.charAt(endIndex - 1);
        if (firstChar == '\'' || firstChar == '"') {
            startIndex++;
        } else {
            return text.substring(startIndex, endIndex);
        }
        if (lastChar == '\'' || lastChar == '"') {
            endIndex--;
        }
        if (startIndex > endIndex) {
            return text.substring(start, end);
        }
        return unescape(text, startIndex, endIndex, firstChar);
    }

    /** Drops the backslash in front of {@code escapable} or another backslash. */
    private static String unescape(String text, int startIndex, int endIndex, char escapable) {
        StringBuilder sb = new StringBuilder(endIndex - startIndex);
        for (int i = startIndex; i < endIndex; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < endIndex) {
                char next = text.charAt(i + 1);
                if (next == escapable || next == '\\') {
                    sb.append(next);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
