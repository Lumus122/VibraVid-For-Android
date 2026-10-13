"""Read the release identity from Gradle so reports cannot label a different build."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
CONFIG = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
VERSION = re.search(r"versionName\s+'([^']+)'", CONFIG).group(1)
VERSION_CODE = int(re.search(r'versionCode\s+(\d+)', CONFIG).group(1))
LOG_VERSION = VERSION.split('-')[0].replace('.', '')
