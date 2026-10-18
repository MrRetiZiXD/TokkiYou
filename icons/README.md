# Icons

Google's Material Design Icons, at 24dp, taken from
[google/material-design-icons](https://github.com/google/material-design-icons)
and used under the **Apache License 2.0**.

They are here as files rather than as resources because this build adds no
resources to somebody else's apk: `tokkiyou/build.py` writes them into
`inject/java/cat/narezany/tokkiyou/Icons.java` as base64, and the mod decodes
and tints them at runtime.

Adding one is downloading it here and naming it in `SettingsActivity`.
