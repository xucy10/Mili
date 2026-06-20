package net.minecraft.util.monitoring.jmx;

import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class MinecraftServerStatistics implements DynamicMBean {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final MBeanInfo mBeanInfo;
    private final Map<String, MinecraftServerStatistics.AttributeDescription> attributeDescriptionByName = Stream.of(
            new MinecraftServerStatistics.AttributeDescription("tickTimes", this::getTickTimes, "Historical tick times (ms)", long[].class),
            new MinecraftServerStatistics.AttributeDescription("averageTickTime", this::getAverageTickTime, "Current average tick time (ms)", long.class)
        )
        .collect(Collectors.toMap(attributeDescription -> attributeDescription.name, Function.identity()));

    private MinecraftServerStatistics(MinecraftServer server) {
        this.server = server;
        MBeanAttributeInfo[] mBeanAttributeInfos = this.attributeDescriptionByName
            .values()
            .stream()
            .map(MinecraftServerStatistics.AttributeDescription::asMBeanAttributeInfo)
            .toArray(MBeanAttributeInfo[]::new);
        this.mBeanInfo = new MBeanInfo(
            MinecraftServerStatistics.class.getSimpleName(), "metrics for dedicated server", mBeanAttributeInfos, null, null, new MBeanNotificationInfo[0]
        );
    }

    public static void registerJmxMonitoring(MinecraftServer server) {
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(new MinecraftServerStatistics(server), new ObjectName("net.minecraft.server:type=Server"));
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException var2) {
            LOGGER.warn("Failed to initialise server as JMX bean", (Throwable)var2);
        }
    }

    private float getAverageTickTime() {
        return this.server.getCurrentSmoothedTickTime();
    }

    private long[] getTickTimes() {
        return this.server.getTickTimesNanos();
    }

    @Override
    public @Nullable Object getAttribute(String name) {
        MinecraftServerStatistics.AttributeDescription attributeDescription = this.attributeDescriptionByName.get(name);
        return attributeDescription == null ? null : attributeDescription.getter.get();
    }

    @Override
    public void setAttribute(Attribute attribute) {
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        List<Attribute> list = Arrays.stream(attributes)
            .map(this.attributeDescriptionByName::get)
            .filter(Objects::nonNull)
            .map(attributeDescription -> new Attribute(attributeDescription.name, attributeDescription.getter.get()))
            .collect(Collectors.toList());
        return new AttributeList(list);
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        return new AttributeList();
    }

    @Override
    public @Nullable Object invoke(String actionName, Object[] params, String[] signature) {
        return null;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return this.mBeanInfo;
    }

    static final class AttributeDescription {
        final String name;
        final Supplier<Object> getter;
        private final String description;
        private final Class<?> type;

        AttributeDescription(String name, Supplier<Object> getter, String description, Class<?> type) {
            this.name = name;
            this.getter = getter;
            this.description = description;
            this.type = type;
        }

        private MBeanAttributeInfo asMBeanAttributeInfo() {
            return new MBeanAttributeInfo(this.name, this.type.getSimpleName(), this.description, true, false, false);
        }
    }
}
