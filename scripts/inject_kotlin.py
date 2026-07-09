import re

def apply(filepath):
    with open(filepath) as f:
        text = f.read()
    orig = text

    # Remove explicit version (root provides it via apply false)
    text = re.sub(r'kotlin\("jvm"\) version "[^"]+"', 'kotlin("jvm")', text)

    # Add kotlin plugin after 'idea' if not present at all
    if 'kotlin("jvm")' not in text:
        text = re.sub(r'^    idea\b', '    idea\n    kotlin("jvm")', text, count=1, flags=re.MULTILINE)

    # Add kotlin {} config after first standalone '}' (plugins block) if missing
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
