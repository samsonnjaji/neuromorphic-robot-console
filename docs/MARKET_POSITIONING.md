# NRC Market Positioning

**Research date:** 15 November 2026

## Strategic decision

NRC should not compete head-on as another generic robot dashboard, fleet manager, or high-fidelity simulator. It should become an evaluation and evidence layer for autonomous perception under adverse conditions, with first-class support for event-based sensing and neuromorphic models.

## Existing product categories

### Robotics visualization and data observability

Foxglove supports live ROS 2 visualization, recorded-data playback, multimodal logs, recording organization, dataset curation and incident investigation.

PlotJuggler provides high-performance time-series visualization, live streaming, data transforms and ROS 1/2 integrations.

### Robot operations and fleet management

InOrbit provides robotics observability, incident workflows, fleet operations, analytics and remote interventions.

Formant provides fleet observability and orchestration, cross-vendor operations, AI-assisted fleet workflows and synchronized operational records.

### Simulation and synthetic data

NVIDIA Isaac Sim supports physically based robotics simulation, configurable sensors, synthetic data generation, software-/hardware-in-the-loop testing and ROS 2 integration.

### Neuromorphic benchmarking

NeuroBench provides a community-oriented framework for benchmarking neuromorphic algorithms and systems. The research literature identifies standardized comparison across conventional and neuromorphic approaches as an important need.

## NRC's intended wedge

NRC will focus on the workflow that connects:

1. Environmental condition and sensor degradation.
2. Synchronized multimodal sensor inputs.
3. Conventional and neuromorphic perception outputs.
4. Perception-to-action traces and failure events.
5. Reproducible replay and controlled model comparisons.
6. Evidence-backed reports for research and deployment decisions.

This is a product-positioning hypothesis, not a claim that competitors lack every listed feature. NRC's differentiation must be demonstrated through an integrated workflow, validated datasets, measurable evaluation quality and usability.

## Integration strategy

NRC should integrate with, rather than replace, established tools:

- ROS 2 for robot middleware and topic access.
- MCAP and ROS bag for recorded data.
- Foxglove-compatible workflows where practical.
- CARLA and NVIDIA Isaac Sim for simulation inputs.
- Public event-vision and adverse-weather datasets for repeatable benchmarks.
- Hardware-specific telemetry for edge and neuromorphic energy measurements.

## Initial customer segments

- University robotics and neuromorphic research laboratories.
- Autonomous robotics and event-vision startups.
- ADAS and autonomous vehicle research teams.
- UAV, agricultural and disaster-response robotics developers.
- Industrial robotics integrators evaluating perception reliability.

## Business model hypotheses

- Academic/research licences and laboratory deployments.
- Developer and startup subscriptions.
- Enterprise validation and integration contracts.
- Paid benchmark campaigns and reproducibility reports.
- Training and technical support for university and industry teams.

## Evidence before commercial claims

NRC must measure, rather than assume:

- Time required to reproduce and diagnose a failure.
- Number and diversity of environmental test conditions.
- Model performance degradation against labelled ground truth.
- Inference latency and energy per inference on target hardware.
- Reduction in repeated experiments and deployment regressions.
- Research-team adoption, retention and willingness to pay.

The first release should be transparent about simulated versus measured data and should never imply safety certification.
