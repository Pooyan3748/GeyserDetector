package com.gxlydlyf.geyserdetector.paper;

import com.gxlydlyf.geyserdetector.api.BedrockDetector;
import com.gxlydlyf.geyserdetector.api.GeyserDetectorApi;
import com.gxlydlyf.geyserdetector.api.protocol.BedrockCodec;
import com.gxlydlyf.geyserdetector.api.protocol.BedrockMessage;
import com.gxlydlyf.geyserdetector.paper.command.GeyserDetectorCommand;
import com.gxlydlyf.geyserdetector.paper.placeholder.GeyserDetectorExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Receives Bedrock detection from the proxy and publishes it to other plugins.
 */
public final class GeyserDetectorPaper extends JavaPlugin {

    public static final String CHANNEL = "geyserdetector:main";

    private BedrockRegistry registry;

    @Override
    public void onEnable() {
        registry = new BedrockRegistry();
        registry.setFormTransport(new BedrockRegistry.FormTransport() {
            @Override
            public boolean sendForm(java.util.UUID playerId, String formJson) {
                if (!registry.available()) {
                    return false;
                }
                sendToProxy(new BedrockMessage.SendForm(playerId, formJson));
                return true;
            }

            @Override
            public boolean closeForm(java.util.UUID playerId) {
                if (!registry.available()) {
                    return false;
                }
                sendToProxy(new BedrockMessage.CloseForm(playerId));
                return true;
            }
        });

        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, new ChannelListener(this, registry));
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);

        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(registry), this);

        // Published two ways on purpose: the service is the late-binding path for
        // plugins that never compile against us, the static facade is the ergonomic one.
        getServer().getServicesManager().register(BedrockDetector.class, registry, this, ServicePriority.Normal);
        GeyserDetectorApi.install(() -> registry);

        if (getCommand("geyserdetector") != null) {
            GeyserDetectorCommand command = new GeyserDetectorCommand(this, registry);
            getCommand("geyserdetector").setExecutor(command);
            getCommand("geyserdetector").setTabCompleter(command);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new GeyserDetectorExpansion(registry).register();
            getLogger().info("Registered PlaceholderAPI expansion.");
        }

        requestSync();

        getLogger().info("GeyserDetector enabled. Waiting for the proxy to report Bedrock players.");
    }

    @Override
    public void onDisable() {
        GeyserDetectorApi.uninstall();
        getServer().getServicesManager().unregisterAll(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        // Deliberately no Bukkit.getScheduler().cancelTasks(this): that call does not
        // exist on Folia. Nothing here schedules repeating work anyway.
        if (registry != null) {
            registry.clear();
        }
    }

    /**
     * Asks the proxy for the full picture.
     *
     * <p>Delayed rather than sent inline: a plugin channel is only usable once a
     * player connection exists, and on a cold start the proxy may still be
     * finishing its own startup. Retried a few times because there is no way to
     * know when the proxy became ready.
     */
    private void requestSync() {
        for (int attempt = 0; attempt < 5; attempt++) {
            long delayTicks = 20L * (attempt + 1);
            FoliaScheduler.asyncLater(this, () -> {
                if (registry.available()) {
                    return;
                }
                sendToProxy(new BedrockMessage.SyncRequest());
            }, delayTicks);
        }
    }

    /**
     * Sends a frame to the proxy through any connected player.
     *
     * <p>One carrier is enough: the proxy answers on that player's connection.
     * The send is dispatched onto the carrier's own region thread, because
     * touching a player off-thread is not legal on Folia.
     */
    void sendToProxy(BedrockMessage message) {
        byte[] payload = BedrockCodec.encode(message);
        Bukkit.getOnlinePlayers().stream().findFirst().ifPresent(carrier ->
                FoliaScheduler.onPlayer(this, carrier, () -> {
                    if (carrier.isOnline()) {
                        carrier.sendPluginMessage(this, CHANNEL, payload);
                    }
                }));
    }
}
