# NRC Environment Robustness Lab

## Purpose

The Environment Robustness Lab is NRC's first differentiated workflow. It evaluates how a perception pipeline's observable sensor and inference signals change when the same input snapshot is subjected to controlled environmental stress profiles.

The first prototype supports clear baseline, heavy rain, fog, low light, and dust/smoke profiles.

## Current prototype behavior

The common Kotlin evaluator applies deterministic perturbations to a RobotTelemetry snapshot:

- LiDAR return dropout and small range offsets.
- Event sample retention and deterministic synthetic noise events.
- Detection-confidence scaling.
- Inference-latency scaling.

It reports input and retained sample counts, added noise-event count, mean detection confidence before/after, and inference latency before/after.

Every run also receives a versioned ExperimentManifest containing an experiment ID, source type and ID, source sequence/timestamp, model identifier/version, selected scenario and exact perturbation coefficients. In the current demo, the source and model are explicitly identified as simulated. The source timestamp is simulation time, not wall-clock time.

## Scientific limitations

The current profiles are illustrative software coefficients. They are not calibrated models of atmospheric optics, event-camera physics, LiDAR backscatter, sensor failure distributions, or real model accuracy. The displayed confidence values are transformed simulator outputs, not measured precision, recall, or mAP.

The prototype must never be presented as proof of real-world robustness or as a safety certification.

## Validation roadmap

1. Serialize the versioned experiment manifest with dataset, sensor configuration, model identity, software commit and run parameters.
2. Ingest real sensor recordings and preserve their original timestamps.
3. Add dataset adapters and benchmark splits for relevant public adverse-weather/event-vision datasets.
4. Fit and validate perturbation profiles against measured sensor degradation.
5. Add ground-truth-aware metrics such as precision, recall, mAP, tracking continuity and localisation error where annotations permit.
6. Compare conventional and spiking models under identical input conditions.
7. Capture hardware-specific latency, memory, power and energy-per-inference measurements.
8. Export reproducible experiment reports with dataset, model, commit and configuration provenance.

## Product principle

NRC complements established visualization, simulation and fleet-operations tools. It should focus on repeatable perception evaluation, neuromorphic benchmarking and evidence-backed failure analysis rather than rebuilding a general-purpose robot dashboard.
