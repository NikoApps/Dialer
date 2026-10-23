# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow==11.3.0"]
# ///
"""
Installs the debug build on every connected phone and gets it ready to try: permissions
granted, call screening role held (Android 10+), setup done, helper PIN 1234, and test
people with placeholder faces on the grid.

The numbers are fictional 555-01xx numbers, so a tap calls no one.

Needs a debug build: it writes into Dialer's own files with `run-as`, which only works on
a debuggable app. It replaces the people and settings already on the phone.

    uv run scripts/seed-device.py            # 4 people, every connected phone
    uv run scripts/seed-device.py --grid 6   # 6 people
    uv run scripts/seed-device.py -s R9JN70Z0QNJ --no-install
"""

import argparse
import base64
import hashlib
import io
import struct
import subprocess
import sys
import tempfile
import uuid
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PACKAGE = "io.github.nikoapps.dialer"
REPO = Path(__file__).resolve().parent.parent
APK = REPO / "app/build/outputs/apk/debug/app-debug.apk"
STAGING = "/data/local/tmp/niko-dialer-seed"

# Must match DialerHelperPin in settings/DialerHelperPin.kt.
PIN_SALT = "niko-dialer-helper-pin:"
PIN = "1234"

PEOPLE = [
    ("Mum", "+1 555 0101", "#B5452F"),
    ("Dad", "+1 555 0102", "#2F6DB5"),
    ("Sita", "+1 555 0103", "#3D8B4A"),
    ("Ram", "+1 555 0104", "#8A4FB0"),
    ("Doctor", "+1 555 0105", "#A87B1E"),
    ("Neighbour", "+1 555 0106", "#2E8C8C"),
]

PERMISSIONS = [
    "android.permission.CALL_PHONE",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.RECORD_AUDIO",
]
STORAGE_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE"  # Android 8 and 9 only


# DataStore Preferences file: a protobuf PreferenceMap { map<string, Value> preferences = 1 }.
# Value fields: boolean = 1, float = 2, integer = 3, string = 5.

def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def field(number: int, wire: int, payload: bytes) -> bytes:
    return varint(number << 3 | wire) + payload


def length_delimited(number: int, data: bytes) -> bytes:
    return field(number, 2, varint(len(data)) + data)


def value(v) -> bytes:
    if isinstance(v, bool):
        return field(1, 0, varint(int(v)))
    if isinstance(v, int):
        return field(3, 0, varint(v))
    if isinstance(v, float):
        return field(2, 5, struct.pack("<f", v))
    return length_delimited(5, v.encode())


def preferences(entries: dict) -> bytes:
    return b"".join(
        length_delimited(1, length_delimited(1, k.encode()) + length_delimited(2, value(v)))
        for k, v in entries.items()
    )


def b64(s: str) -> str:
    return base64.urlsafe_b64encode(s.encode()).decode().rstrip("=")


def person_id(name: str) -> str:
    # Stable across runs, so a second run replaces the same people and photos.
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"niko-dialer-test/{name}"))


def encode_people(people) -> str:
    """Same format as PeopleCodec.encode."""
    return ",".join(
        ";".join([str(place), b64(pid), b64(name), b64(number), "1", "", "0", "0", ""])
        for place, (pid, name, number, _) in enumerate(people)
    )


def face(initial: str, colour: str) -> bytes:
    """A 512 pixel square with a big initial, like PersonPhotos saves."""
    img = Image.new("RGB", (512, 512), colour)
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=300)
    draw.text((256, 256), initial, fill="white", font=font, anchor="mm")
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=90)
    return buf.getvalue()


def adb(serial: str, *args: str, data: bytes | None = None, check: bool = True) -> str:
    result = subprocess.run(["adb", "-s", serial, *args], input=data, capture_output=True)
    if check and result.returncode != 0:
        sys.exit(f"{serial}: adb {' '.join(args)} failed: {result.stderr.decode().strip()}")
    return result.stdout.decode(errors="replace").strip()


