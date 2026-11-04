import sys; sys.path.insert(0,'.')
import r1_figs as F
from r1_lib import defs
items=[F.gunner(), F.gunner(aim=True), F.artisan(), F.artisan(swing=-38), F.crewman(), '<g transform="translate(0 -60)">'+F.pilot()+'</g>', '<g transform="translate(0 -40)">'+F.fallen_gear()+'</g>']
cells="".join(f'<svg width="300" height="420" viewBox="-75 -185 150 210" style="background:#3A3226;margin:4px">{it}</svg>' for it in items)
html=f"<html><body style='margin:0;background:#222'>{defs()}<svg width='0' height='0' style='position:absolute'><defs>{F.KK.defs_svg()}</defs></svg>{cells}</body></html>"
open("../shots/r1_figprev.html","w").write(html)
