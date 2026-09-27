# Mili 插件 NMS 兼容层（Legacy NMS Compat）

## 解决什么问题

Paper 自 1.20.5 起默认运行 Mojang 映射，大量老插件通过反射访问的
Spigot 时代 NMS 类名/方法名/字段名（如 `net.minecraft.server.ScoreboardServer`、
`Entity#getDataWatcher()`、`ServerPlayer#playerConnection`）在运行时
解析失败，抛出 `ClassNotFoundException` / `NoSuchMethodException` /
`NoSuchFieldException`。

Mili 在 `PaperReflection`（Paper 的插件反射重映射代理）里注入了一个
前置 shim，把这类旧名字翻译成当前等价物后再走原逻辑。

## 组成

| 组件 | 位置 | 作用 |
|---|---|---|
| 映射表实现 | `mili-server/src/main/java/fun/bm/mili/nms/LegacyNMSMappings.java` | 类名/方法名/字段名三层映射 + 外部文件 + 日志 |
| 类名挂接补丁 | `mili-server/paper-patches/features/0024-Mili-Add-legacy-NMS-class-remapping.patch` | 挂接 `mapClassName` |
| 成员名挂接补丁 | `mili-server/paper-patches/features/0025-Mili-Add-legacy-NMS-member-remapping.patch` | 挂接 `mapMethodName` / `mapDeclaredMethodName` / `mapFieldName` / `mapDeclaredFieldName` |

## 安全策略（为什么不会误伤）

成员名重映射仅在**旧名字在目标类层级（含父类与接口）中不存在**时生效。
若类真实声明了被请求的成员，请求按原样放行——活跃成员永远优先于映射表。

方法名查找按类层级回溯：`ServerLevel.getType` 会命中 `Level.getType` 键。

## 服主扩展映射（无需重编服务端）

放置 `config/mili-legacy-nms.properties`（或用系统属性
`-Dmili.nmscompat.mappings=<路径>` 指定），格式：

```properties
# 类名：旧全限定名 = 当前全限定名
class.net.minecraft.server.SomeOldClass=net.minecraft.world.entity.SomeNewClass
# 方法：当前类简单名.旧方法名 = 当前方法名
method.Entity.getDataWatcher=getSynchedEntityData
# 字段：当前类简单名.旧字段名 = 当前字段名
field.ServerPlayer.playerConnection=connection
```

服务端启动时加载；运行期可调用 `LegacyNMSMappings.reload()` 重新加载。

## 诊断

- 默认：每次成功重映射记 `FINE` 日志（logger 名 `Mili/NMSCompat`）。
- `-Dmili.nmscompat.verbose=true`：升为 `INFO`，可据此定位是哪个插件的
  哪次反射调用被挽救。

## 已知边界

- 仅覆盖**反射**查找（`reflection-rewriter` 改写过的调用）；插件字节码里
  硬编码的 NMS 类符号引用无法在此层修复，需插件作者更新。
- 方法重映射只匹配名字（参数类型仅用于"成员是否真实存在"的判断，
  不参与重载选择）。
