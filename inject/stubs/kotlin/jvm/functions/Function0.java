package kotlin.jvm.functions;

/**
 * Kotlin's no-argument function, in outline.
 *
 * A real name on a real interface -- it is Kotlin's own, not TikTok's, and it
 * will be spelled this way for as long as the app is written in Kotlin. It is
 * here so that a rewritten call can be handed back with the types the verifier
 * saw; the mod never calls one.
 */
public interface Function0<R> {

    R invoke();
}
