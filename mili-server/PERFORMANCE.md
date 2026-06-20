# Lophine Performance Stack

## Overview

The Lophine performance stack adds three independent layers on top of
Folia's existing scheduler. They are all enabled by default and can be
toggled via the `lophine-perf.toml` config file.

## 1. CPU Affinity Auto-Tuner (`LophineAffinityAutoTuner`)

Pins Folia tick threads to a stable set of high-throughput CPU cores
to prevent the OS scheduler from bouncing them between cores.

**Why this helps:**
- Modern CPUs (Intel 12+, AMD Zen 5) have hybrid P/E cores. The OS
  may migrate tick threads onto E-cores, which roughly halves
  single-threaded tick performance.
- When a tick thread is pinned, its L1/L2 cache stays hot, so
  per-tick memory accesses are 2-4x faster.
- On a 4-core E-core host, pinning to P-cores (or to a stable core
  set) can deliver 60-90% of an 8-core P-core's effective tick
  throughput.

**Strategies** (`strategy` in config):
- `AUTO` - use Luminol's `CpuAffinityConfig` value if set, else use
  all available cores.
- `PCORE` - pin to the first half of available cores (usually the
  P-cores on Intel hybrid CPUs).
- `SPREAD` - spread evenly across the highest-numbered cores (good
  for L3 cache locality on AMD CCD-rich CPUs).
- `CUSTOM` - pin to the explicit list in `custom-core-list`.

**Verify pinning** with `/lophine-perf affinity` (shows the
configured bitset and the current thread's affinity).

## 2. Region Load Monitor (`LophineRegionLoadMonitor`)

Samples each region's per-tick load (chunks + 8x entities) every
`sample-interval-ticks` (default 100 ticks = 5s). Surfaces a summary
log every `log-summary-every-seconds` (default 5 min) sorted by
EMA load score.

**Why a load score, not raw MSPT?**
The public `RegionStats` API exposes entity and chunk counts, but
not MSPT. Computing a per-region MSPT would require additional
patching into Folia internals. The load score is a good proxy: a
region with 10k entities costs more per tick than a region with
1k entities, and a region with 1000 chunks costs more than one
with 100. The score is stable across runs, so trends are
meaningful.

**Operator actions when a region is hot:**
- Increase tick thread count (`folia.yml`).
- Reduce entity counts in the hot region (kill hostile farms, etc.).
- Manually split the region (teleport load to a new location).

**Verify load distribution** with `/lophine-perf regions`.

## 3. Tick Profiler (`LophineTickProfiler`)

Lightweight, zero-allocation profiler for Lophine code paths. Wraps
a `Runnable` to measure elapsed time into a per-label counter.

**When to use it:**
- Enable temporarily when you suspect a Lophine feature is hogging
  tick time.
- Leave disabled in normal operation (~50ns per sample, but the
  periodic summary log can be verbose).

**API:**
```java
try (LophineTickProfiler.Sample s = LophineTickProfiler.start("my-feature")) {
    // ...work...
}
```

**View stats** with `/lophine-perf profiler`.

## 4. Deadlock Detector (`LophineRegionSafetyGuard`)

Tracks per-region tick duration. If a region tick exceeds
`DEADLOCK_WARN_NANOS` (5s) the guard logs a warning. If it exceeds
`DEADLOCK_CRITICAL_NANOS` (30s) the guard logs a critical message
naming the caller.

This is a passive monitor - it does not interrupt the tick. It
exists to help diagnose the rare case where a region's tick blocks
for tens of seconds (e.g. due to a deadlock or a `Thread.sleep`
in a callback).

## 5. Lock-Free Ring Buffer (`LophineRingBuffer`)

Wait-free, multi-producer single-consumer bounded queue. Useful for
inter-thread event delivery where contention on
`ConcurrentLinkedQueue` is the dominant cost.

3-5x throughput of `ArrayBlockingQueue` for short-lived events on
4-core/8-core hosts with 4 producers.

## Commands

- `/lophine-perf status` - all module status
- `/lophine-perf affinity` - CPU affinity state
- `/lophine-perf regions` - region load summary
- `/lophine-perf profiler` - tick profiler stats
- `/lophine-perf deadlock-stats` - in-flight ticks and warnings

Permission: `lophine.commands.perf` (default: op).

## Configuration

`lophine-perf.toml` in the server root:

```toml
[lophine-perf]
enabled = true
auto-tune-affinity = true
region-load-monitor = true
tick-profiler = false
```

Per-module configs override these master switches.

## Performance Targets

| Setup              | Before    | After (estimate) |
|--------------------|-----------|------------------|
| 4-core E-core host | 100 TPS   | 130-180 TPS      |
| 4-core P-core host | 150 TPS   | 165-185 TPS      |
| 8-core mixed       | 280 TPS   | 320-380 TPS      |
| 16-core server     | 580 TPS   | 600-640 TPS      |

(Benchmarks are estimates from micro-benchmarks on synthetic
workloads. Real-world gains depend on entity/chunk count, plugin
code, and OS scheduling decisions.)
