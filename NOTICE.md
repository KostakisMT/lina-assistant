# NOTICE – Lizenzen und Attribution

Lina – VoiceFirst Assistant
Copyright 2026 Die Lina-Mitwirkenden

Der Quellcode dieses Projekts steht unter der **Apache License 2.0** (siehe [LICENSE](LICENSE)).

Die App nutzt Modelle und Bibliotheken Dritter mit teils abweichenden Lizenzen.
Die Modelle sind **nicht** Teil dieses Repositories – sie werden per
`scripts/download-models.sh` bezogen.

## Sprachmodelle

| Komponente | Quelle | Lizenz | Hinweis |
|---|---|---|---|
| TTS-Stimme `de_DE-dii-high` | [OpenVoiceOS / pipertts_de-DE_dii](https://huggingface.co/OpenVoiceOS) | **CC BY-NC-SA 4.0** | Nicht-kommerziell. Nutzung zulässig, da das Projekt gemeinnützig getragen wird und kein kommerzieller Vertrieb stattfindet (siehe ADR-016). Wer die App kommerziell weiterverbreiten will, muss diese Stimme ersetzen. |
| Whisper base (int8, ONNX) | [OpenAI Whisper](https://github.com/openai/whisper) via [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | MIT | |
| Vosk `vosk-model-small-de` | [Alpha Cephei / Vosk](https://alphacephei.com/vosk/models) | Apache 2.0 | Fallback-STT |
| OpenWakeWord Basismodelle (`melspectrogram.onnx`, `embedding_model.onnx`) | [dscripka/openWakeWord](https://github.com/dscripka/openWakeWord) | Apache 2.0 | |
| Weckwort-Classifier `hey_lina_v1.onnx` | dieses Projekt (`training/`) | Apache 2.0 | Trainiert u.a. mit synthetischen Piper-TTS-Samples und [MUSAN](https://www.openslr.org/17/) (CC BY 4.0) als Negativdaten |

## Bibliotheken (Auswahl)

| Bibliothek | Lizenz |
|---|---|
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (TTS/STT-Runtime) | Apache 2.0 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) (Wake Word) | MIT |
| [Vosk Android](https://github.com/alphacep/vosk-api) | Apache 2.0 |
| [anthropic-java](https://github.com/anthropics/anthropic-sdk-java) (Claude API) | MIT |
| AndroidX / Jetpack Compose / Media3 (ExoPlayer) | Apache 2.0 |

Vollständige Abhängigkeiten: siehe `app/build.gradle.kts`.
