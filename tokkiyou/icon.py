"""Swapping the launcher icon without touching a resource id.

The apk already has an icon: a resource id, a file for every density, and for
Android 8 and up an adaptive icon pointing at two more. Every one of those is
a path in the zip, so the icon changes by rewriting what is at those paths --
the id, the table entry and the density it was chosen for all stay exactly as
TikTok left them.

Raster launcher icons use the supplied artwork at each existing density.
Adaptive icon layers use that same artwork as the background bitmap and a
transparent foreground bitmap, so Android does not independently enlarge a
traced logo over the original image.
"""

from __future__ import annotations

from typing import List, Optional

from . import artwork, png, vector
from .apkzip import Apk
from .arsc import Arsc, Value
from .axml import Axml, TYPE_REFERENCE

COLOUR_TYPES = (0x1C, 0x1D, 0x1E, 0x1F)


class Icon:
    """Replaces every file behind an icon resource, reporting as it goes."""

    def __init__(self, apk: Apk, arsc: Arsc, master_png: bytes):
        self.apk = apk
        self.arsc = arsc
        self.master = png.decode(master_png)
        self.log: List[str] = []
        self._scaled = {}

    # ------------------------------------------------------------ the sizes

    def bitmap(self, size: int) -> bytes:
        if size not in self._scaled:
            self._scaled[size] = png.encode(self.master.resized(size))
        return self._scaled[size]

    @staticmethod
    def transparent_bitmap(size: int) -> bytes:
        return png.encode(png.Image(size, size, bytearray(size * size * 4)))

    # ------------------------------------------------------------- the work

    def replace(self, res_id: int) -> None:
        for value in self.arsc.values(res_id):
            path = self.arsc.file_path(value)
            if path is None:
                self.log.append("  0x%08x: not a file, left alone" % res_id)
                continue
            self._replace_file(path, value, layer=None)

    def _replace_file(self, path: str, value: Value, layer: Optional[str]) -> None:
        if not self.apk.has(path):
            self.log.append("  %s: named by the table, missing from the apk" % path)
            return
        if path.endswith(".png"):
            self._replace_png(path, layer)
        elif path.endswith(".xml"):
            self._replace_xml(path, value, layer)
        else:
            self.log.append("  %s: not a drawable this build knows" % path)

    def _replace_png(self, path: str, layer: Optional[str]) -> None:
        width, height = png.size_of(self.apk.read(path))
        size = max(width, height)
        self.apk.replace(path, self.bitmap(size))
        self.log.append("  %s: %dx%d bitmap" % (path, size, size))

    def _replace_xml(self, path: str, value: Value, layer: Optional[str]) -> None:
        """An XML drawable: an adaptive icon to follow, or a vector to replace."""
        if layer is None:
            try:
                axml = Axml.parse(self.apk.read(path))
                root = next(
                    (axml.pool.get(n.name) for n in axml.nodes if n.kind == 0x0102), None
                )
            except Exception:
                root = None
            if root == "adaptive-icon":
                self._replace_adaptive(path, axml)
                return

        paths = {
            "background": artwork.BACKGROUND,
            "foreground": artwork.GLYPH,
            None: artwork.COMBINED,
        }[layer]
        self.apk.replace(path, vector.build(artwork.VIEWPORT, paths))
        self.log.append("  %s: vector (%s)" % (path, layer or "whole icon"))

    def _replace_adaptive(self, path: str, axml: Axml) -> None:
        self.log.append("  %s: adaptive icon, following its layers" % path)
        for layer in ("background", "foreground"):
            for node in axml.elements(layer):
                attr = axml.attr(node, "drawable")
                if attr is None:
                    self.log.append("    <%s> draws something inline, left alone" % layer)
                    continue
                if attr.kind != TYPE_REFERENCE:
                    self.log.append("    <%s> is not a reference, left alone" % layer)
                    continue
                self._replace_layer(attr.data, layer)

    def _replace_layer(self, res_id: int, layer: str) -> None:
        values = self.arsc.values(res_id)
        if not values:
            self.log.append("    <%s> 0x%08x: nothing in the table" % (layer, res_id))
            return
        for value in values:
            if value.kind in COLOUR_TYPES:
                self.arsc.set_value(value, value.kind, artwork.MINT)
                self.log.append("    <%s> a colour, repainted mint" % layer)
                continue
            path = self.arsc.file_path(value)
            if path is None:
                self.log.append("    <%s> 0x%08x: not a file" % (layer, res_id))
                continue
            if path.endswith(".xml") and layer in ("background", "foreground"):
                bitmap_path = path[:-4] + ".png"
                self.arsc.replace_string(value.data, bitmap_path)
                image = (self.bitmap(512) if layer == "background"
                         else self.transparent_bitmap(512))
                self.apk.add(bitmap_path, image)
                self.log.append("    <%s> %s: full-image bitmap" % (layer, bitmap_path))
                continue
            self._replace_file(path, value, layer)


def replace_everywhere(apk: Apk, arsc: Arsc, manifest: Axml, master_png: bytes) -> List[str]:
    """Redraw every icon the manifest points the launcher at."""
    from . import manifest as manifest_module

    icon = Icon(apk, arsc, master_png)
    for res_id in manifest_module.icon_ids(manifest):
        icon.log.append("  icon resource 0x%08x" % res_id)
        icon.replace(res_id)
    return icon.log
