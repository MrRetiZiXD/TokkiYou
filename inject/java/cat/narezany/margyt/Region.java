package cat.narezany.margyt;

import android.content.Context;
import android.telephony.TelephonyManager;

/**
 * Where the rewritten call sites land.
 *
 * Every
 *     invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
 * in TikTok's bytecode becomes
 *     invoke-static  {v0}, Lcat/narezany/margyt/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
 *
 * Same instruction format, same register count, same return type: the receiver
 * simply becomes the first argument, so nothing around the call has to be
 * renumbered. Each method here takes that receiver and hands it straight back
 * the real answer whenever the mod is off -- switching TokkiYou off has to leave
 * the app exactly as it was, down to the exception the real call would have
 * thrown.
 *
 * The answer passes through the plugins on its way out, so a plugin can say
 * something else about where the phone is without touching the call sites.
 */
public final class Region {

    private Region() {}

    public static String getSimCountryIso(TelephonyManager tm) {
        String answer = !Margy.active()
                ? (tm == null ? "" : tm.getSimCountryIso())
                : Margy.current()[Margy.ISO];
        return Plugins.region("sim_country", answer);
    }

    public static String getNetworkCountryIso(TelephonyManager tm) {
        String answer = !Margy.active()
                ? (tm == null ? "" : tm.getNetworkCountryIso())
                : Margy.current()[Margy.ISO];
        return Plugins.region("network_country", answer);
    }

    public static String getSimOperator(TelephonyManager tm) {
        String answer = !Margy.active()
                ? (tm == null ? "" : tm.getSimOperator())
                : Margy.current()[Margy.MCCMNC];
        return Plugins.region("sim_operator", answer);
    }

    public static String getNetworkOperator(TelephonyManager tm) {
        String answer = !Margy.active()
                ? (tm == null ? "" : tm.getNetworkOperator())
                : Margy.current()[Margy.MCCMNC];
        return Plugins.region("network_operator", answer);
    }

    public static String getSimOperatorName(TelephonyManager tm) {
        String answer = !Margy.active()
                ? (tm == null ? "" : tm.getSimOperatorName())
                : Margy.current()[Margy.CARRIER];
        return Plugins.region("sim_operator_name", answer);
    }

    public static String getNetworkOperatorName(TelephonyManager tm) {
        String answer = !Margy.active()
                ? (tm == null ? "" : tm.getNetworkOperatorName())
                : Margy.current()[Margy.CARRIER];
        return Plugins.region("network_operator_name", answer);
    }

    /**
     * Whether the phone really has a card in it.
     *
     * The invented card below is for phones with none, or nothing ever asks
     * which country the SIM is from. Where a card exists, the card answers:
     * two APIs describing the same hardware and disagreeing is what a fraud
     * check looks for.
     */
    private static boolean realCard(TelephonyManager tm) {
        if (tm == null) return false;
        try {
            if (tm.hasIccCard()) return true;
            return tm.getSimState() == TelephonyManager.SIM_STATE_READY;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int getSimState(TelephonyManager tm) {
        if (!Margy.active() || realCard(tm)) {
            return tm == null ? TelephonyManager.SIM_STATE_UNKNOWN : tm.getSimState();
        }
        return TelephonyManager.SIM_STATE_READY;
    }

    public static int getSimState(TelephonyManager tm, int slot) {
        if (!Margy.active() || realCard(tm)) {
            return tm == null ? TelephonyManager.SIM_STATE_UNKNOWN : tm.getSimState(slot);
        }
        return slot == 0 ? TelephonyManager.SIM_STATE_READY : TelephonyManager.SIM_STATE_UNKNOWN;
    }

    public static boolean hasIccCard(TelephonyManager tm) {
        if (!Margy.active() || realCard(tm)) return tm != null && tm.hasIccCard();
        return true;
    }

    /** Roaming would tell the app the SIM's country and the network's disagree. */
    public static boolean isNetworkRoaming(TelephonyManager tm) {
        if (!Margy.active()) return tm != null && tm.isNetworkRoaming();
        return false;
    }

    // ------------------------------------------- whether there is a card at all

    /**
     * How many cards the phone has, as far as the app is concerned.
     *
     * Answering the questions about the card is not enough on a phone with no
     * card in it. The app asks how many there are first, is told none, and
     * never asks any of the rest -- so a region chosen here did nothing at all
     * unless a real SIM happened to be in the tray.
     */
    public static int getPhoneCount(TelephonyManager tm) {
        if (!Margy.active() || realCard(tm)) return tm == null ? 0 : tm.getPhoneCount();
        return 1;
    }

    public static int getActiveModemCount(TelephonyManager tm) {
        if (!Margy.active() || realCard(tm)) {
            return tm == null ? 0 : tm.getActiveModemCount();
        }
        return 1;
    }

    /** GSM, because every network the list offers is one. */
    public static int getPhoneType(TelephonyManager tm) {
        if (!Margy.active() || realCard(tm)) {
            return tm == null ? TelephonyManager.PHONE_TYPE_NONE : tm.getPhoneType();
        }
        return TelephonyManager.PHONE_TYPE_GSM;
    }

    public static int getActiveSubscriptionInfoCount(
            android.telephony.SubscriptionManager subs) {
        if (!Margy.active()) return subs == null ? 0 : subs.getActiveSubscriptionInfoCount();
        return 1;
    }

    /**
     * Which subscription is the default one.
     *
     * With no card these answer INVALID_SUBSCRIPTION_ID, and code that asks
     * usually gives up on the spot. The first slot is what a phone with one
     * card answers.
     */
    public static int getDefaultDataSubscriptionId() {
        if (!Margy.active()) {
            return android.telephony.SubscriptionManager.getDefaultDataSubscriptionId();
        }
        return 1;
    }

    public static int getDefaultVoiceSubscriptionId() {
        if (!Margy.active()) {
            return android.telephony.SubscriptionManager.getDefaultVoiceSubscriptionId();
        }
        return 1;
    }

    public static int getDefaultSmsSubscriptionId() {
        if (!Margy.active()) {
            return android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId();
        }
        return 1;
    }

    public static int getActiveDataSubscriptionId() {
        if (!Margy.active()) {
            return android.telephony.SubscriptionManager.getActiveDataSubscriptionId();
        }
        return 1;
    }

    /**
     * The carrier id is a number in Google's own carrier list, and there is no
     * honest way to pick one for a carrier we are only claiming to be on.
     * Unknown is what a phone says when the list has no answer either.
     */
    public static int getSimCarrierId(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? -1 : tm.getSimCarrierId();
        return TelephonyManager.UNKNOWN_CARRIER_ID;
    }
}
