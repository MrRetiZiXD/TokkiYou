"""The edits TokkiYou makes to AndroidManifest.xml.

Three of them, and the package name is not one: the apk keeps the package it
was built with. Renaming it is what makes a repackaged TikTok interesting to
debug -- provider authorities collide, the OAuth redirect scheme is claimed
twice, and every `com.zhiliaoapp.musically.something` string in fifty-two dex
files is suddenly half true.
"""

from __future__ import annotations

from typing import List, Optional

from .axml import (
    Axml,
    Node,
    RES_XML_END_ELEMENT,
    RES_XML_START_ELEMENT,
    TYPE_REFERENCE,
    TYPE_STRING,
)

MAIN_ACTION = "android.intent.action.MAIN"
LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"


class ManifestError(Exception):
    pass


def application(axml: Axml) -> Node:
    elements = axml.elements("application")
    if not elements:
        raise ManifestError("the manifest has no <application>")
    return elements[0]


def package_name(axml: Axml) -> Optional[str]:
    return axml.attr_string(axml.elements("manifest")[0], "package")


def application_class(axml: Axml) -> Optional[str]:
    return axml.attr_string(application(axml), "name")


def min_sdk(axml: Axml, fallback: int = 21) -> int:
    """The oldest Android the apk is built for.

    Everything the build produces has to be readable that far back: a dex
    assembled for a newer api is stamped with a newer format, and an Android
    that does not know the format refuses the whole app rather than the file.
    """
    for node in axml.elements("uses-sdk"):
        attr = axml.attr(node, "minSdkVersion")
        if attr is not None and attr.kind != TYPE_STRING:
            return attr.data
    return fallback


def icon_ids(axml: Axml) -> List[int]:
    """The resource ids the launcher will actually draw, without duplicates."""
    app = application(axml)
    out = []
    for name in ("icon", "roundIcon"):
        attr = axml.attr(app, name)
        if attr is not None and attr.kind == TYPE_REFERENCE and attr.data not in out:
            out.append(attr.data)
    return out


def launcher_elements(axml: Axml) -> List[Node]:
    """Every activity or alias that puts an entry on the launcher."""
    stack: List[Node] = []
    found: dict = {}
    for node in axml.nodes:
        if node.kind == RES_XML_START_ELEMENT:
            stack.append(node)
            name = axml.pool.get(node.name)
            if name in ("action", "category"):
                value = axml.attr_string(node, "name")
                if value in (MAIN_ACTION, LAUNCHER_CATEGORY):
                    for parent in reversed(stack):
                        if axml.pool.get(parent.name) in ("activity", "activity-alias"):
                            found.setdefault(id(parent), [parent, set()])[1].add(value)
                            break
        elif node.kind == RES_XML_END_ELEMENT:
            if stack:
                stack.pop()
    return [node for node, kinds in found.values() if len(kinds) == 2]


def shared_authorities(axml: Axml, package: str) -> List[str]:
    """Provider authorities that are not spelled with the package name.

    Android will not install two apps that claim the same provider authority,
    and most of TikTok's authorities are safe because they are built out of the
    package name -- rename the package and they follow. These do not: they are
    written out in full, identical in every apk built from this one. Keeping
    TikTok's package name means keeping them too, and then any other mod of the
    same app on the phone is enough to have the installer refuse this one.
    """
    out = []
    for node in axml.elements("provider"):
        value = axml.attr_string(node, "authorities")
        if not value:
            continue
        for authority in value.split(";"):
            if authority and package not in authority and authority not in out:
                out.append(authority)
    return out


def rename_authorities(axml: Axml, renames: dict) -> List[str]:
    """Rewrite provider authorities according to `renames`."""
    touched = []
    for node in axml.elements("provider"):
        value = axml.attr_string(node, "authorities")
        if not value:
            continue
        parts = [renames.get(part, part) for part in value.split(";")]
        new_value = ";".join(parts)
        if new_value != value:
            axml.set_attr_string(node, "authorities", new_value)
            touched.append(new_value)
    return touched


def set_label(axml: Axml, label: str) -> List[str]:
    """Put `label` under the icon, wherever the launcher would read it from."""
    touched = []
    app = application(axml)
    axml.set_attr_string(app, "label", label)
    touched.append("application")
    for node in launcher_elements(axml):
        if axml.attr(node, "label") is not None:
            axml.set_attr_string(node, "label", label)
            touched.append(axml.pool.get(node.name) + " " + str(axml.attr_string(node, "name")))
    return touched


def has_activity(axml: Axml, class_name: str) -> bool:
    return any(
        axml.attr_string(node, "name") == class_name
        for node in axml.elements("activity") + axml.elements("activity-alias")
    )


