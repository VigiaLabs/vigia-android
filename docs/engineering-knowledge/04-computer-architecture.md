# Computer architecture: design for the machine that will run it

Computer architecture explains why an algorithm that looks linear can still miss a real-time budget. For VIGIA, the phone and Pi have different power, memory, scheduling, and trust boundaries. The same mental model helps in an interview and when tuning an edge pipeline.

## From instruction to observable latency

Reason through the path: instruction fetch/decode, branch prediction, execution units, cache hierarchy, TLB, main memory, device driver, DMA, and network/storage. The expensive event is often a cache miss, allocation, copy, lock convoy, page fault, or thermal frequency drop rather than the arithmetic itself.

Use a budget for each stage and measure P50/P95/P99. Average FPS or latency hides outliers that a driver experiences as a stall. Separate cold start/model load from steady-state inference.

## Locality, memory, and data movement

Temporal locality reuses recently touched data; spatial locality benefits from contiguous access. Structure hot data so the cache line contains what the loop needs. Avoid false sharing between real-time threads. Prefer bounded buffers and ownership transfer over unbounded queues and accidental copies. DMA and zero-copy techniques help only when the lifetime and synchronization rules are explicit.

When a buffer crosses a thread or device boundary, define who may mutate it and when. A lock-free data structure is not automatically faster or safer; memory ordering, ABA/reclamation, and backpressure still need proof.

## Parallelism and real-time behaviour

Distinguish throughput from latency. Pinning a thread can reduce migration jitter but can also starve other work. A real-time loop needs bounded work, pre-allocation where appropriate, deadline/timeout handling, and a shutdown path. GPU/NPU/CPU selection is a measured trade-off across accuracy, throughput, memory, temperature, and power.

Quantization and SIMD/vector extensions can increase throughput while changing accuracy. Keep a reference model, a calibration set, and a regression threshold. The RISC-V specification is a useful reminder that an ISA contract, microarchitecture, and implementation-specific performance are different claims.

## Trust at the hardware boundary

Hardware-backed keys, secure elements, and boot/integrity features reduce key-extraction risk; they do not prove that a remote device is the correct owner or that an app request is authorized. Pairing still needs an authenticated protocol, freshness, challenge binding, and revocation. A static key does not provide forward secrecy by itself.

The Pi pipeline should therefore separate:

- sensor authenticity and packet integrity;
- model/inference confidence and calibration;
- event identity, sequence, and replay protection;
- transport TLS/client authentication;
- server authorization and ownership.

## Mobile-specific architecture

Android's process and power model is part of computer architecture from the app's point of view. A process can be killed while the user is away; a coroutine can be cancelled; radio, microphone, and GPS availability can change. Durable checkpoints, lifecycle-aware collection, bounded work, and explicit availability are correctness features, not UI polish.

## Interview follow-ups

- Why can two `O(n)` loops have different latency distributions?
- What does a cache miss or false-sharing event do to a hot path?
- When is a lock-free queue worse than a mutex?
- How do you prove a quantized model has not crossed the safety threshold?
- Which state survives process death, and where is it stored?
- What claim does hardware-backed storage prove—and what does it not prove?

