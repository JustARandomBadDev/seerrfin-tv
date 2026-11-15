# Physical Android TV testing

Use this procedure only when the user has explicitly authorized the requested
operations on the physical TV. The validated device is a wireless-ADB
`Smart_TV_Pro` running Android 14. Do not assume that a previous transport is
still available.

## Select one ADB transport

List the current transports before every test session:

```bash
adb devices -l
```

The same TV can appear more than once, for example through an IP transport and
an mDNS TLS transport. Choose one entry whose state is `device` and whose model
is `Smart_TV_Pro`, then use it explicitly for every subsequent command:

```bash
TV_SERIAL='transport-reported-by-adb'
adb -s "$TV_SERIAL" shell getprop ro.product.model
adb -s "$TV_SERIAL" shell getprop ro.build.version.release
```

Never use an unqualified `adb` device command when multiple transports exist.
Wireless ADB ports can change, and a transport can disappear during a test. If
a command reports a missing or offline device, run `adb devices -l` again,
select an authorized transport that still identifies the same TV, and update
`TV_SERIAL`. Do not assume that a replacement serial represents the TV without
checking its model.

## Install, launch, and identify the process

Install only the Seerrfin TV debug APK. `-r` replaces the existing application
without deliberately clearing its data:

```bash
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_SERIAL" shell am start -n \
  io.github.justarandombaddev.seerrfintv/.MainActivity
SEERRFIN_PID="$(adb -s "$TV_SERIAL" shell pidof io.github.justarandombaddev.seerrfintv)"
test -n "$SEERRFIN_PID"
```

Use `dumpsys activity activities` when the foreground activity matters. A Back
navigation, normal activity transition, or process moving to the background is
not by itself a crash. Confirm crashes from the application process logs:

```bash
adb -s "$TV_SERIAL" shell dumpsys activity activities | \
  rg -m 3 'topResumedActivity|io\.github\.justarandombaddev\.seerrfintv'
adb -s "$TV_SERIAL" logcat --pid="$SEERRFIN_PID" -d -t 300 | \
  rg -i 'fatal exception|androidruntime|noclassdeffounderror' || true
```

## Exercise the remote-control path

Drive the actual UI instead of treating successful command injection as proof
of correct behavior:

```bash
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_DPAD_UP
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_DPAD_DOWN
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_DPAD_LEFT
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_DPAD_RIGHT
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_DPAD_CENTER
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_ENTER
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_BACK
```

Visually or structurally verify the initial focus, every focus transition,
activation, loading and error states, and Back behavior. Retain focused unit and
Compose tests, but do not replace the physical-TV traversal entirely with them.

## Enter text on the AZERTY TV keyboard

Do not use `adb shell input text` for URLs on this TV. That command translates
characters through a key map that does not match the TV's AZERTY input method;
punctuation and some letters can therefore become different visible
characters. P01.1 validated semantic `KEYCODE_*` events instead.

First focus the text field and open the keyboard with D-pad Center. To replace
an existing value, move to the end and send enough Delete events to empty the
known field:

```bash
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_MOVE_END
for unused_index in {1..100}; do
  adb -s "$TV_SERIAL" shell input keyevent KEYCODE_DEL
done
```

Enter letters with their semantic key codes. The following fictitious URL
fragment demonstrates the special characters that were validated during
P01.1:

```bash
# "https://media.example.test/"
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_H
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_T
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_T
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_P
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_S
adb -s "$TV_SERIAL" shell input keycombination \
  KEYCODE_SHIFT_LEFT KEYCODE_SEMICOLON  # colon
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_SLASH
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_SLASH

# Continue with KEYCODE_M, KEYCODE_E, etc. Useful URL punctuation:
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_PERIOD
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_MINUS
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_SLASH
```

`KEYCODE_M` is important: it produces `m` semantically without relying on the
physical AZERTY/QWERTY key position. Finish editing with `KEYCODE_ENTER`; on the
validated TV this accepts the IME entry and closes the keyboard. Then use
`KEYCODE_DPAD_DOWN` and `KEYCODE_DPAD_CENTER` to focus and activate the connect
action.

Before submitting a sensitive value, verify the exact field contents with a UI
dump. Use only fictitious data in reusable commands and do not print the dump:

```bash
TV_DUMP=/tmp/seerrfin-tv-ui.xml
adb -s "$TV_SERIAL" exec-out uiautomator dump /dev/tty > "$TV_DUMP"
rg -Fq 'https://media.example.test/' "$TV_DUMP"
```

A zero exit status proves an exact literal match. It does not prove that focus,
loading, or success rendering is correct; inspect those separately.

## Visual, state, persistence, and crash checks

Take a temporary screenshot only when visual focus or rendering evidence is
needed:

```bash
TV_SCREENSHOT=/tmp/seerrfin-tv-screen.png
adb -s "$TV_SERIAL" exec-out screencap -p > "$TV_SCREENSHOT"
```

Inspect the image locally, then remove it if it can contain user data. For
machine-verifiable messages or values, create a fresh `uiautomator dump` and
check the expected literal with `rg -Fq` without printing the full XML.

To verify persistence, complete the successful action, send `KEYCODE_BACK`,
confirm that the TV launcher is resumed, launch `MainActivity` again, and check
the restored value with a new UI dump. Finally, verify that `pidof` still
returns the application process and filter its recent logcat for fatal errors.

## Confidentiality and cleanup

- Never put real server URLs, local IPs, usernames, passwords, API keys, or
  tokens in documentation, source code, tests, screenshots, or logs.
- Avoid printing UI dumps because they can contain field values.
- Use fictitious hosts such as `media.example.test` in reusable examples.
- Delete temporary screenshots and dumps immediately after inspection:

```bash
rm -f "$TV_SCREENSHOT" "$TV_DUMP"
```

- Do not retain or publish a capture that exposes personal content. Do not use
  broad logcat output when a process-scoped and filtered query is sufficient.
