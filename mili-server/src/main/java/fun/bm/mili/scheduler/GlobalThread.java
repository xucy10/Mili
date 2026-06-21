package fun.bm.mili.scheduler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注方法必须在全局线程中调用 / Marks method must be called from global thread.
 *
 * <p>全局线程负责协调所有区域，处理跨区域操作。
 * 标注此注解的方法只能从全局 tick 线程中调用 /
 * Global thread coordinates all regions and handles cross-region operations.
 * Methods marked with this annotation must only be called from global tick thread.
 *
 * <p>示例 / Example:
 * <pre>{@code
 * @GlobalThread
 * public void broadcastToAllRegions(String message) {
 *     // 只能从全局 tick 线程调用
 *     for (ServerLevel level : server.getAllLevels()) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @author Mili Team
 * @since 1.21.11
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GlobalThread {
    /**
     * 可选：描述线程约束 / Optional: describe thread constraint.
     */
    String value() default "Must be called from global tick thread";
}
