import argparse
import hashlib
import json
import os
import struct
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def notice(title, message):
    if os.environ.get("GITHUB_ACTIONS"):
        escaped = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::notice title={title}::{escaped}")
    else:
        print(f"{title}: {message}")


def verify(smoke):
    reports = list(Path("build/test-results/test").glob("TEST-*.xml"))
    assert reports, "No JUnit XML reports found"
    tests = failures = errors = skipped = 0
    for report in reports:
        suite = ET.parse(report).getroot()
        tests += int(suite.attrib["tests"])
        failures += int(suite.attrib["failures"])
        errors += int(suite.attrib["errors"])
        skipped += int(suite.attrib.get("skipped", 0))
    assert tests >= 80, f"Expected the full test suite, found {tests} tests"
    assert failures == 0 and errors == 0, f"JUnit failed: {failures} failures, {errors} errors"
    notice("Unit test evidence", f"{tests} tests; {failures} failures; {errors} errors; {skipped} skipped")

    if smoke:
        run = Path("build/smoke-run")
        result = (run / "smoke-result.txt").read_text(encoding="utf-8")
        lines = result.splitlines()
        assert lines[0] == "PASSED", result
        assert len(lines) >= 25, f"Not enough smoke checks: {result}"
        log = (run / "logs/latest.log").read_text(encoding="utf-8", errors="replace")
        assert "FANZIK_SMOKE_PASSED" in log, "Client did not confirm smoke test completion"
        assert "Fanzik event handler failed" not in log, "An event handler failed in the real client"
        assert "Fanzik operation failed" not in log, "A command/config operation failed in the real client"
        screenshots = set()
        for name in ["01-movement.png", "02-combat.png", "03-configs.png", "04-scaled.png"]:
            image = run / "screenshots" / name
            data = image.read_bytes()
            assert len(data) > 1024 and data.startswith(b"\x89PNG\r\n\x1a\n"), f"Invalid screenshot: {name}"
            screenshots.add(hashlib.sha256(data).hexdigest())
            width, height = struct.unpack(">II", data[16:24])
            assert width >= 640 and height >= 360, f"Unexpected screenshot size: {width}x{height}"
        assert len(screenshots) == 4, "The four UI states did not produce distinct screenshots"
        notice("Real client evidence", result.strip())

    jars = [file for file in Path("build/libs").glob("*.jar") if not file.name.endswith("-sources.jar")]
    assert len(jars) == 1, f"Expected one installable mod, found {jars}"
    jar = jars[0]
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        required = {
            "fabric.mod.json",
            "fanzik.mixins.json",
            "dev/fanzik/client/FanzikClient.class",
            "dev/fanzik/client/module/impl/AutoSprint.class",
            "dev/fanzik/client/render/Render2D.class",
            "dev/fanzik/client/render/Render3D.class",
            "dev/fanzik/client/config/ConfigService.class",
            "dev/fanzik/client/ui/ClickGuiScreen.class",
            "assets/fanzik/font/roboto.ttf",
            "assets/fanzik/lang/ru_ru.json",
            "assets/fanzik/icon.png",
        }
        assert required <= names, f"Missing release files: {required - names}"
        assert not any("/smoke/" in name or name.endswith("Test.class") for name in names), "Test code leaked into the release"
        metadata = json.loads(archive.read("fabric.mod.json"))
        assert metadata["id"] == "fanzik" and metadata["environment"] == "client"
        assert metadata["depends"]["minecraft"] == "1.21.1"
        assert metadata["version"] == "1.0.0"
        mixins = json.loads(archive.read("fanzik.mixins.json"))
        assert set(mixins["client"]) == {"KeyboardMixin", "ChatScreenMixin"}
        refmap = mixins.get("refmap")
        if refmap:
            mappings = json.loads(archive.read(refmap))
            mapping_text = json.dumps(mappings)
            assert "method_1466" in mapping_text and "method_25404" in mapping_text, "Production mixin targets were not remapped"
            mixin_status = f"refmap: {refmap}"
        else:
            keyboard = archive.read("dev/fanzik/client/mixin/KeyboardMixin.class")
            chat = archive.read("dev/fanzik/client/mixin/ChatScreenMixin.class")
            assert b"method_1466" in keyboard and b"method_25404" in chat, "Missing production mixin remapping"
            mixin_status = "inline intermediary remapping"
    checksum = hashlib.sha256(jar.read_bytes()).hexdigest()
    notice("Release evidence", f"{jar.name}; {jar.stat().st_size} bytes; SHA-256 {checksum}; {mixin_status}; no test code")




if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--smoke", action="store_true")
    try:
        verify(parser.parse_args().smoke)
    except Exception as failure:
        message = str(failure).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        if os.environ.get("GITHUB_ACTIONS"):
            print(f"::error title=Verification failed::{message}")
        raise
