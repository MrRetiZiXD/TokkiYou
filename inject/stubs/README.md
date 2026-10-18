# Stubs

TikTok's own classes, in outline, so the mod can be compiled against them.

Only the shape matters: the name, and the signatures the mod calls or is
rewritten into. The bodies are never used — these classes are on javac's
classpath and on d8's, and **not** in the dex that ships. The real ones are in
the apk, and a second copy under the same name would be a different class as
far as the runtime is concerned.

Every signature here was read out of the apk's own method table rather than
guessed, and the build fails if a rewrite lands on something the mod does not
define. What it cannot check is that TikTok still declares them: a release that
renames one leaves a rewrite with no call sites, which the build reports.
