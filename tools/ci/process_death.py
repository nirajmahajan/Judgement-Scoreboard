#!/usr/bin/env python3
"""Host-side lifecycle checks. The verifier survives the target process's death.

LifecycleFixtureTest persists each scenario and exits before this driver starts the UI.
The saved model and visible text must survive both a background kill and force-stop.
"""
import re
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "android.example.judgement.debug"
ACTIVITY = "android.example.judgement.ui.MainActivity"
RUNNER = f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
OUTPUT = Path("ci-artifacts/process-death")
OUTPUT.mkdir(parents=True, exist_ok=True)


def adb(*args, timeout=60):
    return subprocess.run(["adb", *args], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=timeout).stdout


def hierarchy(label):
    adb("shell", "uiautomator", "dump", "/sdcard/lifecycle.xml")
    text = adb("shell", "cat", "/sdcard/lifecycle.xml")
    (OUTPUT / f"{label}.xml").write_text(text)
    return ET.fromstring(text)


def launch_from_launcher():
    # Match Android's launcher identity on the initial launch and every resume.
    # An actionless explicit start creates a different standard-activity instance.
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER", "-f", "0x10200000",
        "-n", f"{PACKAGE}/{ACTIVITY}")


def target_activity(label):
    state = adb("shell", "dumpsys", "activity", "activities")
    (OUTPUT / f"{label}-activities.txt").write_text(state)
    records = [block for block in state.split("* Hist")
               if block.splitlines() and f"{PACKAGE}/{ACTIVITY}" in block.splitlines()[0]]
    if len(records) != 1:
        raise AssertionError(f"Expected one target activity record, found {len(records)}: {label}")
    task = re.search(r"\bt(\d+)\}", records[0].splitlines()[0])
    if not task:
        raise AssertionError(f"Target activity has no task identity: {label}")
    return task.group(1), records[0]


def background_with_saved_state(label):
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        task, record = target_activity(label)
        if (re.search(r"\bstate=STOPPED\b", record)
                and "mHaveState=true" in record and "mIcicle=Bundle[" in record):
            return task
        time.sleep(0.25)
    raise AssertionError(f"Android did not acknowledge stopped activity with saved state: {label}")


def assert_same_task(expected, label):
    actual, _ = target_activity(label)
    if actual != expected:
        raise AssertionError(f"Task-preserving death replaced task {expected} with {actual}: {label}")


