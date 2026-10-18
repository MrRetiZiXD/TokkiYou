"""Generates ttcuz settings icons (24x24, filled outlines, only M/L/C/Z so SettingsGlyph can parse them).
Each stroke is a union of round capsules; all capsules share one winding so non-zero fill merges them."""
import math, json, sys
R = 1.0
K = 0.5523

def f(v):
    s = f"{v:.2f}".rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

def capsule(p1, p2, r=R):
    dx, dy = p2[0]-p1[0], p2[1]-p1[1]
    L = math.hypot(dx, dy)
    if L < 1e-6: dx, dy, L = 1, 0, 1
    d = (dx/L, dy/L); n = (-d[1], d[0])
    def pt(c, a, b): return (c[0]+r*(a[0]*1)+0, c[1]+r*a[1]) if False else (c[0]+r*a[0], c[1]+r*a[1])
    def arc(c, u, v):
        a = (c[0]+r*u[0], c[1]+r*u[1]); b = (c[0]+r*v[0], c[1]+r*v[1])
        c1 = (c[0]+r*(u[0]+K*v[0]), c[1]+r*(u[1]+K*v[1]))
        c2 = (c[0]+r*(v[0]+K*u[0]), c[1]+r*(v[1]+K*u[1]))
        return c1, c2, b
    neg = lambda v: (-v[0], -v[1])
    out = []
    s = (p1[0]+r*n[0], p1[1]+r*n[1])
    out.append(('M', [s]))
    out.append(('L', [(p2[0]+r*n[0], p2[1]+r*n[1])]))
    for u, v in ((n, d), (d, neg(n))):
        c1, c2, b = arc(p2, u, v); out.append(('C', [c1, c2, b]))
    out.append(('L', [(p1[0]-r*n[0], p1[1]-r*n[1])]))
    for u, v in ((neg(n), neg(d)), (neg(d), n)):
        c1, c2, b = arc(p1, u, v); out.append(('C', [c1, c2, b]))
    out.append(('Z', []))
    return out

def area(pts): return sum(pts[i][0]*pts[(i+1) % len(pts)][1]-pts[(i+1) % len(pts)][0]*pts[i][1] for i in range(len(pts)))/2
CAP_SIGN = area([(0, 0), (1, 0), (1, -1), (0, -1)])  # placeholder, calibrated below
# calibrate: capsule corner polygon p1+n, p2+n, p2-n, p1-n for d=(1,0) -> n=(0,1)
CAP_SIGN = 1 if area([(0, 1), (1, 1), (1, -1), (0, -1)]) > 0 else -1

def poly_fill(points):
    pts = list(points)
    if (area(pts) > 0) != (CAP_SIGN > 0): pts.reverse()
    out = [('M', [pts[0]])] + [('L', [p]) for p in pts[1:]] + [('Z', [])]
    return out

class Icon:
    def __init__(self): self.cmds = []
    def line(self, *pts, r=R):
        for a, b in zip(pts, pts[1:]): self.cmds += capsule(a, b, r)
        return self
    def poly(self, *pts, r=R):
        return self.line(*pts, pts[0], r=r)
    def arc(self, c, rad, a0, a1, r=R, step=18, ry=None):
        ry = rad if ry is None else ry
        n = max(2, int(abs(a1-a0)/step)+1)
        pts = [(c[0]+rad*math.cos(math.radians(a0+(a1-a0)*i/n)), c[1]-ry*math.sin(math.radians(a0+(a1-a0)*i/n))) for i in range(n+1)]
        return self.line(*pts, r=r), pts
    def ring(self, c, rad, r=R, ry=None, step=24):
        self.arc(c, rad, 0, 360, r=r, step=step, ry=ry); return self
    def disc(self, c, rad):
        self.cmds += capsule(c, (c[0]+0.001, c[1]), rad); return self
    def fill(self, *pts): self.cmds += poly_fill(pts); return self
    def chev(self, tip, d, size=3.4, ang=45, r=R):
        L = math.hypot(*d); d = (d[0]/L, d[1]/L)
        arms = []
        for s in (1, -1):
            a = math.radians(ang*s); rx = d[0]*math.cos(a)-d[1]*math.sin(a); ry_ = d[0]*math.sin(a)+d[1]*math.cos(a)
            arms.append((tip[0]-size*rx, tip[1]-size*ry_))
        return self.line(arms[0], tip, arms[1], r=r)
    def d(self):
        parts = []
        for c, pts in self.cmds: parts.append(c + (' ' + ' '.join(f(x)+' '+f(y) for x, y in pts) if pts else ''))
        return ' '.join(parts)

