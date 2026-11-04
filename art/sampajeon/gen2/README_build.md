# Building artboards

The Design canvas silently drops any file over 2 MiB when it loads. After running a generator, shrink its output:

    python3 shrink_dc.py ../design2/project/<Name>.dc.html
    python3 shrink_dc.py ../design2/project/Terrain.dc.html --merge   # Terrain needs the tighter mode

Files that need it today: RaceR1, RaceR3, Match, Terrain. RaceR2 is 1.96 MiB unshrunk, so any addition there needs it too.