def open_state(scenario, label, marker):
    launch_from_launcher()
    deadline = time.monotonic() + 20
    candidates = []
    while time.monotonic() < deadline:
        tree = hierarchy(label + "-home")
        if any(marker in value for value in texts(tree)):
            return tree
        candidates = [n for n in tree.iter("node") if n.get("package") == PACKAGE and n.get("clickable") == "true" and
                      ("resume" in n.get("text", "").lower() or
                       "continue setup" in n.get("text", "").lower() or
                       "completed results" in n.get("text", "").lower())]
        if candidates:
            break
        time.sleep(0.25)
    if not candidates:
        raise AssertionError(f"No persisted resume/setup action or restored state for {scenario}")
    bounds = list(map(int, re.findall(r"\d+", candidates[0].get("bounds", ""))))
    if len(bounds) != 4:
        raise AssertionError("Resume action has no bounds")
    adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2),
        str((bounds[1] + bounds[3]) // 2))
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        tree = hierarchy(label)
        if any(marker in value for value in texts(tree)):
            return tree
        time.sleep(0.25)
    raise AssertionError(f"{scenario} did not render persisted marker within20seconds: {marker}")


def texts(tree):
    return {n.get("text", "") for n in tree.iter("node")
            if n.get("package") == PACKAGE and n.get("text", "").strip()}


def pid():
    result = subprocess.run(["adb", "shell", "pidof", PACKAGE], text=True,
                            stdout=subprocess.PIPE, timeout=15)
    return result.stdout.strip()


# Connected Android tests remove their packages when they finish. Install the exact
# APKs built by that run before starting the independent host-side verifier.
for apk in ["app/build/outputs/apk/debug/app-debug.apk",
            "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
    if not Path(apk).is_file():
        raise AssertionError(f"Lifecycle APK is missing: {apk}")
    adb("install", "-r", "-t", apk)

for scenario in ["Setup", "Bidding", "Results", "Completed"]:
    fixture = adb("shell", "am", "instrument", "-w", "-e", "class",
                  f"android.example.judgement.LifecycleFixtureTest#seed{scenario}",
                  RUNNER, timeout=180)
    (OUTPUT / f"{scenario}-fixture.txt").write_text(fixture)
    if "FAILURES!!!" in fixture or "OK (1 test)" not in fixture:
        raise AssertionError(f"Lifecycle fixture failed: {scenario}")
    marker_match = re.search(r"INSTRUMENTATION_STATUS: lifecycleMarker=(.+)", fixture)
    if not marker_match:
        raise AssertionError(f"Fixture supplied no expected UI marker: {scenario}")
    marker = marker_match.group(1).strip()
    baseline = texts(open_state(scenario, f"{scenario}-before", marker))
    if not any(marker in text for text in baseline):
        raise AssertionError(f"{scenario} fixture marker missing before death: {marker}")
    if not baseline:
        raise AssertionError("Fixture produced an empty screen")
    for mode in ["background-kill", "force-stop"]:
        old_pid = pid()
        if not old_pid:
            raise AssertionError("Target process was not running before death test")
        saved_task = background_with_saved_state(f"{scenario}-{mode}-background")
        if mode == "background-kill":
            if not re.fullmatch(r"\d+", old_pid):
                raise AssertionError(f"Expected exactly one target PID, found {old_pid}")
            # Android protects its previous app from am kill on recent releases.
            # A same-UID SIGKILL preserves the task and does not mark the app stopped.
            adb("shell", "run-as", PACKAGE, "kill", "-9", old_pid)
        else:
            adb("shell", "am", "force-stop", PACKAGE)
        for _ in range(30):
            if not pid():
                break
            time.sleep(0.2)
        if pid():
            raise AssertionError(f"{mode} did not remove PID {old_pid}")
        if mode == "background-kill":
            package_state = adb("shell", "dumpsys", "package", PACKAGE)
            (OUTPUT / f"{scenario}-background-package-state.txt").write_text(package_state)
            if not re.search(r"User 0:.*stopped=false", package_state):
                raise AssertionError("Background SIGKILL unexpectedly marked the app stopped")
        restored = texts(open_state(scenario, f"{scenario}-{mode}", marker))
        if mode == "background-kill":
            assert_same_task(saved_task, f"{scenario}-{mode}-restored")
        new_pid = pid()
        if not new_pid or new_pid == old_pid:
            raise AssertionError(f"PID did not change: {old_pid} -> {new_pid}")
        missing = baseline - restored
        if missing:
            raise AssertionError(f"{scenario}/{mode} lost visible state: {sorted(missing)}")
        with (OUTPUT / "pid-changes.txt").open("a") as report:
            report.write(f"{scenario}/{mode}: {old_pid} -> {new_pid}\n")
print("Setup, partial bids, partial results, and completed history survived both death modes.")

# An unsaved dialog is task state, so verify it under task-preserving death only.
# A force-stop intentionally starts a fresh task and does not retain an unsaved form.
fixture = adb("shell", "am", "instrument", "-w", "-e", "class",
              "android.example.judgement.LifecycleFixtureTest#seedSetup", RUNNER, timeout=180)
(OUTPUT / "OpenNameForm-fixture.txt").write_text(fixture)
if "FAILURES!!!" in fixture or "OK (1 test)" not in fixture:
    raise AssertionError("Open-name-form setup fixture failed")
tree = open_state("Setup", "OpenNameForm-setup", "Lifecycle player A")


def tap_node(node):
    bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    if len(bounds) != 4:
        raise AssertionError("UI control has no tappable bounds")
    adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2),
        str((bounds[1] + bounds[3]) // 2))


def await_node(label, predicate):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        tree = hierarchy(label)
        match = next((node for node in tree.iter("node") if predicate(node)), None)
        if match is not None:
            return match
        time.sleep(0.25)
    raise AssertionError(f"Expected form control did not appear: {label}")


for scroll in range(8):
    add = next((node for node in tree.iter("node")
                if node.get("resource-id", "").endswith(":id/add_player")), None)
    if add is not None:
        break
    scrolling = next((node for node in tree.iter("node")
                      if node.get("resource-id", "").endswith(":id/screen_list")), None)
    if scrolling is None:
        raise AssertionError("Setup has no scrollable player list")
    bounds = list(map(int, re.findall(r"\d+", scrolling.get("bounds", ""))))
    adb("shell", "input", "swipe", str((bounds[0] + bounds[2]) // 2), str(bounds[3] - 40),
        str((bounds[0] + bounds[2]) // 2), str(bounds[1] + 40), "300")
    tree = hierarchy(f"OpenNameForm-scroll-{scroll}")
else:
    raise AssertionError("Add player never became reachable")
tap_node(add)
field = await_node("OpenNameForm-empty", lambda node: node.get("resource-id", "").endswith(":id/edit_value"))
tap_node(field)
adb("shell", "input", "text", "Unsaved%sstaged%sname")
typed = await_node("OpenNameForm-typed-name", lambda node: node.get("resource-id", "").endswith(":id/edit_value")
                   and node.get("text", "").lower() == "unsaved staged name")
expected_form_text = typed.get("text")
(OUTPUT / "OpenNameForm-state.json").write_text(json.dumps({
    "unsavedName": expected_form_text, "saveForFuture": True,
    "attribution": "created by chatgpt-codex",
}, indent=2))
check = await_node("OpenNameForm-typed", lambda node: node.get("checkable") == "true"
                   and node.get("text") == "Save for future games")
if check.get("checked") != "false":
    raise AssertionError("Save-for-future checkbox must start unchecked")
tap_node(check)
await_node("OpenNameForm-before", lambda node: node.get("checkable") == "true"
           and node.get("checked") == "true")
old_pid = pid()
if not re.fullmatch(r"\d+", old_pid):
    raise AssertionError(f"Expected one process before form death, found {old_pid}")
saved_form_task = background_with_saved_state("OpenNameForm-background")
adb("shell", "run-as", PACKAGE, "kill", "-9", old_pid)
for _ in range(30):
    if not pid():
        break
    time.sleep(0.2)
if pid():
    raise AssertionError("Open-form background SIGKILL did not remove the old PID")
package_state = adb("shell", "dumpsys", "package", PACKAGE)
(OUTPUT / "OpenNameForm-background-package-state.txt").write_text(package_state)
if not re.search(r"User 0:.*stopped=false", package_state):
    raise AssertionError("Open-form background death unexpectedly force-stopped the app")
launch_from_launcher()
await_node("OpenNameForm-restored", lambda node: node.get("resource-id", "").endswith(":id/edit_value")
           and node.get("text") == expected_form_text)
await_node("OpenNameForm-restored", lambda node: node.get("checkable") == "true"
           and node.get("text") == "Save for future games" and node.get("checked") == "true")
assert_same_task(saved_form_task, "OpenNameForm-restored")
new_pid = pid()
if not new_pid or new_pid == old_pid:
    raise AssertionError(f"Open form did not restore under a different PID: {old_pid} -> {new_pid}")
with (OUTPUT / "pid-changes.txt").open("a") as report:
    report.write(f"OpenNameForm/background-kill: {old_pid} -> {new_pid}\n")
print("Unsaved name and Save-for-future checkbox survived genuine background process death.")
