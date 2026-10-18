package cat.narezany.margyt;

/**
 * The arithmetic of the bottom bar, apart from the views it is drawn over:
 * how wide each tab is while one of them is opening, and how an icon written
 * as path data becomes strokes of a pen.
 */
final class NavPlan {

    private NavPlan() {}

    /**
     * Where each tab begins and ends along a bar `room` wide.
     *
     * Every tab has the same width to start with, and a tab that is open is
     * wider by what its name takes, `extra`, in proportion to how far open it
     * is. The rest share what is left. No tab is narrower than `least`: when
     * the names would squeeze the others past that, it is the names that give.
     * There is one more edge than there are tabs, the first at nought and the
     * last at `room`.
     */
    static float[] edges(float room, float[] open, float[] extra, float least) {
        int count = open.length;
        float[] edges = new float[count + 1];
        if (count == 0) return edges;
        float wanted = 0f;
        for (int i = 0; i < count; i++) wanted += open[i] * extra[i];
        float each = (room - wanted) / count, share = 1f;
        if (each < least && wanted > 0f) {
            each = Math.min(least, room / count);
            share = Math.max(0f, (room - each * count) / wanted);
        }
        for (int i = 0; i < count; i++) {
            edges[i + 1] = edges[i] + each + open[i] * extra[i] * share;
        }
        edges[count] = room;
        return edges;
    }

    /** What an outline is drawn with. */
    interface Pen {
        void move(float x, float y);
        void line(float x, float y);
        void curve(float x1, float y1, float x2, float y2, float x, float y);
        void close();
    }

    /**
     * Draws path data, the way an icon is written in a vector drawable, with
     * a pen. It knows the commands the icons here are made of -- M, L, H, V,
     * C, S, Q, T and Z, in capitals and in small letters -- and stops at one
     * it does not know rather than draw something else. A pen has one kind
     * of curve, so the simpler kind is drawn as that one.
     */
    static void trace(String data, Pen pen) {
        int at = 0, length = data.length();
        char command = 0;
        float x = 0f, y = 0f, startX = 0f, startY = 0f, bendX = 0f, bendY = 0f;
        boolean bent = false, bowed = false;
        float[] given = new float[6];
        while (true) {
            at = past(data, at);
            if (at >= length) return;
            char next = data.charAt(at);
            if ((next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z')) {
                command = next;
                at++;
                if (command == 'z' || command == 'Z') {
                    pen.close();
                    x = startX;
                    y = startY;
                    bent = false;
                    bowed = false;
                }
                continue;
            }
            int wanted = wants(command);
            if (wanted == 0) return;
            for (int i = 0; i < wanted; i++) {
                at = past(data, at);
                int from = at;
                if (at < length && (data.charAt(at) == '-' || data.charAt(at) == '+')) at++;
                boolean point = false;
                while (at < length) {
                    char digit = data.charAt(at);
                    if (digit >= '0' && digit <= '9') {
                        at++;
                    } else if (digit == '.' && !point) {
                        point = true;
                        at++;
                    } else {
                        break;
                    }
                }
                if (at == from) return;
                try {
                    given[i] = Float.parseFloat(data.substring(from, at));
                } catch (NumberFormatException broken) {
                    return;
                }
            }
            boolean relative = command >= 'a';
            float baseX = relative ? x : 0f, baseY = relative ? y : 0f;
            char kind = relative ? (char) (command - 32) : command;
            if (kind == 'Q' || kind == 'T') {
                // T: the bend mirrors the one before it, when what came before was one of these
                float atX = kind == 'Q' ? baseX + given[0] : bowed ? 2 * x - bendX : x;
                float atY = kind == 'Q' ? baseY + given[1] : bowed ? 2 * y - bendY : y;
                float toX = baseX + given[kind == 'Q' ? 2 : 0], toY = baseY + given[kind == 'Q' ? 3 : 1];
                pen.curve(x + (atX - x) * 2f / 3f, y + (atY - y) * 2f / 3f,
                        toX + (atX - toX) * 2f / 3f, toY + (atY - toY) * 2f / 3f, toX, toY);
                bendX = atX;
                bendY = atY;
                x = toX;
                y = toY;
                bowed = true;
                bent = false;
                continue;
            }
            bowed = false;
            switch (kind) {
                case 'M':
                    x = baseX + given[0];
                    y = baseY + given[1];
                    pen.move(x, y);
                    startX = x;
                    startY = y;
                    // more pairs after a move are lines
                    command = relative ? 'l' : 'L';
                    bent = false;
                    break;
                case 'L':
                    x = baseX + given[0];
                    y = baseY + given[1];
                    pen.line(x, y);
                    bent = false;
                    break;
                case 'H':
                    x = baseX + given[0];
                    pen.line(x, y);
                    bent = false;
                    break;
                case 'V':
                    y = baseY + given[0];
                    pen.line(x, y);
                    bent = false;
                    break;
                case 'C':
                    bendX = baseX + given[2];
                    bendY = baseY + given[3];
                    pen.curve(baseX + given[0], baseY + given[1], bendX, bendY,
                            baseX + given[4], baseY + given[5]);
                    x = baseX + given[4];
                    y = baseY + given[5];
                    bent = true;
                    break;
                default:  // S: the first bend mirrors the last one of the curve before
                    float firstX = bent ? 2 * x - bendX : x, firstY = bent ? 2 * y - bendY : y;
                    bendX = baseX + given[0];
                    bendY = baseY + given[1];
                    pen.curve(firstX, firstY, bendX, bendY, baseX + given[2], baseY + given[3]);
                    x = baseX + given[2];
                    y = baseY + given[3];
                    bent = true;
                    break;
            }
        }
    }

    private static int past(String data, int at) {
        while (at < data.length() && (data.charAt(at) == ' ' || data.charAt(at) == ',')) at++;
        return at;
    }

    private static int wants(char command) {
        switch (command) {
            case 'M': case 'm': case 'L': case 'l': return 2;
            case 'H': case 'h': case 'V': case 'v': return 1;
            case 'C': case 'c': return 6;
            case 'S': case 's': case 'Q': case 'q': return 4;
            case 'T': case 't': return 2;
            default: return 0;
        }
    }
}
