# Third-party notices

Katib's own code is MIT-licensed (see `LICENSE`). It bundles or downloads the following:

| Component | How it's used | License |
|---|---|---|
| sherpa-onnx 2.0.1 (`app/libs/sherpa-onnx-static-2.0.1.aar`) | bundled native runtime | Apache-2.0 |
| ONNX Runtime (inside sherpa-onnx) | bundled | MIT |
| IBM Plex Sans Arabic, IBM Plex Mono, Instrument Serif, Amiri (`app/src/main/res/font`) | bundled fonts | SIL Open Font License 1.1 |
| SQLCipher for Android | Gradle dependency | BSD-style (Zetetic) |
| AndroidX / Jetpack Compose / Room / WorkManager | Gradle dependencies | Apache-2.0 |
| Apache Commons Compress | Gradle dependency | Apache-2.0 |
| Qwen3-ASR 0.6B (sherpa-onnx export) | downloaded at runtime by the user | Apache-2.0 |
| Whisper base (sherpa-onnx export) | downloaded at runtime by the user | MIT |
| Silero VAD | downloaded at runtime by the user | MIT |
| pyannote segmentation 3.0 | downloaded at runtime by the user | MIT |
| WeSpeaker ResNet34 (VoxCeleb) | downloaded at runtime by the user | Apache-2.0 (model weights: CC BY 4.0) |

Model weights are not included in this repository. They're fetched from the sherpa-onnx GitHub
releases on first use and checked against SHA-256 hashes pinned in `asr/Models.kt`. Check each upstream
project for its current license before redistributing.
