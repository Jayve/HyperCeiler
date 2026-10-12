from pathlib import Path
import os, subprocess, sys, tempfile

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
tests = Path(__file__).resolve().parent
libhook = root / 'library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook'
rule = libhook / 'rules/systemui/statusbar/HideStatusBarBeforeScreenshot.java'
failed = 0


def javac_bin():
    candidates = [Path(r'D:/build-tools/jdk-25.0.4.1+1/bin')]
    if os.environ.get('JAVA_HOME'):
        candidates.append(Path(os.environ['JAVA_HOME']) / 'bin')
    for candidate in candidates:
        if (candidate / 'javac.exe').exists():
            return candidate
    return None


jdk = javac_bin()
if jdk is None:
    print('FAIL no javac found (expected D:/build-tools/jdk-25.0.4.1+1 or JAVA_HOME)')
    sys.exit(1)

with tempfile.TemporaryDirectory(prefix='hc-hide-statusbar-') as out:
    sources = list((tests / 'stubs').rglob('*.java'))
    sources += [rule, tests / 'HideStatusBarScreenshotRuleTest.java']
    z = subprocess.run([str(jdk / 'javac.exe'), '-J-Duser.language=en', '-encoding', 'UTF-8', '-d', out]
                       + [str(x) for x in sources], capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    if z.returncode:
        print(z.stdout, end='')
        print(z.stderr, end='')
        sys.exit(z.returncode)
    z = subprocess.run([str(jdk / 'java.exe'), '-ea', '-cp', out,
                        'com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar'
                        '.HideStatusBarScreenshotRuleTest'],
                       capture_output=True, text=True, encoding='utf-8', errors='replace')
    print(z.stdout, end='')
    print(z.stderr, end='')
    failed += int(z.returncode != 0)

system_ui = (libhook / 'app/SystemUI/SystemUIV.java').read_text(encoding='utf8')
system_ui_b = (libhook / 'app/SystemUI/SystemUIB.java').read_text(encoding='utf8')
base_load = (libhook / 'base/BaseLoad.java').read_text(encoding='utf8')
rule_text = rule.read_text(encoding='utf8')
checks = {
    'SystemUIV registers hide-status-bar rule once':
        system_ui.count('initHook(new HideStatusBarBeforeScreenshot()') == 1,
    'SystemUIB registers hide-status-bar rule once':
        system_ui_b.count('initHook(new HideStatusBarBeforeScreenshot()') == 1,
    'BaseLoad rejects duplicated hook registration':
        'sInitializedHookClasses.add(hookClass)' in base_load
        and 'sInitializedHookClasses.clear()' in base_load,
    'rule keeps process-wide state instead of instance fields':
        'private static final Map<View, HiddenStatusBar>' in rule_text
        and 'mVisibilityBeforeScreenshot' not in rule_text,
    'rule restores every hidden status bar on finish':
        'restoreHiddenStatusBars();' in rule_text,
}
for name, ok in checks.items():
    print(('PASS ' if ok else 'FAIL ') + name)
    failed += int(not ok)

print('Hide status bar screenshot suite: ' + ('PASS' if failed == 0 else 'FAIL'))
sys.exit(int(failed != 0))
