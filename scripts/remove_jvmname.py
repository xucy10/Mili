from pathlib import Path
import re

base = Path(r'E:\Program Files\Tencent\AndrowsData\Mili\mili-server\src\main\kotlin\fun\bm\mili')
for f in base.rglob('*.kt'):
    text = f.read_text(encoding='utf-8')
    new_text = re.sub(r'@file:JvmName\("[^"]+"\)\s*\n', '', text)
    if new_text != text:
        f.write_text(new_text, encoding='utf-8')
        print(f"removed @file:JvmName from {f}")
