package fun.bm.mili.scheduler;

/**
 * Mili 线程资源分层。
 *
 * <p>分层的依据是<b>任务的阻塞特性与线程身份要求</b>，而不是调用方属于哪个功能模块。
 * 同一个进程端到端的粗粒度分层有三个目的：避免 CPU 密集任务被阻塞 IO 拖死、
 * 避免后台巡检抢占 tick 附近的 CPU、以及给全局线程预算一个分摊口径。</p>
 */
public enum Tier {

    /**
     * Folia region tick 线程。
     *
     * <p><b>硬约束</b>：执行线程必须是 {@code TickRegionScheduler} 通过其 threadFactory
     * 创建的 TickThreadRunner —— tick 代码深处会校验线程身份，身份证明不吻合会直接抛错，
     * 更糟的情况是静默产生跨区域数据竞争。</p>
     *
     * <p>因此本层<b>不可由 {@link MiliScheduler} 创建，也不可共享</b>。它只做登记与观测，
     * 仲裁权另属 tick 仲裁层。任何试图在通用池里跑 region tick 的方案都是错的。</p>
     */
    TICK(false),

    /**
     * 纯 CPU 计算：寻路、批量数学、加密压缩等。
     *
     * <p>线程数与核心数挂钩，队列必须有界。任务一旦变成阻塞式应立即降级到
     * {@link #BLOCKING_IO}，否则会拖空整个池。</p>
     */
    CPU(true),

    /**
     * 阻塞式 IO：磁盘读写、区域文件刷盘、远端 HTTP。
     *
     * <p>允许比 CPU 层更多的线程（阻塞时不占 CPU），但仍需上界，因为每个线程
     * 都要吃虚拟内存栈。</p>
     */
    BLOCKING_IO(true),

    /**
     * 低频后台巡检与定时任务。
     *
     * <p>典型负载是"每 N 秒看一眼"的叹息式任务，共享一个定时池即可，
     * 无需每个组件各自 {@code newSingleThreadScheduledExecutor}。</p>
     */
    BACKGROUND(true),

    /**
     * 短生命周期、数量大、天然阻塞的批量任务（如 DAG 波次内的独立分支）。
     *
     * <p>底层是虚拟线程，但必须经 {@link MiliScheduler#virtualExecutor(String, int)}
     * 的并发闸门，不能直接 {@code Thread.ofVirtual().start()} —— 后者等价于无界线程，
     * 正是本次治理要消灭的形态。</p>
     */
    VIRTUAL(true);

    private final boolean allocatable;

    Tier(final boolean allocatable) {
        this.allocatable = allocatable;
    }

    /**
     * 该层是否允许 {@link MiliScheduler} 代为创建线程。
     *
     * @return true 表示可分配；false 表示只能登记
     */
    public boolean isAllocatable() {
        return this.allocatable;
    }
}