def has_permission(axml: Axml, name: str) -> bool:
    return any(axml.attr_string(node, "name") == name
               for node in axml.elements("uses-permission"))


def add_permission(axml: Axml, name: str) -> bool:
    """Ask for one more permission, as a child of <manifest>.

    Returns whether anything was added: an apk that already asks for it is
    left exactly as it was.
    """
    if has_permission(axml, name):
        return False
    node = axml.make_element("uses-permission")
    axml.set_attr_string(node, "name", name)
    axml.insert_into(axml.elements("manifest")[0], [node, axml.close_element(node)])
    return True


def version_name(axml: Axml) -> str:
    """What the apk calls its own version, for the mod to write down."""
    for node in axml.elements("manifest"):
        found = axml.attr_string(node, "versionName")
        if found:
            return found
    return "?"


def version_code(axml: Axml) -> int:
    """The numeric install version, kept monotonic across margyt releases."""
    for node in axml.elements("manifest"):
        found = axml.attr(node, "versionCode")
        if found is not None and found.kind != TYPE_STRING:
            return found.data
    return 1


def set_version_code(axml: Axml, value: int) -> None:
    if value < 1 or value > 0x7FFFFFFF:
        raise ManifestError("versionCode must be between 1 and 2,147,483,647")
    node = axml.elements("manifest")[0]
    axml.set_attr(node, "versionCode", 0x10, value)


def add_provider(axml: Axml, class_name: str, authority: str) -> None:
    """Declare a provider, which is how the mod gets to run at start-up.

    Android builds every declared provider before the application's own
    onCreate, so one that answers nothing is still a hook -- and one that needs
    no patch to TikTok's own Application class to exist.
    """
    provider = axml.make_element("provider")
    axml.set_attr_string(provider, "name", class_name)
    axml.set_attr_bool(provider, "exported", False)
    axml.set_attr_string(provider, "authorities", authority)
    # the same provider hands the installer the apk of an update: a content uri
    # the mod grants read on for that one intent, which is what a FileProvider
    # would have done had adding its xml been possible here
    axml.set_attr_bool(provider, "grantUriPermissions", True)
    axml.insert_into(application(axml), [provider, axml.close_element(provider)])


def add_meta_data(axml: Axml, name: str, value: str) -> None:
    """A named word about the app that another app may read.

    The patcher app reads the mod's version this way: an installed app says
    nothing else about itself that a stranger can ask for.
    """
    data = axml.make_element("meta-data")
    axml.set_attr_string(data, "name", name)
    axml.set_attr_string(data, "value", value)
    axml.insert_into(application(axml), [data, axml.close_element(data)])


LAUNCH_SINGLE_TASK = 2


def launcher_entry(axml: Axml):
    """What the home screen opens, as (the component, the activity behind it).

    TikTok's own entry is not an activity at all -- it is an `activity-alias`
    called SplashActivity standing in front of MainActivity. Which is the same
    trick the icon switcher uses, so the two halves of the answer are both
    wanted: the component to turn off when another icon is chosen, and the
    activity every new alias should point at.

    Read by walking the node stream rather than by asking for children: the
    parsed manifest is a flat run of opens and closes.
    """
    from .axml import RES_XML_START_ELEMENT, RES_XML_END_ELEMENT

    name = target = None
    inside = None
    saw_main = saw_launcher = False
    for node in axml.nodes:
        if node.kind == RES_XML_START_ELEMENT:
            tag = axml.pool.get(node.name)
            if tag in ("activity", "activity-alias"):
                inside = tag
                name = axml.attr_string(node, "name")
                target = axml.attr_string(node, "targetActivity") or name
                saw_main = saw_launcher = False
            elif inside and tag in ("action", "category"):
                value = axml.attr_string(node, "name")
                saw_main |= value == MAIN_ACTION
                saw_launcher |= value == LAUNCHER_CATEGORY
        elif node.kind == RES_XML_END_ELEMENT:
            if axml.pool.get(node.name) == inside:
                if saw_main and saw_launcher and name and target:
                    return name, target
                inside = None
    raise ManifestError("this apk has no launcher entry to stand in for")


