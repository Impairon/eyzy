# swizy fixes: what to do

Five small patches for `Impairon/eyzy`, branch `pushy`, on top of commit `c32b972`
("Use Android settings for TTS voice selection"). Each one is a separate commit, so any of
them can be left out or reverted on its own.

## 1. Apply them

Put the five `.patch` files in a folder, for example `~/swizy-fixes/`, then:

```bash
cd ~/path/to/eyzy
git checkout pushy
git pull                                   # make sure you are on c32b972 or newer
git am ~/swizy-fixes/000*.patch
```

If `git am` stops because you have newer commits that touch the same lines:

```bash
git am --abort
git am --3way ~/swizy-fixes/000*.patch     # tries a three-way merge instead
```

Both ways keep the original commit messages. Then `git push`.

## 2. One-time cleanup (run once, after the patches)

These commands are not in a patch because they delete 219 binary build files, which would
make the patch several megabytes. Run them yourself:

```bash
git rm -r -q --cached app/build .gradle local.properties
git rm -q window.xml output.txt output2.txt 0002-Clean-up-repo-and-fix-small-issues.patch \
          eyzy-changes.bundle PROJECT_PATHS.md
git rm -r -q patches
git commit -m "Stop tracking build output and debugging leftovers"
git push
```

`git rm --cached` keeps the files on your disk. `local.properties` stays where it is, so your
own builds still find the Android SDK.

**About `window.xml`:** it is a UI dump of a Telegram screen and contains on-screen text. The
repo is public, so it is still readable in the old commits after you delete it. If that
matters, rewrite the history with `git filter-repo --path window.xml --invert-paths`, then
force-push. That is optional, and it changes every commit hash after the file was added.

## 3. Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Patch 1 also adds a GitHub Actions job. After you push, open the **Actions** tab: each push
should build the app and attach the APK. That job has not been run yet, so if its first run
fails, paste the log and I will fix the workflow.

After installing, turn swizy off and on in Settings > Accessibility.

## 4. What each patch does, and how to test it

| # | Patch | What it fixes |
|---|---|---|
| 1 | Add .gitignore, README, CI build | Keeps junk out of the repo and catches a broken build on every push. |
| 2 | Fix manual boxes, profiles and undo bugs | Boxes shared between apps, a switch that changed by itself, a stuck edit mode. |
| 3 | Fix control circle | The jump when the menu opens, a landscape crash, taps that did not open the menu. |
| 4 | Fix the line | The line now hides the whole avatar and stays out of Telegram private chats. |
| 5 | Make scanning lighter | Less work per scan, fewer redraws. |

### Patch 2: boxes, profiles, undo
- **Boxes per app.** Draw a box in Chrome, then open WhatsApp: the box must not be there.
- **"Hide all black boxes"** stays as you set it when you switch apps or rotate.
- **Notification shade:** pull it down over WhatsApp and close it. Your boxes must not blink off.
- **Edit profile:** choose Add profile in an app that already has a profile, draw a box, then Close.
  The old profile and its boxes must still be there.
- **Move or resize** a box: no second copy stays behind. A tap that moves nothing is not an undo step.
- **Back gesture** during Add, Move or Resize: the screen returns to normal instead of staying locked.

### Patch 3: control circle
- Drag it **slowly**: it follows the finger and the menu does not open when you let go.
- Tap it with the circle on the **left** side, near the top and near the bottom: the menu opens
  without the circle jumping.
- Open the menu in **landscape**: it must not crash. On short screens the rows shrink.
- Rotate or restart: the circle comes back where you left it.

### Patch 4: the line
- **WhatsApp chat list and group chat:** one line, as wide as the avatars, not covering the top bar.
- **Telegram group:** the line shows. **Telegram private chat and channel:** no line over the bubbles.
- If the line is slightly too thin or thick, the thickness slider in Settings is now a *minimum*.

### Patch 5: scanning
No visible change, scrolling should feel lighter. Nothing to test beyond normal use.

## 5. Behavior changes you should know about

- **Old manual boxes disappear.** They used to be global (stored as `manual`). They are now stored
  per app (`manual-<package>`). Your old boxes are still in the preferences but are no longer shown.
  Draw them again in each app, or use Add profile for apps you use often.
- **"Hide all black boxes" is now your own switch.** Before, entering an app with a profile turned
  it back on. If boxes seem missing after the update, open the circle menu and choose
  **Show all black boxes**.
- **The line width** is the avatar width plus 4dp, or your minimum thickness if that is larger.

## 6. What I checked and what I did not

Checked:
- Every patch applies, in order, to a fresh clone of `origin/pushy` at `c32b972`.
- The result compiles (Kotlin 1.9.24 against the Android 34 stubs).
- The circle menu placement was checked for every circle position on a 1080x2400 screen in portrait
  and landscape: the circle never moves and the menu never leaves the screen.
- The Telegram group pattern was checked on typical subtitles ("168 members, 2 online" is a group;
  "5,000 subscribers" and "last seen recently" are not).

Not checked (needs your phone):
- Any of the behavior above on a real device or with real apps.
- The GitHub Actions workflow.
- Telegram in languages other than English and Arabic. The header test uses "members" words and a
  "number text, number" pattern, with the position of the message bubbles as a fallback.

## 7. Still open

- The Dynamic circle mode in a Telegram chat still draws a circle on every message row of a group.
- The scan runs on the main thread. It is lighter now, but a very busy app can still make the
  circles trail slightly behind fast scrolling.
- There are no tests. A few for the rectangle math would be the next useful addition.

## 8. If something goes wrong

Revert one patch without touching the others:

```bash
git log --oneline -6        # find the commit
git revert <hash>
```

Or go back to before all of them: `git reset --hard c32b972` (only if you have not pushed).
