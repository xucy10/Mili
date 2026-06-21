package fun.bm.mili.scheduler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注方法是线程安全的，可以从任意线程调用 / Marks method as thread-safe.
 *
 * <p>标注此注解的方法内部已处理线程同步，可以安全地从任何线程调用 /
 * Methods marked with this annotation have internal thread synchronization
 * and can be safely called from any thread.
 *
 * <p>典型场景 / Typical scenarios:
 * <ul>
 *   <li>只读方法 / Read-only methods</li>
 *   <li>使用 ConcurrentMap 等线程安全集合 / Uses thread-safe collections</li>
 *   <li>使用 volatile/AtomicXxx 的无锁实现 / Lock-free implementations</li>
 * </ul>
 *
 * @author Mili Team
 * @since 1.21.11
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ThreadSafe {
    /**
     * 可选：描述线程安全机制 / Optional: describe thread-safety mechanism.
     */
    String value() default "Thread-safe implementation";
}
