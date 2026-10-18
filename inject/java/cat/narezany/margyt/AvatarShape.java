package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The shape a round picture is cut to.
 *
 * TikTok does not cut avatars round itself: the image loader does, and it
 * does it in three drawables that all come to the same line -- add a circle
 * to a path, then fill the path with the picture. The build sends that one
 * call here, and what is added instead is whatever shape was chosen. The
 * picture is still drawn by the loader, with its own smoothing and its own
 * border, which now follows the shape because it is built by the same call.
 *
 * It is every picture the loader cuts round, not only faces: the loader does
 * not know what it is drawing, and nearly everything round in the app is an
 * avatar.
 */
public final class AvatarShape {

    private AvatarShape() {}

    static final String KEY = "avatar_shape";
    static final String DEFAULT = "rounded";
    static final String[] ALL = {
            "circle", "rounded", "square", "cookie", "clover", "burst", "sun",
            "pentagon", "hexagon", "heart"};

    /** How finely an outline given as a distance in each direction is traced. */
    private static final int POINTS = 144;

    /** -1 until the setting has been read. */
    private static volatile int chosen = -1;
    private static final Path[] UNIT = new Path[ALL.length];
    private static final Matrix PLACE = new Matrix();

    static String chosen() {
        return ALL[index()];
    }

