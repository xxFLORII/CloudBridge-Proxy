package de.pocketcloud.cloudbridge.api;

import de.pocketcloud.cloudbridge.api.player.CloudPlayer;
import de.pocketcloud.cloudbridge.api.registry.Registry;
import de.pocketcloud.cloudbridge.api.server.CloudServer;
import de.pocketcloud.cloudbridge.api.server.status.ServerStatus;
import de.pocketcloud.cloudbridge.api.template.Template;
import de.pocketcloud.cloudbridge.network.Network;
import de.pocketcloud.cloudbridge.network.packet.RequestPacket;
import de.pocketcloud.cloudbridge.network.packet.impl.normal.CloudServerSavePacket;
import de.pocketcloud.cloudbridge.network.packet.impl.normal.CloudServerStatusChangePacket;
import de.pocketcloud.cloudbridge.network.packet.impl.request.CloudServerStartRequestPacket;
import de.pocketcloud.cloudbridge.network.packet.impl.request.CloudServerStopRequestPacket;
import de.pocketcloud.cloudbridge.network.packet.impl.request.LoginRequestPacket;
import de.pocketcloud.cloudbridge.network.packet.impl.response.LoginResponsePacket;
import de.pocketcloud.cloudbridge.network.packet.impl.types.VerifyStatus;
import de.pocketcloud.cloudbridge.network.request.RequestManager;
import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.logger.MainLogger;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.utils.config.YamlConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CloudAPI {

    private static CloudAPI instance;
    private VerifyStatus verified = VerifyStatus.NOT_VERIFIED;

    public CloudAPI() {
        instance = this;
    }

    public void processLogin() {
        if (verified == VerifyStatus.VERIFIED) return;
        RequestManager.getInstance().sendRequest(new LoginRequestPacket(getServerName(), (int) ProcessHandle.current().pid())).then(responsePacket -> {
            LoginResponsePacket loginResponsePacket = (LoginResponsePacket) responsePacket;
            if (loginResponsePacket.getVerifyStatus() == VerifyStatus.VERIFIED) {
                MainLogger.getLogger().info("§rThis cloud server was §averified §rby the cloud!");
                verified = VerifyStatus.VERIFIED;
            } else {
                MainLogger.getLogger().error("§4This cloud server wasn't §averified by the cloud! Shutdown...");
                ProxyServer.getInstance().shutdown();
            }
        }).failure(packet -> {
            MainLogger.getLogger().error("§4The cloud didn't respond on the verify request! Shutdown...");
            ProxyServer.getInstance().shutdown();
        });
    }

    public void changeStatus(ServerStatus serverStatus) {
        Network.getInstance().sendPacket(new CloudServerStatusChangePacket(serverStatus));
    }

    public RequestPacket startServer(Template template) {
        return startServer(template, 1);
    }

    public RequestPacket startServer(String template) {
        return startServer(template, 1);
    }

    public RequestPacket startServer(Template template, int count) {
        return startServer(template.getName(), count);
    }

    public RequestPacket startServer(String template, int count) {
        return RequestManager.getInstance().sendRequest(new CloudServerStartRequestPacket(template, count));
    }

    public RequestPacket stopServer(CloudServer server) {
        return stopServer(server.getName());
    }

    public RequestPacket stopServer(String server) {
        return RequestManager.getInstance().sendRequest(new CloudServerStopRequestPacket(server));
    }

    public RequestPacket stopTemplate(Template template) {
        return stopTemplate(template.getName());
    }

    public RequestPacket stopTemplate(String template) {
        return RequestManager.getInstance().sendRequest(new CloudServerStopRequestPacket(template));
    }

    public void saveCurrentServer() {
        Network.getInstance().sendPacket(new CloudServerSavePacket());
    }

    public boolean transferPlayer(ProxiedPlayer player, CloudServer server) {
        CloudPlayer cloudPlayer = getPlayerByName(player.getName());
        if (server.getTemplate().getTemplateType().equals("SERVER")) {
            ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(server.getName());
            if (serverInfo != null) {
                player.connect(serverInfo);
                return true;
            }
        }
        return false;
    }

    public CloudServer getFreeLobby(String... exclude) {
        List<CloudServer> lobbyServices = getServers().stream().filter(service -> service.getTemplate().isLobby() && service.getServerStatus() == ServerStatus.ONLINE).collect(Collectors.toList());
        if (lobbyServices.isEmpty()) return null;
        for (String excludingName : exclude) lobbyServices.removeIf(server -> server.getName().equals(excludingName));
        if (lobbyServices.isEmpty()) return null;
        Collections.shuffle(lobbyServices);
        return lobbyServices.get(0);
    }

    public List<CloudServer> getServersOfTemplate(Template template) {
        return getServers().stream().filter(server -> server.getTemplate().getName().equals(template.getName())).collect(Collectors.toList());
    }

    public List<CloudPlayer> getPlayersOfTemplate(Template template) {
        return getPlayers().stream().filter(player -> {
            if (template.getTemplateType().equals("PROXY")) return (player.getCurrentProxy() != null && player.getCurrentProxy().getTemplate().getName().equals(template.getName()));
            else return (player.getCurrentServer() != null && player.getCurrentServer().getTemplate().getName().equals(template.getName()));
        }).collect(Collectors.toList());
    }

    public CloudServer getServerByName(String name) {
        return getServers().stream().filter(server -> server.getName().equals(name)).findFirst().orElse(null);
    }

    public Template getTemplateByName(String name) {
        return getTemplates().stream().filter(template -> template.getName().equals(name)).findFirst().orElse(null);
    }

    public CloudPlayer getPlayerByName(String name) {
        return getPlayers().stream().filter(player -> player.getName().equals(name)).findFirst().orElse(null);
    }

    public CloudPlayer getPlayerByUniqueId(String uniqueId) {
        return getPlayers().stream().filter(player -> player.getUniqueId().equals(uniqueId)).findFirst().orElse(null);
    }

    public CloudPlayer getPlayerByXboxUserId(String xboxUserId) {
        return getPlayers().stream().filter(player -> player.getXboxUserId().equals(xboxUserId)).findFirst().orElse(null);
    }

    public String getServerName() {
        return new YamlConfig(ProxyServer.getInstance().getDataPath().toString() + "/config.yml").getString("server-name");
    }

    public String getTemplateName() {
        return new YamlConfig(ProxyServer.getInstance().getDataPath().toString() + "/config.yml").getString("template");
    }

    public int getCloudPort() {
        return Integer.parseInt(new YamlConfig(ProxyServer.getInstance().getDataPath().toString() + "/config.yml").getString("cloud-port"));
    }

    public String getCloudPath() {
        return new YamlConfig(ProxyServer.getInstance().getDataPath().toString() + "/config.yml").getString("cloud-path");
    }

    public CloudServer getCurrentServer() {
        return getServerByName(getServerName());
    }

    public Template getCurrentTemplate() {
        return getTemplateByName(getTemplateName());
    }

    public ArrayList<CloudServer> getServers() {
        return Registry.getServers();
    }

    public ArrayList<Template> getTemplates() {
        return Registry.getTemplates();
    }

    public ArrayList<CloudPlayer> getPlayers() {
        return Registry.getPlayers();
    }

    public boolean isVerified() {
        return verified == VerifyStatus.VERIFIED;
    }

    public static CloudAPI getInstance() {
        return instance;
    }
}
