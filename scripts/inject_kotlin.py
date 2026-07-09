import re

KOTLIN_VERSION = "2.3.21"

def apply(filepath):
    with open(filepath) as f:
        text = f.read()
    orig = text

    # Upgrade any explicit kotlin version to target version
    text = re.sub(r'kotlin\("jvm"\) version "[^"]+"', f'kotlin("jvm") version "{KOTLIN_VERSION}"', text)

    # Add kotlin plugin after 'idea' if not present at all
    if 'kotlin("jvm")' not in text:
        text = re.sub(r'^    idea\b', f'    idea\n    kotlin("jvm") version "{KOTLIN_VERSION}"', text, count=1, flags=re.MULTILINE)

    # Add kotlin {} config after first standalone '}' (plugins block) if not present
    if 'jvmToolchain' not in text:
        text = re.sub(r'^}\s*$', '}\n\nkotlin {\n    jvmToolchain(21)\n}', text, count=1, flags=re.MULTILINE)

    if text != orig:
        with open(filepath, 'w') as f:
            f.write(text)
        print(f"{filepath}: updated")
    else:
        print(f"{filepath}: OK")

for f in ["mili-api/build.gradle.kts", "mili-server/build.gradle.kts"]:
    apply(f)