    static void choose(Context context, String shape) {
        int found = 0;
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(shape)) found = i;
        chosen = found;
        context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, ALL[found]).apply();
    }

    static void reloadSettings() {
        chosen = -1;
    }

    private static int index() {
        int known = chosen;
        if (known >= 0) return known;
        Context context = Margy.context();
        if (context == null) return defaultIndex();  // too early to know; ask again
        int found = defaultIndex();
        try {
            SharedPreferences prefs = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
            String name = prefs.getString(KEY, DEFAULT);
            for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(name)) found = i;
        } catch (Throwable ignored) { }
        chosen = found;
        return found;
    }

    private static int defaultIndex() {
        for (int i = 0; i < ALL.length; i++) if (DEFAULT.equals(ALL[i])) return i;
        return 0;
    }

    /** What the image loader calls where it used to add a circle to its path. */
    public static void addCircle(Path path, float x, float y, float radius, Path.Direction direction) {
        int shape = index();
        if (shape > 0 && radius > 0) {
            try {
                Path unit = unit(shape);
                synchronized (PLACE) {
                    PLACE.setScale(radius, radius);
                    PLACE.postTranslate(x, y);
                    path.addPath(unit, PLACE);
                }
                return;
            } catch (Throwable error) {
                // a shape that cannot be drawn is a circle
            }
        }
        path.addCircle(x, y, radius, direction);
    }

    // ------------------------------------------------------- the story ring

    private static final Path RING = new Path();
    private static final Path PART = new Path();
    private static final Matrix RING_PLACE = new Matrix();

    /**
     * What the view that draws a story ring calls where it used to draw an arc.
     *
     * The ring is not a picture and is not cut out of anything: it is a view
     * of its own over the avatar that strokes an arc round an oval, all the
     * way round for one story and in pieces for several or while one loads.
     * So the arc is what takes the avatar's shape -- the outline of the
     * chosen shape fitted to the same oval, or as much of it as the arc swept,
     * drawn with the same paint and so in the same colours.
     */
    public static void drawArc(Canvas canvas, RectF oval, float start, float sweep,
                               boolean useCentre, Paint paint) {
        int shape = index();
        if (shape > 0 && !useCentre && oval != null && sweep != 0) {
            try {
                synchronized (RING) {
                    RING.rewind();
                    RING_PLACE.setScale(oval.width() / 2f, oval.height() / 2f);
                    RING_PLACE.postTranslate(oval.centerX(), oval.centerY());
                    RING.addPath(unit(shape), RING_PLACE);
                    if (Math.abs(sweep) >= 359.5f) {
                        canvas.drawPath(RING, paint);
                        return;
                    }
                    // a piece of it: as far round the outline as the arc was round the circle.
                    // An arc is measured from three o'clock; the outlines here begin at the top.
                    android.graphics.PathMeasure measure = new android.graphics.PathMeasure(RING, true);
                    float length = measure.getLength();
                    float from = start + (sweep < 0 ? sweep : 0) + 90f;
                    from = ((from % 360f) + 360f) % 360f / 360f * length;
                    float span = Math.min(Math.abs(sweep), 360f) / 360f * length;
                    PART.rewind();
                    if (from + span <= length) {
                        measure.getSegment(from, from + span, PART, true);
                    } else {
                        measure.getSegment(from, length, PART, true);
                        measure.getSegment(0, from + span - length, PART, true);
                    }
                    canvas.drawPath(PART, paint);
                    return;
                }
            } catch (Throwable error) {
                // a ring left round is better than no ring
            }
        }
        canvas.drawArc(oval, start, sweep, useCentre, paint);
    }

    /** The shape avatars are in at the moment, in a square two across. */
    static Path chosenUnit() {
        return unit(index());
    }

    /** A shape in a square two across, centred on nothing. Built once and never changed. */
    static Path unit(int shape) {
        Path known = UNIT[shape];
        if (known != null) return known;
        Path made = new Path();
        switch (ALL[shape]) {
            case "rounded":
                made.addRoundRect(new RectF(-1, -1, 1, 1), 0.44f, 0.44f, Path.Direction.CW);
                break;
            case "square":
                made.addRect(-1, -1, 1, 1, Path.Direction.CW);
                break;
            case "heart":
                // Two circles that overlap at the top and a point at the
                // bottom, filling the square from edge to edge. Drawn as one
                // curve it left a quarter of the picture unused, and what was
                // left of a face in it was small.
                made.arcTo(new RectF(-1f, -1f, 0.1f, 0.1f), -35.1f, -174.9f, true);
                made.cubicTo(-0.70f, 0.25f, -0.28f, 0.70f, 0f, 1f);
                made.cubicTo(0.28f, 0.70f, 0.70f, 0.25f, 0.926f, -0.175f);
                made.arcTo(new RectF(-0.1f, -1f, 1f, 0.1f), 30f, -174.9f, false);
                made.close();
                break;
            case "circle":
                made.addCircle(0, 0, 1, Path.Direction.CW);
                break;
            default:
                for (int i = 0; i < POINTS; i++) {
                    double around = 2 * Math.PI * i / POINTS - Math.PI / 2;
                    float reach = reach(ALL[shape], around + Math.PI / 2);
                    float px = (float) (reach * Math.cos(around)), py = (float) (reach * Math.sin(around));
                    if (i == 0) made.moveTo(px, py);
                    else made.lineTo(px, py);
                }
                made.close();
        }
        UNIT[shape] = made;
        return made;
    }

    /** How far from the centre a shape reaches in a direction, out of 1. Up is zero. */
    static float reach(String shape, double around) {
        switch (shape) {
            case "cookie": return 0.92f + 0.08f * (float) Math.cos(9 * around);
            case "clover": return 0.84f + 0.16f * (float) Math.cos(4 * around + Math.PI);
            case "burst": return 0.90f + 0.10f * (float) Math.cos(12 * around);
            case "sun": return 0.87f + 0.13f * (float) Math.cos(8 * around);
            case "pentagon": return sided(5, around);
            case "hexagon": return sided(6, around);
            default: return 1f;
        }
    }

    /** A polygon with its corners taken off, one corner pointing up. */
    private static float sided(int sides, double around) {
        double slice = 2 * Math.PI / sides;
        double within = around % slice;
        if (within < 0) within += slice;
        double flat = Math.cos(Math.PI / sides) / Math.cos(within - slice / 2);
        // the corner is where `flat` reaches 1; a soft minimum against a
        // little less than that rounds it without moving the sides
        double cap = 0.93, sharp = 36.0;
        double soft = -Math.log(Math.exp(-sharp * flat) + Math.exp(-sharp * cap)) / sharp;
        return (float) (soft / cap);
    }

    /** A shape filled with one colour, for the list it is chosen from. */
    static Bitmap picture(String shape, int size, int colour) {
        int which = 0;
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(shape)) which = i;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(colour);
        Path placed = new Path();
        Matrix place = new Matrix();
        place.setScale(size / 2f - 1, size / 2f - 1);
        place.postTranslate(size / 2f, size / 2f);
        placed.addPath(unit(which), place);
        canvas.drawPath(placed, paint);
        return bitmap;
    }

    static String nameOf(String shape) {
        switch (shape) {
            case "rounded": return Text.SHAPE_ROUNDED;
            case "square": return Text.SHAPE_SQUARE;
            case "cookie": return Text.SHAPE_COOKIE;
            case "clover": return Text.SHAPE_CLOVER;
            case "burst": return Text.SHAPE_BURST;
            case "sun": return Text.SHAPE_SUN;
            case "pentagon": return Text.SHAPE_PENTAGON;
            case "hexagon": return Text.SHAPE_HEXAGON;
            case "heart": return Text.SHAPE_HEART;
            default: return Text.SHAPE_CIRCLE;
        }
    }
}
