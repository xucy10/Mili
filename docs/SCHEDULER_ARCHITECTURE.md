# Mili 调度架构

> 分支：`ver/1.21.11`　|　对应施工图：`Mili-1.21.11-Fix-Plan.md`（下称 fix.md）
> 状态：调度核心已实现并通过自检；NMS 侧接线待构建期确认（见 [§7](#7-验证状态)）

---

## 1. 一句话模型

> **异步计算可以并行，但 Minecraft 状态修改必须始终回到合法的 Region Ownership 上。**

实现上这句话落成**两条互不重叠的执行通道**：

```
区域通道  drainOwned(region, budget)          —— 必须由该 region 的属主线程调用
             唯一允许触碰 Minecraft 状态的通道

计算通道  computeLane().compute(pureSupplier) —— Mili worker 线程
             只允许处理快照 / 不可变输入，纯计算
             结果通过 submit() 回到区域通道做 Apply
```

旧代码里存在第三条通道，而且是**意外存在**的：*"队列排不上，就让提交线程自己跑"*。
这条通道在新设计里**不存在任何一种形式**。

---

## 2. 为什么旧实现是错的

### 2.1 `RegionBalancer.submitAndWait()` 主路径同步执行

```java
// 旧实现，enabled=true 时的主路径
RegionLoadMonitor.beforeTick(scheduleRef);
final long begin = System.nanoTime();
try {
    work.run(); // execute on the calling thread to preserve region context
}
```

方法名承诺"提交并等待"，实际**既不提交也不等待**——在调用线程上原地执行。
注释说这样做是为了"保留 region context"，但当一个属于 region A 的线程带着
region B 的 `scheduleRef` 调用它时，它执行的是 **B 的 tick**，恰恰是在破坏
region context。

这正是 fix.md §2.3 明令禁止的那条路径：

```
Region A Thread → submit Region B task → queue/worker unavailable → Region A executes Region B task
```

### 2.2 队列满时的同步兜底

fix.md §3 描述的兜底在旧代码里的具体形态不是"队列满"，而是**更严重的版本**：
`submit()` / `submitAndWait()` / `retryTask()` 在 `workerPool == null` 或
`enabled == false` 时都会 `work.run()`。而 `enabled` 默认就是 `false`。

实际上旧代码的 `taskQueue` 是无界 `PriorityBlockingQueue`，结构上不可能满——
所以背压策略**根本不存在**，任务只会无限堆积。

### 2.3 取消只改状态，不停代码

```java
record.cancelRequested = true;
record.state = TaskState.CANCELLED;
```

`TaskRecord` 里没有 `executingThread` 字段，全类从不中断工作线程。于是
"scheduler 认为任务已取消"与"代码已经停止"是两件不同的事。

### 2.4 跨区 target 从未被解析

```java
RegionizedWorldData srcRegion = level.getCurrentWorldData();
RegionizedWorldData tgtRegion = level.getCurrentWorldData();   // 同一个表达式
if (srcRegion == tgtRegion) return;                            // 因此恒为真
```

`neighbor`（唯一能定位目标 region 的输入）被存进事件后**从未被读取**。
该方法永远直接 return，是一个彻底的 no-op。

### 2.5 实体 tick 热路径每次分配

```java
EntityThrottlerReturn retVal = new EntityThrottlerReturn();     // 连 isRemoved() 早退都先分配
entityLimitTickInfoMap.computeIfAbsent(entityLimit, el -> {...}); // 捕获型 lambda，每次调用都新建
```

每实体每 tick 两次分配，只为携带三个布尔值。

### 2.6 组件之间的 lifecycle 全是断的

| 组件 | 清理入口 | 调用者 |
|---|---|---|
| `RegionLoadMonitor` | `remove(Object)` | **无** |
| `SmartRegionManager` | `unregisterRegion(Integer)` | **无** |
| `CrossRegionHelper` | `onRegionUnload(...)` | **无** |
| `MiliOptimizations` | `init(Plugin)` | **无** |

`MiliOptimizations.init()` 是全部子系统的唯一生命周期入口，而它**零调用点**。
也就是说：调度器、区块系统、村民优化等一大票组件是编译进去、可被 `/mili perf`
统计、但**从未真正生效**的死代码。

---

## 3. 新架构

### 3.1 分层

```
fun.bm.mili.scheduler            纯 Java 核心，零 NMS 依赖，可用 javac 独立编译
   ├── 抽象        MiliScheduler / RegionRuntime
   ├── 实现        MiliSchedulerImpl / MiliRegionRuntime
   ├── 任务        TaskHandle / TaskController / CancellationToken / TaskState
   ├── 背压        SubmissionResult / RegionWorkQueue
   ├── 预算        RegionBudget / SchedulerDebt / BudgetController / EntityScheduler
   ├── 身份        RegionIdRegistry / RegionOwnership / RegionResolver
   ├── 跨区        CrossRegionTransaction / TransactionState
   ├── 生命周期     RegionLifecycle
   └── 桥接        FoliaSchedulerAdapter / WorkerRuntime / SchedulerLog

fun.bm.mili.utils                适配层，允许触碰 NMS
   ├── RegionBalancer           → 薄外观，转发到 scheduler
   ├── CrossRegionHelper        → 按 neighbor 坐标解析 target region
   ├── RegionLoadMonitor        → 按稳定 regionId 统计
   ├── RegionTaskIdRegistry     → 单 Map + 正确的 TTL
   └── AsyncPathfinder          → Capture / Compute / Apply 三段式
```

**核心零 NMS 依赖是刻意的**，它带来两个好处：

1. 调度模型可以脱离整个服务端单独编译、单独测试（`scripts/scheduler-verify`）；
2. 任何误把 Minecraft 类型引入核心的改动，都会在 `javac -Xlint:all` 阶段以
   编译错误的形式立刻暴露。

### 3.2 两条通道的入口

| 时机 | 入口 | 说明 |
|---|---|---|
| region tick | `FoliaSchedulerAdapter.onRegionTick(region)` | 计算预算 → `drainOwned` → 记录 debt |
| region unload | `FoliaSchedulerAdapter.onRegionUnload(region)` | 执行 fix.md §13 的九步销毁 |
| 服务器 tick | `FoliaSchedulerAdapter.onServerTick()` | 到期扫描 + 预算重分配 |
| 外部线程 | `executeOnOwningThread(region, work)` | ownership 优先，否则排队等待 |
| 纯计算 | `computeLane().compute(supplier)` | 只准读快照 |

---

## 4. fix.md 逐条对照

| fix.md | 内容 | 状态 | 落点 |
|---|---|---|---|
| §2.1 | `submitAndWait` ownership 优先 | ✅ | `RegionOwnership` + `MiliSchedulerImpl.submitAndWait` |
| §2.3 | 禁止 A 线程执行 B 的任务 | ✅ | 由 `scripts/scheduler-verify` 两条用例锁定 |
| §3 | 队列满四级背压，不 fallback | ✅ | `SubmissionResult` + `RegionWorkQueue.offer` |
| §4 | Task 保存执行线程并中断 | ✅ | `TaskHandle.executingThread` / `requestCancel` |
| §5 | `CancellationToken` | ✅ | `CancellationToken` / `TaskCancelledException` |
| §6 | 异步 worker 与状态修改解耦 | ✅ | `WorkerRuntime`（计算通道）+ `drainOwned`（区域通道） |
| §7 | `AsyncPathfinder` 快照化 | ✅ | `AsyncPathfinder.PathSnapshot` / `PathRequest` / 纯 A* |
| §8 | 稳定 `long regionId` | ✅ | `RegionIdRegistry`（弱键，防泄漏） |
| §9 | Task Registry 软上限与生命周期 | ✅ | `RegionTaskIdRegistry`（单 Map、按 `updatedAt` 淘汰、新增 `unregisterByScheduleRef`） |
| §10 | 实体热路径零分配 | ✅ | `EntityTickDecision`（int）+ `KaiijuEntityThrottler` |
| §11 | Throttler → Entity Scheduler | 🟡 | `EntityScheduler` / `EntityPriority` 已就绪，**待 region tick 钩子传入 regionId** |
| §12 | 跨区 target 解析 | ✅ | `CrossRegionHelper.regionAt` + `RegionResolver` |
| §13 | 统一 Region destroy 九步 | ✅ | `RegionLifecycle.destroy` |
| §14 | Region slots 注销 | ✅ | `EntityScheduler.release` 已接入销毁序列 |
| §15 | Metrics / Context 全生命周期清理 | ✅ | `FoliaSchedulerAdapter.installLifecycleHooks` |
| §16 | 不做 sleep→mark success 假迁移 | ❌ | `SmartRegionManager` **未改**，见 §6 |
| §17 | Balancer 职责收敛 | ✅ | `RegionBalancer` 已降为外观，自建线程池/队列/记录表全部删除 |
| §18 | `MiliScheduler` / `RegionRuntime` 接口 | ✅ | 同名接口 |
| §19 | DAG / Scheduler / Worker / TaskController 四层 | ✅ | 无独立 DAG（仓库本就没有 DAGScheduler），由 `TaskController` 承担任务层 |
| §20 | 控制 Tick Budget 而非降频 | ✅ | `RegionBudget` + `BudgetController` |
| §21 | Scheduler Debt | ✅ | `SchedulerDebt` + `MiliRegionRuntime.priorityScore` |

---

## 5. 关键设计决策

### 5.1 ownership 探测：证明不了就当 `false`

三级降级：注入式 `Resolver` → Mili 自己记录的属主线程（按 regionId 存，不持强引用）
→ 反射探测 `isOwnedByCurrentRegion()` / `isOwnedByCurrentThread()`。

**探测不出来返回 `false`**。这是刻意的安全方向：`false` 只是让任务多绕一次队列
（正确但略慢），而乐观的 `true` 会让外部线程改写别的 region 的状态——这是唯一会
**损坏世界**而不是仅仅损失延迟的失败模式。

### 5.2 但"探测不出来" ≠ "确定不是"

`RegionOwnership.canDetermineOwnership()` 区分这两种情况。这个区分是必要的，
因为 `RegionBalancer.submitAndWait` 的调用契约是"这是我的 region 的 tick"：
如果因为钩子没装而把 tick 塞进一个**只有本线程能抽干**的队列，每个 region tick
都会卡到超时。所以那条路径在"ownership 不可知"时选择 inline 执行并打一次警告，
而不是静默降级——把选择摆到明面上，而不是藏在默认值里。

### 5.3 销毁顺序 `RegionLifecycle.destroy`

```
1. deactivate          停止接受新任务
2. close queue         队列关闭
3. drain pending       取消存量并释放等待者
3b. cancel transactions 切断所有指向本 region 的跨区事务
4. cancel async        取消在飞任务，有界等待线程真正离开
5. unregister          从 scheduler 查表移除
6. remove metrics
7. remove load monitor 及其他按 region 建表的组件
8. close region context
9. release region      注销 ID 并打墓碑
```

原则：**先停止进入，再清理存量，最后删除生命周期数据**。相邻两步互换都会产生真实 bug。

第 9 步同时**打墓碑**（`RegionIdRegistry.markDestroyed`）：如果一个提交在销毁之后
才到达，`idOf` 会重新分配一个全新 id，于是 scheduler 会围绕一个已死的 region 建出
新 runtime——"region 复活"。这个 bug 是自检抓出来的（见 §7），已修。

### 5.4 稳定 ID 用弱键

`RegionIdRegistry` 内部是 `Collections.synchronizedMap(new WeakHashMap<>())`。
用弱键的原因是：region 随玩家移动高频创建销毁，而 region 类是我们改不动的
Minecraft 类型，不可能给它加一个 id 字段。强引用注册表会把每个 region 及其
`RegionizedWorldData` 整图钉死。

墓碑表同样用弱键——墓碑的规模应当被回收 region 的那个 GC 自然限制住。

### 5.5 跨区路由不持有 region

`CrossRegionHelper.pendingByRegion` 现在以 `long regionId` 为键，**队列里不包含任何
region 引用**。事件携带的是坐标（`int`）而不是 `BlockPos`/`RegionizedWorldData`。
这不只是省内存：它意味着"事件把死 region 钉住"这个泄漏在类型层面就不可能发生。

---

## 6. 未完成项

### 6.1 `SmartRegionManager` 仍是模拟迁移（fix.md §16）

```java
TimeUnit.MILLISECONDS.sleep(10);
profile.recordMigrationResult(true);   // 无条件成功
return true;
```

`execute()` 恒返回 `true`，所以 `successfulMigrations` 单调增长、`consecutiveFailures`
恒为 0——对外暴露的统计**不反映任何真实迁移**。

**本轮未改**，理由：真正的迁移需要 Folia region ownership 的实际转移
（Prepare → 停止接活 → drain → 转移 ownership → 重绑 scheduler → resume → commit），
这依赖尚未 apply 的补丁才能确认的 Folia 内部 API。在没有构建验证的前提下写入
"看起来像真迁移"的代码，只会把模拟换成更隐蔽的模拟。

**建议**：在下一次能跑通构建时优先处理；在此之前应当把它的统计从对外输出中摘掉，
避免误导。

### 6.2 `EntityScheduler` 未接入调用点（fix.md §11）

核心已就绪（`EntityScheduler` / `EntityPriority` / `EntityTickDecision`），但接线需要
region tick 钩子把 `regionId` 传给 `KaiijuEntityThrottler`。当前实体路径只完成了
§10 的零分配改造。

注意：`EntityScheduler` 的 `REMOVE` 决策**默认关闭**（`setRemovalEnabled(false)`）。
预算压力删除实体是玩法决策而非调度决策，必须显式打开。

### 6.3 引导补丁尚未合入

`MiliOptimizations.init()` 现在会初始化调度器，但**调用它的补丁还没有进入构建**。
原 `todo/0125-Bootstrap-Mili-optimizations.patch` 需要先解决 13 处
`Bukkit.getScheduler()` Folia 违规（`LagRemover` ×3、`TPSTracker`、`VillagerOptimizer`、
`MiliChunkSystem`、`AutoBackupManager` ×2 等）。其中 `LagRemover` 与
`VillagerOptimizer` 会在**全局调度线程**里遍历 `Bukkit.getWorlds()` 并直接改实体，
属于 region 线程安全违规，必须按 per-region 重新设计，不能只是换个调度器。

---

## 7. 验证状态

### 7.1 已完成的验证

```bash
bash scripts/scheduler-verify/run.sh
```

用 JDK 25 对 `fun.bm.mili.scheduler`（29 个源文件）做 `javac -Xlint:all` **零 classpath**
编译，然后跑 73 项行为断言。全部通过。

自检覆盖的是**缺陷本身**，不是覆盖率：

| 用例 | 锁定的缺陷 |
|---|---|
| `checkForeignThreadNeverRunsWorkInline` | fix.md §2.3：外部线程绝不执行不属于它的工作 |
| `checkQueueFullNeverRunsWorkInline` | fix.md §3：队列满不 fallback 到调用线程 |
| `checkCancellationStopsTheBody` | fix.md §4/§5：取消必须真的让代码停下来 |
| `checkDeadlineCancelsInsteadOfRunningLate` | 超时任务被取消，而不是迟到执行 |
| `checkEntityTickDecisionIsAllocationFree` | fix.md §10：100 万次决策分配 **0 字节** |
| `checkRegionDestroyReleasesEverything` | fix.md §13/§14/§15：销毁后所有表都清干净 |
| `checkCrossRegionTargetsAreTrackedSeparately` | fix.md §12：source/target 独立解析 |

自检过程中抓到并修复了一个真实缺陷：销毁后的 region 会被重新注册复活（见 §5.3）。

### 7.2 本轮**没有**验证的部分

用户选择"只做 Java 侧验证"——不初始化 `folia-server` 子模块、不 apply 补丁、
不跑 Gradle 全量构建。因此下列内容**只经过人工审查，未经编译**：

| 文件 | 需要构建期确认的点 |
|---|---|
| `CrossRegionHelper.regionAt` | `level.regioniser.getRegionAtUnsynchronised(cx, cz)` 存在；返回对象的 `getData().regionData` 是 `RegionizedWorldData` |
| `AsyncPathfinder.capture` | `BlockState.getCollisionShape(BlockGetter, BlockPos)` 签名 |
| `RegionBalancer` | `FoliaSchedulerAdapter` 反射探测用的方法名是否命中 |
| `MiliOptimizations` | 新增 import 与调用点 |
| `0043-...patch` | 补丁行数与上下文（已保持新增行数不变，hunk 头未动） |

这些都是**编译期错误**（不存在的方法/字段会直接编译失败），不会变成运行期的静默错误。

---

## 8. 后续顺序

```
Phase A  构建打通
   ├─ git submodule update --init folia-server
   ├─ ./gradlew applyAllPatches
   ├─ 修 §7.2 列出的编译错误
   └─ 修 13 处 Bukkit.getScheduler() Folia 违规

Phase B  接线
   ├─ 引导补丁进 features/
   ├─ 安装 ownership resolver（region tick 钩子）
   ├─ 安装 region tick / region unload / server tick 三个钩子
   └─ EntityScheduler 接入实体路径

Phase C  收尾
   ├─ SmartRegionManager 真迁移（或摘掉其统计）
   └─ docs/WIKI.md 第 5 章重写
```

**关键约束**：Phase B 的钩子安装顺序不能颠倒。ownership resolver 必须在
region tick 钩子之前装好，否则第一次 tick 会落到"ownership 不可知"分支，
按 §5.2 走 inline 并打警告——功能正常但调度没生效。

---

## 9. 回归基线

改动后应保持 `/mili perf` 至少包含以下字段，用于确认调度器真的在跑：

```
state                     RUNNING
regions_tracked           > 0
submitted_accepted        > 0
drained_tasks             > 0
inline_owner_runs         随 region 数量增长
wait_timeouts             应接近 0
submitted_after_destroy   应为 0（非 0 说明有调用点在销毁后提交）
unresolved_targets        应接近 0（非 0 说明 regioniser 解析失败）
```

`submitted_after_destroy` 与 `unresolved_targets` 是本次新增的两个"不变量计数器"：
它们**正常情况下的期望值就是 0**，一旦不为 0 就是在告诉你某处有 bug，而不是在
告诉你系统变慢了。