def add_icon_alias(axml: Axml, name: str, target: str, icon: int,
                   label: str, enabled: bool) -> None:
    """An `activity-alias` that is the same app under a different icon.

    Which icon an app wears is a property of the component the home screen
    opens, and there is no way to change one at runtime -- so an app that
    offers a choice ships one component per icon and turns on the one that was
    chosen. They all point at the same activity, so whichever is on, tapping it
    opens the app exactly as before.
    """
    alias = axml.make_element("activity-alias")
    axml.set_attr(alias, "icon", TYPE_REFERENCE, icon)
    axml.set_attr_string(alias, "label", label)
    axml.set_attr_string(alias, "name", name)
    axml.set_attr_bool(alias, "enabled", enabled)
    axml.set_attr_bool(alias, "exported", True)
    axml.set_attr_string(alias, "targetActivity", target)

    intent_filter = axml.make_element("intent-filter")
    action = axml.make_element("action")
    axml.set_attr_string(action, "name", MAIN_ACTION)
    category = axml.make_element("category")
    axml.set_attr_string(category, "name", LAUNCHER_CATEGORY)

    axml.insert_into(application(axml), [
        alias,
        intent_filter,
        action,
        axml.close_element(action),
        category,
        axml.close_element(category),
        axml.close_element(intent_filter),
        axml.close_element(alias),
    ])


def add_activity(axml: Axml, class_name: str, label: str, theme: int,
                 affinity: str = None, launcher: bool = False) -> None:
    """Declare an exported activity, last child of <application>.

    With `launcher` it also gets an entry on the home screen -- and then it
    needs a task affinity of its own, because without one it shares the app's,
    and tapping its icon while TikTok is running does what tapping a launcher
    entry does: brings that task back to the front, TikTok and all, instead of
    opening this screen.

    Without one none of that applies and none of it is written. The screen is
    opened from inside the app, in the app's own task, so the back button goes
    back to where it was opened from.
    """
    for existing in axml.elements("activity"):
        if axml.attr_string(existing, "name") == class_name:
            raise ManifestError("%s is already declared" % class_name)

    activity = axml.make_element("activity")
    axml.set_attr(activity, "theme", TYPE_REFERENCE, theme)
    axml.set_attr_string(activity, "label", label)
    axml.set_attr_string(activity, "name", class_name)
    axml.set_attr_bool(activity, "exported", True)

    if not launcher:
        axml.insert_into(application(axml), [activity, axml.close_element(activity)])
        return

    axml.set_attr_string(activity, "taskAffinity", affinity)
    axml.set_attr(activity, "launchMode", 0x10, LAUNCH_SINGLE_TASK)

    intent_filter = axml.make_element("intent-filter")
    action = axml.make_element("action")
    axml.set_attr_string(action, "name", MAIN_ACTION)
    category = axml.make_element("category")
    axml.set_attr_string(category, "name", LAUNCHER_CATEGORY)

    subtree = [
        activity,
        intent_filter,
        action,
        axml.close_element(action),
        category,
        axml.close_element(category),
        axml.close_element(intent_filter),
        axml.close_element(activity),
    ]
    axml.insert_into(application(axml), subtree)


def add_widget(axml: Axml, class_name: str, info: int) -> None:
    """Declare a home-screen widget: a receiver, and where its description is.

    The launcher finds widgets by the one broadcast they all answer, and reads
    what a widget is from the resource the meta-data points at. It is another
    app doing the asking, so this receiver is exported where the alarm's is
    not.
    """
    receiver = axml.make_element("receiver")
    axml.set_attr_string(receiver, "name", class_name)
    axml.set_attr_bool(receiver, "exported", True)
    intent_filter = axml.make_element("intent-filter")
    action = axml.make_element("action")
    axml.set_attr_string(action, "name", "android.appwidget.action.APPWIDGET_UPDATE")
    meta = axml.make_element("meta-data")
    axml.set_attr_string(meta, "name", "android.appwidget.provider")
    axml.set_attr_ref(meta, "resource", info)
    axml.insert_into(application(axml), [
        receiver, intent_filter, action, axml.close_element(action),
        axml.close_element(intent_filter), meta, axml.close_element(meta),
        axml.close_element(receiver)])


def add_receiver(axml: Axml, class_name: str, actions: List[str]) -> None:
    """Declare a receiver that only the system (and the app itself) can reach.

    An alarm set with AlarmManager outlives the process: when it goes off the
    system starts the app again and delivers the broadcast here. A receiver
    registered in code would have died with the process.
    """
    receiver = axml.make_element("receiver")
    axml.set_attr_string(receiver, "name", class_name)
    axml.set_attr_bool(receiver, "exported", False)
    intent_filter = axml.make_element("intent-filter")
    subtree = [receiver, intent_filter]
    for name in actions:
        action = axml.make_element("action")
        axml.set_attr_string(action, "name", name)
        subtree += [action, axml.close_element(action)]
    subtree += [axml.close_element(intent_filter), axml.close_element(receiver)]
    axml.insert_into(application(axml), subtree)