def write_app_file(serial: str, path: str, data: bytes) -> None:
    # Through /data/local/tmp, since `adb exec-in` into run-as writes nothing on some phones.
    with tempfile.NamedTemporaryFile() as f:
        f.write(data)
        f.flush()
        adb(serial, "push", f.name, STAGING)
    adb(serial, "shell", f"chmod 644 {STAGING} && run-as {PACKAGE} sh -c 'cat {STAGING} > {path} && chmod 600 {path}' && rm {STAGING}")
    size = adb(serial, "shell", f"run-as {PACKAGE} stat -c %s {path}", check=False)
    if size != str(len(data)):
        sys.exit(f"{serial}: couldn't write {path}")


def connected() -> list[str]:
    lines = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True).stdout.splitlines()[1:]
    serials = [line.split()[0] for line in lines if line.strip().endswith("\tdevice")]
    unauthorized = [line.split()[0] for line in lines if "unauthorized" in line]
    for s in unauthorized:
        print(f"{s}: skipped, allow USB debugging on the phone first")
    return serials


def seed(serial: str, grid: int, install: bool) -> None:
    sdk = int(adb(serial, "shell", "getprop", "ro.build.version.sdk"))
    model = adb(serial, "shell", "getprop", "ro.product.model")
    print(f"{serial}: {model}, Android SDK {sdk}")

    if install:
        adb(serial, "install", "-r", "-g", str(APK))
        print("  installed")

    adb(serial, "shell", "am", "force-stop", PACKAGE)

    wanted = PERMISSIONS + ([STORAGE_PERMISSION] if sdk <= 28 else [])
    for p in wanted:
        adb(serial, "shell", "pm", "grant", PACKAGE, p, check=False)
    granted = adb(serial, "shell", "dumpsys", "package", PACKAGE)
    missing = [p.rsplit(".", 1)[1] for p in wanted if f"{p}: granted=true" not in granted]
    if missing:
        # ColorOS and MIUI block adb grants unless a developer option allows it.
        print(f"  not granted: {', '.join(missing)}. Dialer will ask on the phone, or turn on")
        print("  'Disable permission monitoring' (Oppo) or 'USB debugging (Security settings)' (Xiaomi)")
    else:
        print("  permissions granted")

    if sdk >= 29:
        out = adb(serial, "shell", "cmd", "role", "add-role-holder", "android.app.role.CALL_SCREENING", PACKAGE, check=False)
        # `cmd role get-role-holders` only exists from Android 13, so read it from dumpsys.
        roles = adb(serial, "shell", "dumpsys", "role", check=False)
        held = roles.split("name=android.app.role.CALL_SCREENING", 1)[-1].split("}", 1)[0]
        print("  call screening role held" if PACKAGE in held else f"  call screening role not set: {out or held}")

    people = [(person_id(n), n, num, colour) for n, num, colour in PEOPLE[:grid]]

    adb(serial, "shell", "run-as", PACKAGE, "mkdir", "-p", "files/datastore", "files/photos")
    adb(serial, "shell", f"run-as {PACKAGE} sh -c 'rm -f files/photos/* files/people-unreadable.preferences_pb'")
    for pid, name, _, colour in people:
        write_app_file(serial, f"files/photos/{pid}.jpg", face(name[0], colour))

    write_app_file(serial, "files/datastore/people.preferences_pb", preferences({"people": encode_people(people)}))
    write_app_file(serial, "files/datastore/settings.preferences_pb", preferences({
        "people_on_screen": grid,
        "setup_done": True,
        "helper_pin_hash": hashlib.sha256(f"{PIN_SALT}{PIN}".encode()).hexdigest(),
    }))
    print(f"  {grid} people added, setup done, PIN {PIN}")

    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    print("  opened")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("-s", "--serial", action="append", help="only this phone (repeatable)")
    parser.add_argument("--grid", type=int, choices=[1, 2, 4, 6], default=4, help="people on screen, and people added")
    parser.add_argument("--no-install", action="store_true", help="skip building and installing")
    args = parser.parse_args()

    serials = args.serial or connected()
    if not serials:
        sys.exit("No phones connected.")

    if not args.no_install:
        subprocess.run([str(REPO / "gradlew"), "-q", "assembleDebug"], cwd=REPO, check=True)

    for s in serials:
        seed(s, args.grid, not args.no_install)


if __name__ == "__main__":
    main()
