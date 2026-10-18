package cat.narezany.patcher.core

import java.io.File
import kotlin.system.exitProcess

/**
 * The engine, run on a computer: what the app will do on a phone, done here
 * first, so that a patch is known to give the release before it is handed out.
 */
fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("usage: BASE.apk PATCH.mpatch OUT.apk")
        exitProcess(2)
    }
    val (base, patch, out) = args.map(::File)
    val started = System.currentTimeMillis()
    val about = Patch.about(patch)
    if (Patch.sha256(base) != about.baseSha256) {
        System.err.println("this is not the apk the patch is for")
        exitProcess(1)
    }
    val checked = System.currentTimeMillis()
    Patch.apply(base, patch, out)
    val made = System.currentTimeMillis()
    println("the release apk, byte for byte: %d MB, checked in %.1f s, made in %.1f s".format(
        out.length() / 1_000_000, (checked - started) / 1000.0, (made - checked) / 1000.0))
}
