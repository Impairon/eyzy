# swizy

An Android privacy utility with Lowery avatar masking, manual per-app layouts,
Sibha counting, and offline-capable prayer times.

Lowery also includes **Hide an area manually**. Summon the app-icon ball from
the Lowery tab, open its menu, choose **Add**, and drag across any screen
region. Multiple rectangles remain black independently of avatar detection.
The movable ball fades while idle and provides Add, remove-last, show/hide-all,
move-boxes, and Close controls. Settings also has **Do nothing (manual only)**,
which disables dynamic avatar detection while leaving manual boxes available.

Release settings include app/orientation profiles, undo/redo, JSON layout
backup, optional privacy indicator, battery saver scanning, mask color/opacity,
and a local crash log.

See [`PROJECT_PATHS.md`](PROJECT_PATHS.md) for the module layout and build/install
commands. The supplied patch references and combined review bundle are kept under
[`patches/`](patches/).
