package net.minecraft.network;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketUtils;

public interface PacketListener {
    PacketFlow flow();

    ConnectionProtocol protocol();

    void onDisconnect(DisconnectionDetails details);

    default void onPacketError(Packet packet, Exception exception) throws ReportedException {
        throw PacketUtils.makeReportedException(exception, packet, this);
    }

    default DisconnectionDetails createDisconnectionInfo(Component reason, Throwable error) {
        return new DisconnectionDetails(reason);
    }

    boolean isAcceptingMessages();

    default boolean shouldHandleMessage(Packet<?> packet) {
        return this.isAcceptingMessages();
    }

    default void fillCrashReport(CrashReport crashReport) {
        CrashReportCategory crashReportCategory = crashReport.addCategory("Connection");
        crashReportCategory.setDetail("Protocol", () -> this.protocol().id());
        crashReportCategory.setDetail("Flow", () -> this.flow().toString());
        this.fillListenerSpecificCrashDetails(crashReport, crashReportCategory);
    }

    default void fillListenerSpecificCrashDetails(CrashReport crashReport, CrashReportCategory category) {
    }
}
