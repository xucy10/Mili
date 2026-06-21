package fun.bm.mili.scheduler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注方法必须在区域线程中调用 / Marks method must be called from region thread.
 *
 * <p>Folia 使用区域化多线程，每个区域有独立的 tick 线程。
 * 标注此注解的方法只能从对应区域的 tick 线程中调用，否则会触发线程安全检查异常 /
 * Folia uses region-based multithreading. Methods marked with this annotation
 * must only be called from the corresponding region's tick thread.
 *
 * <p>示例 / Example:
 * <pre>{@code
 * @RegionThread
 * public void modifyEntity(Entity entity) {
 *     // 只能从实体所在区域的 tick 线程调用
 *     entity.setHealth(20.0);
 * }
 * }</pre>
 *
 * @author Mili Team
 * @since 1.21.11
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RegionThread {
    /**
     * 可选：描述线程约束 / Optional: describe thread constraint.
     */
    String value() default "Must be called from region tick thread";
}
