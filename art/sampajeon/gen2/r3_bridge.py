"""Access to the preserved 보부상 art (race_r3_kite / hero_r3_kite) for 조선 공방.

hero_r3_kite imports `race_r3`, which now belongs to 오방 신당, so that name is aliased to the snapshot here.
Both snapshots only write files under __main__, so importing them never touches design2/project.
"""
import re
import sys

import race_r3_kite as R

sys.modules["race_r3"] = R
import hero_r3_kite as H  # noqa: E402


def to_prefix(html, base):
    """Rewrite the snapshot namespaces (r3-, r3k-, h3-) to an own prefix so they cannot collide on the artboard."""
    html = re.sub(r'(id="|#)r3k-', rf"\g<1>{base}k-", html)
    html = re.sub(r'(id="|#)[rh]3-', rf"\g<1>{base}-", html)
    return html
