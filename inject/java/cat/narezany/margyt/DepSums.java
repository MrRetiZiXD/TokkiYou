package cat.narezany.margyt;

/**
 * What the Dep Calculator still works out on the phone: the four sums, what a
 * card is called and what colour a number on the wheel is. What anything pays
 * is not here -- the server decides that, and the phone is told.
 */
final class DepSums {

    private DepSums() {}

    static final String[] REEL = {"🍒", "🍋", "🔔", "💎", "🍀", "7"};
    private static final String[] RANK =
            {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    private static final String[] SUIT = {"♠", "♥", "♦", "♣"};

    /** One step of a calculator that works a sum out as it is typed. */
    static double apply(double left, char operation, double right) {
        switch (operation) {
            case '+': return left + right;
            case '−': return left - right;
            case '×': return left * right;
            case '÷': return right == 0 ? Double.NaN : left / right;
            default: return right;
        }
    }

    static String shown(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "∞";
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return Long.toString((long) value);
        String text = String.format(java.util.Locale.US, "%.6f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text;
    }

    /** A card out of fifty-two, as it is printed on one. */
    static String card(int card) {
        return RANK[card % 13] + SUIT[(card / 13) % 4];
    }

    /** Whether a number on the wheel is red. Nought is neither. */
    static boolean red(int number) {
        if (number <= 0 || number > 36) return false;
        boolean low = number <= 10 || (number >= 19 && number <= 28);
        return low == (number % 2 == 1);
    }
}
