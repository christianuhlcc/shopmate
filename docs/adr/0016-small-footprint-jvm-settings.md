# ADR-0016: Small-footprint JVM settings for the prod backend

Date: 2026-11-26 · Status: Proposed

## Context

Prod is one t4g.small (2 vCPU, 2 GiB) shared by the shopmate stack and the logbook (ADR-0010). Shopmate has two
users. The t4g free trial ends 2026-12-31. After that, the instance is the biggest item on the bill, and a smaller or
Spot instance is planned (`docs/plans/spot-instance.md`). The backend JVM is the largest process on the box: 330 MB RSS
in prod, 1 h after a deploy.

The backend ran with JVM ergonomics plus `-XX:MaxRAMPercentage=50` and no container limit. On this host that means G1
(2 CPUs and ≥1792 MB count as a "server-class" machine), a 924 MB maximum heap, a 240 MB code cache and full tiered
JIT (C2). G1 rarely returns committed memory, and the C2 compiler and code cache keep growing while code warms up.
Nothing capped the total.

### Measurement (2026-11-26, local)

Method:
- **Image:** the prod image from ECR (`58205f8`, Spring Boot 4.2 on Temurin 21 JRE), with Postgres 16, run in Colima.
- **Machine shape:** the backend container was limited to 2 CPUs. The baseline was also limited to 1846 MB, so the
  ergonomics matched prod exactly (G1, `MaxHeapSize=968884224`, checked with `-XX:+PrintFlagsFinal`).
- **Load:** 5 min per config, 4 client threads. 40% get list, 50% patch item, 10% add+delete item, on a 60-item list,
  with 2 SSE streams open. About 80 req/s, far above real use.
- **Runs:** one after the other, each in a fresh backend container on the same database, with `-Xlog:gc` in all runs.

| | A: before | **B: chosen** | C: B with full JIT (C2) |
|---|---|---|---|
| Flags | `MaxRAMPercentage=50` (G1, 924 MB heap max) | Serial GC, `-Xmx256m`, `-Xss512k`, `TieredStopAtLevel=1`, `ReservedCodeCacheSize=64m` | Serial GC, `-Xmx256m`, `-Xss512k` |
| Container limit / DB pool | none / 10 | 450 MB / 4 | 450 MB / 4 |
| Startup ("Started … in") | 3.6 s | 3.1 s | 5.3 s |
| RSS idle, 30 s after start | 320 MB | **276 MB** | 299 MB |
| RSS after 5 min load | 537 MB, still rising ~30 MB/min | **362 MB**, flattening | 414 MB |
| cgroup peak | 525 MB | **356 MB** | 404 MB |
| Heap after GC (median / max) | 47 / 77 MB | 62 / 105 MB | 64 / 98 MB |
| Committed heap | 261 MB | 132–142 MB | 125–133 MB |
| Latency p50 / p95 / p99 | 8 / 24 / 37 ms | 14 / 75 / 131 ms | 8 / 35 / 88 ms |
| Postgres connections | 11 | 5 | 5 |

The baseline's heap stayed flat at 261 MB committed while RSS kept climbing, so most of its growth was outside the
heap: JIT compiler memory, code cache and metaspace. All three runs had one 6–10 s latency outlier. Clients saw
connection errors without an HTTP status: 1 in A, 7 in B, 0 in C, against about 22–24k requests per run, with no
error in the server log. They weren't classified: the load script only started recording the exception text after
run B. C has the same settings as B except the JIT and had none, so C1-only may play a part under this load. At real
traffic (two users) it doesn't matter.

## Decision

In `docker-compose.prod.yml`, the backend gets:

- **`mem_limit: 450m` + `memswap_limit: 450m`**: a hard ceiling for the container, RAM and swap together. Without
  `memswap_limit`, Docker would allow another 450 MB of swap on top, and the host has a 1 GB swapfile. The JVM also
  sizes itself from this limit.
- **`-XX:+UseSerialGC`**: no G1 overhead. Young-GC pauses were 1–10 ms under the test load.
- **`-Xmx256m`**: the live set is 50–105 MB even at about 40× real load, so 256 MB leaves 2.5× headroom.
- **`-Xss512k`**: about 45 threads, so smaller stacks are a small, safe saving.
- **`-XX:TieredStopAtLevel=1`** with **`-XX:ReservedCodeCacheSize=64m`**: C1 only. It saves about 50 MB compared with
  run C. The cost is latency that only shows under heavy load (p50 8 → 14 ms at 80 req/s). Two users never come
  close to that.
- **`-Xlog:gc:stdout`**: GC lines in `docker logs`, so the heap size can be checked against real use. It's about one
  line per GC, which is rare at real traffic.
- **`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4`**: 5 Postgres backends instead of 11. Each idle Postgres connection
  is a separate process with its own memory.

## Consequences

- Under stress the backend uses about 175 MB less than before (362 vs 537 MB), and its total is bounded by the
  container limit rather than by the host. That makes a 1 GiB t4g.micro feasible. A t4g.small (on-demand or Spot)
  gets more than 1 GB of headroom for the logbook sync.
- If the heap ever runs out, the symptom is `OutOfMemoryError` in the backend log. If the process goes over 450 MB,
  the symptom is an OOM kill and a restart (`restart: unless-stopped`). Either way, raise `-Xmx` or `mem_limit`. Check
  the GC log first: `docker logs shopmate-backend-1 | grep 'Pause'`. The number after `->` is the live heap.
- C1-only code is slower at peak. If latency ever matters more than memory, drop `TieredStopAtLevel` and
  `ReservedCodeCacheSize` (run C: about 50 MB more, p50 back to the baseline).
- A pool of 4 connections serialises DB work beyond 4 concurrent requests. That wasn't a problem at 4 client threads.
- Not measured: the Picnic export (external API), and long-term behaviour over days. The prod GC log covers both after
  the deploy.
