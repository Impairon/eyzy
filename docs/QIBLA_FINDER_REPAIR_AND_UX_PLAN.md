# Swizy Qibla Finder — Correctness Repair, Sensor Engineering, UX, and Test Plan

**Repository:** \`Impairon/eyzy\`  
**Target branch:** \`pushy\`  
**Reviewed implementation:** \`app/src/main/java/com/abo/avatarblackout/MainActivity.kt\`  
**Document purpose:** Engineering plan for repairing and improving the Qibla finder. This document does not itself change application code.

---

## 1. Executive summary

The Qibla finder has two separate jobs:

1. Calculate the direction from the user's coordinates to the Kaaba.
2. Determine which direction the top edge of the phone is facing, then show the difference between that heading and the Qibla bearing.

The first job is a spherical-bearing calculation. The current implementation uses the standard initial-bearing formula and is structurally correct. The second job is a device-orientation problem. It depends on Android sensor axes, display rotation, magnetic-versus-true north, sensor quality, and the way the compass view renders its needle.

The reported defect is that the displayed direction appears to be approximately 180 degrees wrong: the user has to turn their back toward the indicated direction instead of facing it. **Do not fix this by blindly adding or subtracting 180 degrees.** That could make one phone appear correct while breaking another orientation, portrait/landscape mode, or the underlying compass semantics.

The repair should establish one unambiguous convention:

> \`deviceHeadingTrue\` is the clockwise bearing, measured from true north, of the phone's top edge (the screen's local +Y direction in the natural portrait orientation, adjusted for current display rotation).

Then:

> \`relativeQiblaAngle = normalizeSigned(qiblaBearingTrue - deviceHeadingTrue)\`

A relative angle of zero means the phone's top edge points toward the Qibla. A positive angle means the Qibla is clockwise/right of the phone's top edge; a negative angle means it is counter-clockwise/left.

The work should be delivered in small stages:

1. Add deterministic bearing and angle tests.
2. Make sensor heading and display-axis conventions explicit.
3. Convert magnetic heading to true heading using local magnetic declination.
4. Verify the drawn compass against a physical cardinal-direction test.
5. Improve sensor-quality, calibration, location, and permission states.
6. Simplify the screen so users can tell which way to turn and when alignment is trustworthy.
7. Run a real-device acceptance matrix before declaring the reversal fixed.

---

## 2. What the current repository does

The Qibla implementation is currently inside the large programmatic Android activity:

\`app/src/main/java/com/abo/avatarblackout/MainActivity.kt\`

The relevant responsibilities are all in the same file:

- \`registerSensors()\` selects a rotation-vector sensor or an accelerometer/magnetometer fallback.
- \`updateHeadingFromMatrix()\` remaps the sensor matrix for display rotation, calls \`SensorManager.getOrientation()\`, smooths azimuth and tilt, and updates the compass view.
- \`sensorListener\` handles rotation-vector events.
- \`accelMagListener\` combines accelerometer and magnetic-field readings when no rotation-vector sensor is available.
- \`showQibla()\` reads the saved latitude and longitude, calculates the Qibla bearing, creates the compass view, and shows explanatory text.
- \`qiblaBearing()\` calculates the initial great-circle bearing to the Kaaba.
- \`CompassView\` draws the compass ring, cardinal labels, Qibla arrow, accuracy dot, tilt text, and alignment message.

This is a useful compact first implementation, but it currently mixes four concepts that should be treated separately:

1. Geographic bearing to the Kaaba.
2. Magnetic heading reported by the device.
3. True-north heading used by the geographic bearing.
4. Screen-space drawing coordinates.

Keeping those concepts separate is the most important correctness improvement.

### 2.1 Existing Qibla-bearing calculation

The current method uses the Kaaba coordinates:

- Latitude: \`21.4225°\`
- Longitude: \`39.8262°\`

It converts the user's coordinates and the Kaaba coordinates to radians, computes the initial great-circle bearing with \`atan2(y, x)\`, converts to degrees, and normalizes to \`[0, 360)\`.

That is the correct general approach. Do not replace it with a flat map approximation, a fixed bearing for Egypt, or a hard-coded regional direction.

### 2.2 Existing sensor selection

The app first tries:

- \`Sensor.TYPE_ROTATION_VECTOR\`
- then \`Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR\`

If neither exists, it falls back to:

- \`Sensor.TYPE_ACCELEROMETER\`
- \`Sensor.TYPE_MAGNETIC_FIELD\`

The app registers these sensors in \`onResume()\` through \`registerSensors()\` and unregisters them in \`onPause()\`. This lifecycle is a good foundation: compass sensors should not continue consuming power while the app is paused.

### 2.3 Existing display-axis remapping

The current axis mapping is:

| Display rotation | X axis | Y axis |
|---|---|---|
| \`ROTATION_0\` | \`AXIS_X\` | \`AXIS_Y\` |
| \`ROTATION_90\` | \`AXIS_Y\` | \`AXIS_MINUS_X\` |
| \`ROTATION_180\` | \`AXIS_MINUS_X\` | \`AXIS_MINUS_Y\` |
| \`ROTATION_270\` | \`AXIS_MINUS_Y\` | \`AXIS_X\` |

This is a conventional Android remapping pattern for adapting the sensor coordinate system to display rotation. It is not, by itself, proof that the complete heading is correct on the target device. The mapping, the meaning of the phone edge, the returned azimuth, and the drawing convention must be verified together.

### 2.4 Existing compass drawing

The compass ring is rotated by \`-azimuth\`. The Qibla arrow is drawn at:

\`qiblaBearing - azimuth\`

That is a coherent drawing convention if, and only if, \`azimuth\` is the clockwise heading of the phone's top edge relative to the same north reference used by \`qiblaBearing\`.

This is an important diagnostic clue: the drawing formula is not obviously reversed on its own. A wrong sensor heading, an inconsistent north reference, an incorrect physical interpretation of the arrow, or a screen-axis mismatch can make the overall experience wrong even when the drawing math looks reasonable.

### 2.5 Existing weaknesses to address

The current implementation has several gaps:

- The geographic bearing is referenced to true north, while Android compass heading is normally referenced to magnetic north. The code does not currently apply magnetic declination.
- The app does not expose a clear, testable distinction between magnetic heading and true heading.
- Sensor accuracy is represented by one status value, but the user is not given a complete explanation of what is and is not trustworthy.
- The accelerometer/magnetometer fallback has no explicit freshness tracking for either sensor input.
- The fallback arrays begin as zeros; the app should not treat an uninitialized or stale pair as a valid orientation.
- The compass screen can display a bearing even when the live heading is unavailable. That is useful, but the distinction should be explicit.
- The current alignment threshold is approximately three degrees. That is too strict for a noisy handheld compass and can produce flicker.
- The UI tells the user to point the top edge at the purple arrow, but it does not give a simple left/right turn instruction.
- The current tilt display is derived from the rotation matrix's \`R[8]\` value. It should be checked before being described as a general phone tilt measurement; it is not a complete two-axis level indicator.
- A single \`compassAzimuth == 0f\` check is used as a first-reading sentinel. Zero is a legitimate compass heading, so initialization should use an explicit boolean or nullable state instead.
- The code should refresh its display-rotation assumption if the display configuration changes while the activity remains alive.
- The screen should distinguish a missing sensor, an unreliable sensor, a location problem, and a magnetic-interference problem instead of treating them as variations of one generic state.

---

## 3. Define the coordinate and angle contract first

Do this before changing the sensor implementation. Most compass bugs come from mixing conventions.

### 3.1 Geographic bearing convention

Use a clockwise bearing from north:

- North = \`0°\`
- East = \`90°\`
- South = \`180°\`
- West = \`270°\`

All geographic Qibla bearings should be normalized into \`[0°, 360°)\`.

### 3.2 Phone-heading convention

Use the direction in which the **top edge of the displayed phone** points.

- Phone top edge points north: heading = \`0°\`
- Phone top edge points east: heading = \`90°\`
- Phone top edge points south: heading = \`180°\`
- Phone top edge points west: heading = \`270°\`

This must remain true in portrait and landscape. If the phone is rotated into landscape, the app must not accidentally start measuring the device's old physical edge while the UI tells the user to aim the new screen top edge.

The implementation should name the value \`deviceHeadingTrueDegrees\` or an equally explicit name. Avoid generic names such as \`direction\`, \`angle\`, or \`azimuth\` once values cross module boundaries.

### 3.3 Relative Qibla angle convention

Define:

\`relativeQiblaDegrees = normalizeSigned(qiblaBearingTrueDegrees - deviceHeadingTrueDegrees)\`

Normalize the result to the interval \`(-180°, 180°]\` (or a documented equivalent).

Interpretation:

| Relative angle | Meaning |
|---:|---|
| \`0°\` | Phone top edge points directly toward Qibla. |
| \`+30°\` | Qibla is 30° clockwise/right of the phone top edge. |
| \`-30°\` | Qibla is 30° counter-clockwise/left of the phone top edge. |
| \`+90°\` | Qibla is to the right. |
| \`-90°\` | Qibla is to the left. |
| \`180°\` or \`-180°\` | Qibla is directly behind the phone. |

Do not reverse this definition in the view layer. The view should consume a relative angle that already has a stable meaning.

### 3.4 Magnetic north versus true north

The Qibla bearing calculated from latitude/longitude is a true-north bearing. A magnetic compass reports a magnetic-north heading.

The difference is local magnetic declination. Use Android's \`android.hardware.GeomagneticField\` with the user's latitude, longitude, altitude (if known; otherwise a documented default), and current time.

Android's declination is expressed in degrees, with east declination positive. The intended conversion is:

\`trueHeading = normalize360(magneticHeading + declination)\`

Do not add declination to the Qibla bearing. Do not apply declination twice. Keep the magnetic value and true value separately named so tests can prove that the conversion happens once.

The exact sensor reference should be confirmed against Android's documentation and the target device during implementation. The rotation-vector family is tied to the geomagnetic reference frame; it should not be treated as a GPS/true-north heading without conversion.

### 3.5 Normalization helpers

Create small pure helpers and test them independently:

\`\`\`kotlin
private fun normalize360(degrees: Float): Float {
    return ((degrees % 360f) + 360f) % 360f
}

private fun normalizeSigned(degrees: Float): Float {
    val value = normalize360(degrees)
    return if (value > 180f) value - 360f else value
}
\`\`\`

Use the same helpers in the sensor, alignment, and UI layers. Avoid slightly different modulo expressions in different methods.

---

## 4. Repair the sensor-heading pipeline

### 4.1 Recommended data flow

The target flow should be:

\`\`\`text
Android sensor event
        |
        v
Rotation matrix in device coordinates
        |
        v
Remap axes for current display rotation
        |
        v
Extract magnetic azimuth
        |
        v
Apply local magnetic declination
        |
        v
Normalize + smooth true heading
        |
        v
Compare with true Qibla bearing
        |
        v
Compass UI + left/right guidance
\`\`\`

Keep the geographic calculation independent of the sensor pipeline. If the sensor is missing, the app should still be able to show the calculated bearing from north.

### 4.2 Sensor selection policy

Recommended priority:

1. \`TYPE_ROTATION_VECTOR\` — preferred general-purpose fused orientation sensor.
2. \`TYPE_GEOMAGNETIC_ROTATION_VECTOR\` — useful lower-power alternative, but validate its accuracy and behavior on target devices.
3. Accelerometer + magnetometer — compatibility fallback.

Do not use \`TYPE_GAME_ROTATION_VECTOR\` for a compass that must point to geographic north. A game rotation vector does not use the magnetic field as a north reference and can drift in yaw.

At sensor registration time, record which sensor was selected. Show a useful internal diagnostic label (for example, in a developer/debug panel), but do not burden the main screen with sensor implementation details.

### 4.3 Handle a missing sensor honestly

If no usable orientation sensor is available:

- Keep the calculated Qibla bearing visible.
- Show a clear message such as “Compass sensor unavailable on this device.”
- Do not animate the arrow as if it were responding to the phone.
- Do not show “Aligned” based on a default heading of zero.
- Offer a simple explanation that the displayed bearing is relative to north and that a live compass is unavailable.

The current screen's fallback message is directionally correct, but the view should also have an explicit state indicating that no live heading is available.

### 4.4 Rotation-vector processing

For rotation-vector events:

1. Validate that the event contains enough values.
2. Convert the vector to a 3×3 rotation matrix using \`SensorManager.getRotationMatrixFromVector\`.
3. Remap the matrix for the current display rotation.
4. Extract orientation with \`SensorManager.getOrientation\`.
5. Convert azimuth radians to degrees.
6. Normalize the magnetic heading.
7. Convert magnetic heading to true heading with declination.
8. Smooth the heading using circular-angle smoothing.
9. Publish a timestamped heading sample and quality state.

Do not apply an arbitrary 180-degree correction at any of these steps.

### 4.5 Display rotation and physical edge

The current remapping table is a reasonable starting point, but it must be verified against a physical test.

Document exactly which edge is being measured for each display rotation. Add a small debug readout during development:

- Current display rotation.
- Raw azimuth before remapping (if useful).
- Remapped magnetic azimuth.
- Declination.
- Final true heading.

For each display rotation, place the phone with the screen's top edge pointing north, east, south, and west. The reported true heading should be approximately 0°, 90°, 180°, and 270° respectively.

If the app only intends to support portrait mode, make that an explicit product decision and lock or guide the orientation. Do not accidentally support landscape with incorrect instructions.

### 4.6 Accelerometer/magnetometer fallback

The fallback currently stores filtered gravity and magnetic vectors and calls \`SensorManager.getRotationMatrix\`. Improve it as follows:

- Track whether at least one valid accelerometer sample and one valid magnetometer sample have arrived.
- Track each sample's timestamp.
- Do not calculate orientation until both inputs are initialized and recent.
- Reject zero-length or clearly invalid vectors.
- Keep the existing low-pass gravity filter only if it behaves well on physical devices.
- Consider a similar modest filter for magnetic readings, but avoid smoothing so heavily that the compass lags behind the phone.
- If either sensor stops producing updates or becomes stale, mark the heading unavailable instead of freezing the last value while still claiming it is live.
- Call \`getRotationMatrix\` only after valid input data is present.
- Use the same display remapping, declination conversion, normalization, and smoothing path as the rotation-vector sensor.

Do not let the fallback produce a different sign convention from the preferred sensor.

### 4.7 Sensor accuracy and calibration

Android reports sensor accuracy statuses such as unreliable, low, medium, and high. Treat these as quality indicators, not as a precise angular error bound.

Recommended user-facing states:

- **Good:** heading is available and sensor accuracy is medium/high.
- **Weak:** heading is available but accuracy is low.
- **Unreliable:** Android reports unreliable accuracy or sensor input is stale.
- **Unavailable:** no supported sensor pipeline is available.

Calibration guidance should be conditional and calm:

- If accuracy is low/unreliable, ask the user to move away from magnetic interference and slowly move the phone in a figure-eight motion.
- Do not demand calibration on every launch.
- Do not claim that a figure-eight guarantees accuracy.
- If accuracy is already good, do not show a red warning that encourages unnecessary movement.
- Avoid showing “high accuracy” as a promise that the phone's compass is survey-grade.

Magnetic interference can come from magnetic phone cases, magnetic mounts, speakers, laptops, vehicles, power equipment, and reinforced structures. Explain this briefly in a help panel rather than permanently filling the main screen with warnings.

### 4.8 Circular smoothing

A normal arithmetic average fails around the 0°/360° boundary. For example, averaging 359° and 1° arithmetically gives 180°, which is wrong.

Use shortest-angle interpolation:

\`\`\`kotlin
private fun smoothAngle(current: Float, target: Float, factor: Float): Float {
    val delta = normalizeSigned(target - current)
    return normalize360(current + delta * factor)
}
\`\`\`

Use a moderate factor and tune it on real devices. The current factor of 0.2 is a reasonable experiment, not a universal constant. A large factor feels responsive but jittery; a small factor feels stable but delayed.

Use an explicit initialization flag:

\`\`\`kotlin
private var hasHeading = false
\`\`\`

Do not use \`0f\` as an “uninitialized” sentinel because north is a valid heading.

---

## 5. Magnetic declination implementation

### 5.1 Why it matters

A geographic bearing to the Kaaba is measured from true north. A magnetic compass uses the local magnetic field. The difference varies by location and changes over time.

Without declination correction, the Qibla arrow can be offset even when the sensor axes and drawing math are correct. This is usually not a 180-degree error, but it is a real correctness defect and should be fixed as part of the same engineering work.

### 5.2 Recommended Android API

Use:

\`android.hardware.GeomagneticField\`

Construct it with:

- User latitude in degrees.
- User longitude in degrees.
- Altitude in meters (use a documented approximation if altitude is unavailable).
- Current time in milliseconds.

Read \`getDeclination()\`, then convert magnetic heading to true heading exactly once.

Pseudo-code:

\`\`\`kotlin
val field = GeomagneticField(
    latitude.toFloat(),
    longitude.toFloat(),
    altitudeMeters,
    System.currentTimeMillis()
)
val declination = field.declination
val trueHeading = normalize360(magneticHeading + declination)
\`\`\`

Recompute declination when the location changes materially or the app resumes after a long interval. It does not need to be recalculated on every sensor event.

### 5.3 Test declination separately

Unit-test the conversion with controlled values:

- Magnetic heading 100°, declination +5° → true heading 105°.
- Magnetic heading 100°, declination -5° → true heading 95°.
- Magnetic heading 358°, declination +5° → true heading 3°.
- Magnetic heading 2°, declination -5° → true heading 357°.

These tests verify sign and wraparound. A real-device compass test verifies the actual sensor reference.

---

## 6. Verify the Qibla bearing calculation

### 6.1 Formula

For user latitude \(\phi_1\), user longitude \(\lambda_1\), Kaaba latitude \(\phi_2\), and Kaaba longitude \(\lambda_2\), all converted to radians:

\`\`\`text
deltaLongitude = lambda2 - lambda1

y = sin(deltaLongitude) * cos(phi2)

x = cos(phi1) * sin(phi2)
    - sin(phi1) * cos(phi2) * cos(deltaLongitude)

initialBearing = atan2(y, x)
bearingDegrees = normalize360(toDegrees(initialBearing))
\`\`\`

This is the initial great-circle bearing from the user's location to the Kaaba.

### 6.2 Input validation

Before calculating:

- Latitude must be finite and in [-90, 90].
- Longitude must be finite and in [-180, 180].
- Reject NaN and infinity.
- Do not silently convert invalid strings to zero.
- If the user's location is effectively the Kaaba location, the bearing is undefined/unstable; show an explanatory edge-case state rather than pretending that 0° is meaningful.
- Keep latitude and longitude order explicit. Do not accidentally swap them.

The current preferences are parsed with \`toDoubleOrNull()\`, which handles malformed strings but does not by itself validate geographic ranges or finite values.

### 6.3 Known reference calculations

Use these as regression fixtures (rounded to one decimal place):

| Test location | Approximate initial Qibla bearing |
|---|---:|
| Cairo, Egypt (30.0444, 31.2357) | 136.1° |
| New York, USA (40.7128, -74.0060) | 58.5° |
| London, UK (51.5074, -0.1278) | 119.0° |

These are useful sanity checks, not substitutes for a high-precision reference library or an independently verified geographic test set.

### 6.4 Test the bearing independently from the compass

A location test should call only the geographic bearing function. It should not instantiate sensors, a view, an Activity, or Android UI.

This isolates a wrong coordinate or formula from a wrong sensor or arrow.

---

## 7. Make the compass rendering unambiguous

### 7.1 Use one source of truth

The view should receive:

- Qibla bearing from true north.
- Device heading from true north.
- Sensor quality/live-heading state.
- Optional calibration state.

Prefer calculating the relative angle outside the view and passing it in. The view should not independently invent its own angle convention.

### 7.2 Compass-ring behavior

If the ring rotates beneath a fixed phone pointer:

- Rotate the north ring by \`-deviceHeadingTrueDegrees\`.
- Keep the phone-forward marker fixed at the top of the screen.
- Draw the Qibla indicator at \`relativeQiblaDegrees\`.

If the design instead rotates the Qibla arrow around a fixed ring, make the same relative angle visible. Do not combine a rotating ring and a second independent rotation that applies heading twice.

### 7.3 Visual semantics

The user should immediately understand:

- The fixed top marker represents the direction the phone's top edge faces.
- N/E/S/W labels represent geographic directions.
- The Qibla pointer represents the direction to turn toward.
- The center is the phone's current heading reference.
- A “facing Qibla” state appears only when live heading and quality are sufficient.

Use a distinct Qibla marker and north marker. Do not use two similar purple arrows whose meanings are unclear.

### 7.4 Alignment threshold

The current threshold is about 3°. That is likely too strict for a handheld device and can flicker.

Start testing with:

- Within 5°: “Facing Qibla” / aligned.
- Within 10°: “Almost there.”
- Outside 10°: show left/right turn guidance.

These are UX starting values, not religious/legal tolerances or guaranteed sensor accuracy. Tune only after observing real-device noise. Require a usable sensor-quality state before declaring alignment.

Use hysteresis so the status does not flicker at the boundary. For example, enter aligned at 5° and leave aligned only after the difference exceeds 7°. Keep the chosen values in one named configuration object or constants.

### 7.5 Turn guidance

Use the signed relative angle:

- Positive angle: Qibla is to the right/clockwise; tell the user to turn right.
- Negative angle: Qibla is to the left/counter-clockwise; tell the user to turn left.
- Near zero: facing Qibla.
- Near ±180°: Qibla is behind the user; tell them to turn around, then refine the left/right instruction.

The wording and sign must be validated physically. A sign error here can recreate the original “turn your back toward it” experience even if the compass arrow itself is correct.

### 7.6 Accessibility and readability

- Use text in addition to color. Green alone must not mean aligned.
- Keep labels readable at larger font sizes.
- Add content descriptions to custom-drawn compass elements where possible.
- Avoid flashing or rapidly changing messages.
- Do not rely on subtle animation to communicate direction.
- Ensure the compass is still understandable with reduced motion or accessibility services.

---

## 8. Location correctness and freshness

The current Qibla screen reads saved coordinates from \`Prefs.latitude(this)\` and \`Prefs.longitude(this)\`. The location acquisition itself is handled elsewhere in the Prayers flow.

The Qibla screen should make the source and age of the coordinates clear enough for the user to trust them.

### 8.1 Required location states

Support at least:

1. No saved location.
2. Permission denied.
3. Location services disabled.
4. Valid saved location.
5. Location is old or its source is unknown.
6. Invalid coordinates.
7. Location lookup in progress.
8. Location lookup failed.

Do not request location permission simply by opening the Qibla screen if the app already has a saved location. Explain why permission is needed before requesting it.

### 8.2 Refresh action

Provide a visible “Update location” action when the stored location is old or the user suspects it is wrong.

After refresh:

- Validate coordinates.
- Update the displayed location.
- Recalculate Qibla bearing.
- Recalculate magnetic declination.
- Preserve the current sensor pipeline.
- Show a clear error if no fix is available.

Do not silently replace a valid saved location with (0, 0), an approximate fallback, or a failed lookup result.

### 8.3 Location privacy

Coordinates are needed to calculate Qibla direction, but they do not need to be sent to a remote server. Prefer on-device calculation. Do not introduce network location services or analytics transmission for this feature without a separate, explicit product decision.

---

## 9. UX redesign while preserving Swizy's simple visual style

The goal is not to redesign Swizy. Keep the existing dark/purple visual language and the straightforward card-based layout. Make the Qibla screen clearer and more trustworthy without adding decorative complexity.

### 9.1 Recommended screen hierarchy

1. **Main direction card**
   - Large compass.
   - Clear Qibla pointer.
   - Fixed phone-forward marker.
   - One short live instruction: “Turn right 24°” or “Turn left 12°”.
   - “Facing Qibla” only when alignment is genuinely established.

2. **Bearing information**
   - “Qibla from true north: 136.1°” (example only; calculate from the actual location).
   - Current location label or coordinates in a secondary line.
   - Optional location update action.

3. **Compass status**
   - Good / weak / unavailable.
   - One short action if calibration or interference is suspected.

4. **Help section**
   - Explain that the phone's top edge should face the Qibla.
   - Explain magnetic interference.
   - Explain that a phone compass can be inaccurate near magnets or metal.

Do not make the user read a paragraph before they can understand which way to turn.

### 9.2 Suggested live messages

| State | Suggested message |
|---|---|
| Live heading good, Qibla to right | Turn right 24° |
| Live heading good, Qibla to left | Turn left 12° |
| Within alignment threshold | Facing Qibla |
| Qibla directly behind | Qibla is behind you — turn around |
| Accuracy weak | Compass signal is weak. Move away from magnetic objects. |
| Calibration likely needed | Slowly move your phone in a figure-eight. |
| Sensor unavailable | Live compass is unavailable on this device. |
| Location missing | Set your location to calculate Qibla. |
| Location invalid | Location data is invalid. Update your location. |
| Heading not initialized yet | Finding compass direction… |

Avoid messages that imply the app knows the user is facing the Kaaba with absolute precision. Prefer “Compass indicates you are facing Qibla” if sensor accuracy is limited.

### 9.3 Avoid these UX mistakes

- Do not show “Aligned” when no sensor has produced a valid heading.
- Do not show a frozen arrow as if it were live.
- Do not make the arrow's direction depend on whether the user interprets the phone screen or the back of the phone as its front.
- Do not put raw sensor numbers in the primary UI.
- Do not force calibration when the sensor reports good accuracy.
- Do not add a 180° visual flip to compensate for an unverified axis bug.
- Do not change the rest of the app's navigation or Sibha screen as part of this task.

---

## 10. Suggested code organization

The current implementation lives inside \`MainActivity.kt\`. It is acceptable to make the first correctness fix there if that minimizes risk. Once the behavior is tested, separate the logic so it can be tested without rendering the whole Activity.

Suggested small components:

### 10.1 \`QiblaCalculator.kt\`

Responsibilities:

- Validate coordinates.
- Calculate the initial bearing to the Kaaba.
- Normalize angles.
- Calculate signed relative angle.
- Expose pure functions with no Android UI dependency.

### 10.2 \`CompassHeadingProvider.kt\`

Responsibilities:

- Select supported sensors.
- Register/unregister with lifecycle.
- Remap axes for display rotation.
- Produce magnetic heading samples.
- Track accuracy and sample freshness.
- Handle rotation-vector and accelerometer/magnetometer fallback.
- Avoid UI-specific text and drawing.

### 10.3 \`MagneticDeclinationProvider.kt\`

Responsibilities:

- Calculate declination from coordinates, altitude, and time using \`GeomagneticField\`.
- Cache/recalculate when location or date changes.
- Convert magnetic heading to true heading exactly once.

This can initially be a small utility rather than a large abstraction.

### 10.4 \`CompassView.kt\`

Responsibilities:

- Draw the compass.
- Receive explicit heading and Qibla values.
- Draw the fixed forward marker and Qibla direction.
- Render a quality/alignment state.
- Avoid reading preferences, requesting permissions, or registering sensors.

### 10.5 Activity responsibilities

\`MainActivity\` should:

- Read the saved location.
- Ask the heading provider for live samples.
- Calculate declination and Qibla bearing.
- Update the screen state.
- Handle location update and help actions.
- Start/stop sensor collection at the correct lifecycle points.

Do not introduce a dependency-injection framework or a large architecture migration solely for the Qibla repair. A few focused Kotlin classes are enough.

---

## 11. Testing plan

A Qibla finder cannot be validated by compilation alone. It needs pure math tests, sensor-convention tests, and physical phone tests.

### 11.1 Pure unit tests

Test the following:

**Bearing calculations**
- Cairo fixture returns approximately 136.1°.
- New York fixture returns approximately 58.5°.
- London fixture returns approximately 119.0°.
- Invalid latitude is rejected.
- Invalid longitude is rejected.
- NaN and infinity are rejected.
- Kaaba-coincident input is handled as an undefined/edge case.

**Angle normalization**
- \`normalize360(0) = 0\`
- \`normalize360(360) = 0\`
- \`normalize360(-10) = 350\`
- \`normalize360(725) = 5\`
- \`normalizeSigned(350) = -10\`
- \`normalizeSigned(10) = 10\`
- \`normalizeSigned(180)\` follows the documented boundary convention.

**Relative angle**
- Qibla 136°, heading 136° → 0°.
- Qibla 136°, heading 112° → +24° (turn right).
- Qibla 136°, heading 148° → -12° (turn left).
- Qibla 10°, heading 350° → +20°.
- Qibla 350°, heading 10° → -20°.
- Qibla 180°, heading 0° → 180°.

**Declination**
- Positive and negative declination.
- Wraparound near 0° and 360°.
- Verify declination is applied exactly once.

### 11.2 Display-axis tests

For each display rotation (0°, 90°, 180°, 270°):

- Point the displayed top edge north; expect approximately 0°.
- Point the displayed top edge east; expect approximately 90°.
- Point the displayed top edge south; expect approximately 180°.
- Point the displayed top edge west; expect approximately 270°.

Do not only test portrait. A mapping that works in portrait can still be wrong in landscape.

### 11.3 Physical test procedure

Use a phone with a known-good compass and test away from metal structures:

1. Remove magnetic cases and magnetic accessories.
2. Move away from laptops, speakers, cars, metal desks, and magnetic mounts.
3. Open a trusted compass app and compare cardinal headings.
4. Set the phone flat with its top edge pointing north.
5. Repeat for east, south, and west.
6. Repeat with the phone upright at a comfortable angle.
7. Repeat in landscape if the app supports landscape.
8. Compare Swizy's heading with the trusted compass.
9. Compare Swizy's Qibla bearing with an independent Qibla calculation for the same coordinates.
10. Face the calculated Qibla direction physically and confirm the app says the phone is aligned, not 180° away.

Record the phone model, Android version, case/accessory state, display rotation, sensor type, location, declination, raw magnetic heading, final true heading, and observed error.

### 11.4 Real-world location checks

Use more than one location. At minimum:

- Cairo or another known Egyptian location.
- A location in Europe.
- A location in North America.

This helps catch assumptions that accidentally work only in Egypt.

### 11.5 Sensor failure tests

- No rotation-vector sensor.
- No magnetometer.
- Sensor reports unreliable accuracy.
- Sensor accuracy drops while the screen is open.
- App is backgrounded and resumed.
- Screen rotates while Qibla is open.
- Location is missing or malformed.
- Location changes while the compass is open.
- Sensor event stream pauses or becomes stale.
- Device is near a strong magnetic source.

The UI must not claim alignment in any state where it cannot trust the heading.

---

## 12. Instrumentation and debug mode

During development, add a temporary debug panel or log line. Do not leave a noisy technical panel in the normal user experience.

Recommended fields:

| Field | Purpose |
|---|---|
| Sensor type | Confirms which sensor path is active. |
| Display rotation | Confirms axis remapping. |
| Raw magnetic azimuth | Helps isolate sensor output. |
| Remapped magnetic azimuth | Helps isolate screen-axis handling. |
| Magnetic declination | Confirms location correction. |
| Final true heading | The heading used by the Qibla comparison. |
| Qibla true bearing | The geographic target. |
| Signed relative angle | Confirms left/right instruction. |
| Accuracy status | Shows Android's sensor quality category. |
| Last sensor sample age | Detects frozen/stale heading. |
| Location coordinates/source | Detects stale or swapped coordinates. |

Use logs only for diagnosis. Avoid logging precise coordinates in production logs unless there is a clear privacy justification. A user-facing diagnostic export should require explicit user action and should explain what it contains.

---

## 13. Implementation sequence and commit plan

Keep each commit reviewable. Do not combine the Qibla repair with app-wide cleanup, branding, Sibha, or unrelated navigation changes.

### Commit 1 — \`test: add qibla bearing and angle regression tests\`

- Extract or isolate the pure bearing and angle helpers.
- Add known-location fixtures.
- Add normalization and signed-angle tests.
- Add invalid-coordinate tests.
- Do not change sensor behavior yet.

**Exit condition:** all pure math tests pass.

### Commit 2 — \`fix: define compass heading and display-axis convention\`

- Give heading values explicit names.
- Make display rotation remapping a testable helper.
- Add explicit first-heading state.
- Track valid sensor inputs.
- Ensure stale/uninitialized sensor data cannot be treated as a valid heading.

**Exit condition:** physical cardinal-direction test passes in each supported display rotation.

### Commit 3 — \`fix: convert magnetic compass heading to true north\`

- Add \`GeomagneticField\` declination calculation.
- Convert magnetic heading to true heading once.
- Recompute when location/time requires it.
- Add sign and wraparound tests.

**Exit condition:** Swizy's true heading agrees with an independent compass/reference within the expected device tolerance.

### Commit 4 — \`fix: make qibla alignment and turn guidance reliable\`

- Calculate signed relative angle in one place.
- Correct left/right wording from physical tests.
- Add alignment hysteresis.
- Require valid sensor state before displaying aligned.
- Handle the directly-behind case.

**Exit condition:** no 180° reversal; left/right guidance is physically correct.

### Commit 5 — \`ux: clarify qibla compass and sensor status\`

- Improve the primary instruction.
- Add good/weak/unavailable states.
- Add calibration/interference guidance only when appropriate.
- Clarify the top-edge convention.
- Keep Swizy's simple visual style.

**Exit condition:** a first-time user can understand which way to turn without reading a long explanation.

### Commit 6 — \`test: cover qibla sensor lifecycle and failure states\`

- Verify pause/resume.
- Verify screen rotation.
- Verify fallback sensor path.
- Verify missing/stale location and sensor states.
- Document tested device models.

**Exit condition:** acceptance checklist below is complete.

---

## 14. Acceptance checklist

Do not mark the issue fixed until all required items are checked.

### Geographic calculation

- [ ] Kaaba coordinates are correct and centralized.
- [ ] Bearing uses the initial great-circle formula.
- [ ] Cairo fixture is approximately 136.1°.
- [ ] New York fixture is approximately 58.5°.
- [ ] London fixture is approximately 119.0°.
- [ ] Invalid coordinates are rejected.
- [ ] The Kaaba-coincident edge case is handled.

### Sensor heading

- [ ] Heading convention is explicitly clockwise from north.
- [ ] Heading describes the phone's displayed top edge.
- [ ] Display rotation mapping is physically verified.
- [ ] Rotation-vector path is verified.
- [ ] Accelerometer/magnetometer fallback is verified or clearly reported unsupported.
- [ ] No uninitialized heading is treated as north.
- [ ] Stale samples do not appear live.
- [ ] Circular smoothing does not jump at 0°/360°.

### North reference

- [ ] Geographic Qibla bearing uses true north.
- [ ] Magnetic declination is calculated from the user's location and current date.
- [ ] Declination sign is verified.
- [ ] Declination is applied to the device heading exactly once.
- [ ] No arbitrary 180° correction exists.

### Compass and guidance

- [ ] Fixed phone-forward marker is clear.
- [ ] Qibla direction is clear.
- [ ] Positive relative angle means turn right.
- [ ] Negative relative angle means turn left.
- [ ] Directly-behind state is handled.
- [ ] Alignment requires a valid live heading.
- [ ] Alignment does not flicker.
- [ ] Color is not the only status indicator.

### Location and UX

- [ ] Missing location has a clear next action.
- [ ] Invalid/stale location is handled.
- [ ] Location update recalculates bearing and declination.
- [ ] Sensor-unavailable state is honest.
- [ ] Calibration guidance appears only when useful.
- [ ] Main instruction is short and understandable.
- [ ] No unrelated screen or navigation redesign is included.

### Release verification

- [ ] App builds successfully.
- [ ] Unit tests pass.
- [ ] Physical cardinal-direction test passes.
- [ ] Qibla-facing test passes on a real device.
- [ ] At least one non-Egypt location is checked.
- [ ] Device model and Android version are recorded.

---

## 15. Critical diagnosis of the reported 180° reversal

A 180° reversal is too large to explain with ordinary magnetic declination. Declination may cause a smaller regional offset; it does not normally explain the user needing to face the opposite direction.

Investigate in this order:

1. **Confirm the intended phone edge.** Make sure the app's instruction means the screen's top edge, not the back of the phone or the bottom edge.
2. **Compare raw heading to a trusted compass.** If raw heading is already 180° wrong, investigate sensor-axis mapping and the meaning of the selected device axis.
3. **Compare portrait and landscape.** If the error changes with display rotation, the remapping is wrong or the app's edge convention is inconsistent.
4. **Check the relative-angle sign.** If the numeric heading is right but the drawn arrow is behind, the rendering math or fixed-pointer semantics are wrong.
5. **Check true/magnetic north conversion.** This fixes a systematic smaller offset, but should not be used to hide an axis reversal.
6. **Check the actual stored coordinates.** Swapped latitude/longitude or an incorrect location can produce a wrong geographic bearing.
7. **Check sensor quality and interference.** Magnetic interference can make readings unstable or biased, though a stable exact 180° error more strongly suggests an axis/convention problem.

For diagnosis, record all of these values at the same moment:

- Qibla true bearing.
- Raw sensor azimuth.
- Remapped magnetic heading.
- Magnetic declination.
- Final true heading.
- Signed relative angle.
- Display rotation.
- Sensor type and accuracy.

This makes it possible to identify the exact stage where the direction flips.

### Important rule

Do not merge a temporary \`+180°\` or \`-180°\` patch into production unless a documented sensor/device-specific convention has been proven and the patch is covered by tests. A global 180° offset would conceal the underlying error and could make a correctly behaving device wrong.

---

## 16. Recommended implementation decision

The safest next step is **not** to redesign the compass or immediately rewrite all of \`MainActivity.kt\`.

First add a small testable Qibla calculation/angle layer and a temporary diagnostic readout. Then run the four-cardinal-direction test on the actual phone where the reversal was observed. The results will show whether the defect is:

- sensor axis/remapping,
- display rotation,
- magnetic-versus-true north,
- drawing convention,
- location data,
- or magnetic interference.

After that diagnosis, make the smallest correction that fixes the proven cause. Only then improve turn guidance and visual status.

**Definition of done:** when the user points the phone's top edge toward the Kaaba direction, Swizy's compass indicates alignment; when the phone points 180° away, Swizy clearly indicates that Qibla is behind them. The same behavior must hold across the display orientations the app claims to support, without a hard-coded reversal.