icons = {}
def make(name):
    def deco(fn):
        i = Icon(); fn(i); icons[name] = i.d(); return fn
    return deco

@make('add')
def _(i): i.line((12, 5), (12, 19)).line((5, 12), (19, 12))

@make('edit')
def _(i):
    i.poly((5.2, 18.8), (5.9, 14.7), (15.6, 5), (19, 8.4), (9.3, 18.1))
    i.line((13.3, 7.3), (16.7, 10.7))

@make('restore')
def _(i):
    _, pts = i.arc((12, 12), 8, 200, 525, step=20)
    H = pts[-1]; th = math.radians(525)
    t = (-math.sin(th), -math.cos(th))
    i.chev(H, t, size=3.6, ang=42)
    i.line((12, 7.8), (12, 12.2), (15.2, 14.1))

@make('swap_horiz')
def _(i):
    i.line((4.5, 8), (19.5, 8)).chev((19.5, 8), (1, 0), 3.6)
    i.line((19.5, 16), (4.5, 16)).chev((4.5, 16), (-1, 0), 3.6)

@make('upload')
def _(i):
    i.line((12, 15.5), (12, 5)).chev((12, 5), (0, -1), 4.4)
    i.line((5, 15), (5, 19), (19, 19), (19, 15))

@make('flare')
def _(i):
    i.disc((12, 12), 2.6)
    for k in range(8):
        a = math.radians(45*k); lo, hi = (6.1, 9.6) if k % 2 == 0 else (5.9, 8.0)
        i.line((12+lo*math.cos(a), 12-lo*math.sin(a)), (12+hi*math.cos(a), 12-hi*math.sin(a)))

@make('animation')
def _(i):
    for c in ((8.7, 9.2), (15.3, 9.2), (12, 14.9)): i.ring(c, 4.3, step=26)

@make('bug_report')
def _(i):
    i.ring((12, 14.2), 4.4, ry=5.6, step=26)
    i.line((12, 9.5), (12, 19.8)).line((7.8, 13.2), (16.2, 13.2))
    i.line((4, 13.2), (7.6, 13.2)).line((16.4, 13.2), (20, 13.2))
    i.line((4.6, 18.6), (8, 17)).line((19.4, 18.6), (16, 17))
    i.line((4.6, 8), (8.3, 10)).line((19.4, 8), (15.7, 10))
    i.line((9.3, 3.8), (10.7, 6)).line((14.7, 3.8), (13.3, 6))

@make('speed')
def _(i):
    i.arc((12, 13.5), 8.6, 205, -25, step=18)
    i.line((12, 13.5), (16.4, 8.9)); i.disc((12, 13.5), 1.9)

def tri(i, pts):
    i.fill(*pts); i.poly(*pts, r=0.9)

@make('fast_forward')
def _(i):
    tri(i, [(4.6, 7.2), (4.6, 16.8), (11.4, 12)]); tri(i, [(12.6, 7.2), (12.6, 16.8), (19.4, 12)])

@make('fast_rewind')
def _(i):
    tri(i, [(19.4, 7.2), (19.4, 16.8), (12.6, 12)]); tri(i, [(11.4, 7.2), (11.4, 16.8), (4.6, 12)])

@make('open_with')
def _(i):
    i.line((12, 5), (12, 19)).line((5, 12), (19, 12))
    for tip, d in (((12, 3.8), (0, -1)), ((12, 20.2), (0, 1)), ((3.8, 12), (-1, 0)), ((20.2, 12), (1, 0))): i.chev(tip, d, 3.2)

if __name__ == "__main__":
    mode = sys.argv[1]
    if mode == 'json': json.dump(icons, open(sys.argv[2], 'w'), indent=1)
    if mode == 'svg':  # python make_settings_icons.py svg  -> writes assets/ui/settings/*.svg
        import pathlib
        out = pathlib.Path(__file__).parent / 'settings'
        for k, v in icons.items():
            (out / f'{k}.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24"><path d="{v}"/></svg>\n')
