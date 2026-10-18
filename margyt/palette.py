"""Which colours the accent takes over, and what it turns them into.

TikTok is not built around one pink. `#FE2C55` is the one in the brand guide
and the one the bytecode spells out, but the resources hold it at a dozen
opacities and surround it with a family of neighbours -- `#FF1764` fifty times,
`#ED3495` forty-four, and the reds of Shop, LIVE and the gradients behind them.
Repainting only the exact value leaves nine tenths of the red on screen, which
is what the first build did.

So the rule is a zone rather than a value, and the mapping is relative rather
than flat:

* a colour is taken over when its hue is within `HUE` degrees of the reference
  and it is saturated and bright enough to read as part of the family -- grey,
  black and white are near every hue and belong to none;
* it is moved by the same step that takes the reference to the accent, in hue,
  saturation and value, so the reference lands exactly on the accent, a lighter
  member of the family stays lighter, and the two ends of a gradient stay two
  ends of a gradient;
* alpha is never touched.

Only *typed* colours go through here -- entries in the resource table and
attributes in compiled XML, where the format says "this is a colour". An int in
the bytecode is just an int, and widening the rule there would repaint whatever
number happened to look pink.

`inject/java/cat/narezany/margyt/Palette.java` is the same arithmetic in Java,
for the colours that are only known while the app is running. The two have to
agree; `tests/test_margyt.py` pins the cases that say so.
"""

from __future__ import annotations

import colorsys
from typing import Tuple

# How far from the reference hue still counts as the same family, in degrees.
# Twenty reaches TikTok's magentas and its Shop reds without touching its cyan,
# which is the other half of the brand and has to stay where it is.
HUE = 20.0

# Below these a colour has no hue worth speaking of: near-black, near-white and
# the greys sit at some arbitrary hue and must not be dragged along.
MIN_SATURATION = 0.35
MIN_VALUE = 0.35


def _split(colour: int) -> Tuple[int, float, float, float]:
    alpha = (colour >> 24) & 0xFF
    red = ((colour >> 16) & 0xFF) / 255.0
    green = ((colour >> 8) & 0xFF) / 255.0
    blue = (colour & 0xFF) / 255.0
    hue, saturation, value = colorsys.rgb_to_hsv(red, green, blue)
    return alpha, hue, saturation, value


def _join(alpha: int, hue: float, saturation: float, value: float) -> int:
    red, green, blue = colorsys.hsv_to_rgb(hue % 1.0, _clamp(saturation), _clamp(value))
    return ((alpha & 0xFF) << 24
            | int(round(red * 255)) << 16
            | int(round(green * 255)) << 8
            | int(round(blue * 255)))


def _clamp(value: float) -> float:
    return 0.0 if value < 0.0 else (1.0 if value > 1.0 else value)


def captures(colour: int, reference: int) -> bool:
    """Whether `colour` belongs to the reference's family.

    The reference itself is always in it, at any opacity and whatever it is:
    the thresholds below are there to keep unrelated greys from being dragged
    along with the neighbours, not to argue about the colour being replaced.
    """
    if colour & 0xFFFFFF == reference & 0xFFFFFF:
        return True

    _alpha, hue, saturation, value = _split(colour)
    _ref_alpha, ref_hue, _ref_saturation, _ref_value = _split(reference)
    if reference & 0xFFFFFF == 0xFE2C55 and saturation >= 0.06 and value >= 0.25 and (hue * 360 >= 300 or hue * 360 <= 10):
        return True
    if saturation < MIN_SATURATION or value < MIN_VALUE:
        return False
    apart = abs(hue - ref_hue)
    apart = min(apart, 1.0 - apart) * 360.0
    return apart <= HUE


def map_colour(colour: int, reference: int, accent: int) -> int:
    """Move `colour` by the step that takes `reference` to `accent`.

    Untouched if it is not in the family, and exactly `accent` -- alpha aside --
    if it is the reference itself, which is what keeps the brand colour landing
    on the colour the person actually chose.
    """
    if reference == accent or not captures(colour, reference):
        return colour
    # already the accent: one inside the family would be moved again each pass
    if colour & 0xFFFFFF == accent & 0xFFFFFF:
        return colour

    alpha, hue, saturation, value = _split(colour)
    _ra, ref_hue, ref_saturation, ref_value = _split(reference)
    _aa, accent_hue, accent_saturation, accent_value = _split(accent)

    hue = accent_hue + (hue - ref_hue)
    # a ratio rather than a difference: half as saturated as the pink comes out
    # half as saturated as the accent, whatever the accent happens to be
    saturation = accent_saturation * (saturation / ref_saturation) if ref_saturation else accent_saturation
    value = accent_value * (value / ref_value) if ref_value else accent_value
    return _join(alpha, hue, saturation, value)
