package m4z.app.config;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;

public class ProxyManager {

    public static Proxy proxy() {
        if (ConfigManager.getConfig().app().proxy().active()) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ConfigManager.getConfig().app().proxy().host(), ConfigManager.getConfig().app().proxy().port()));
        }
        return Proxy.NO_PROXY;
    }

    public static ProxySelector proxySelector() {
        if (ConfigManager.getConfig().app().proxy().active()) {
            return ProxySelector.of(new InetSocketAddress(ConfigManager.getConfig().app().proxy().host(), ConfigManager.getConfig().app().proxy().port()));
        }
        return ProxySelector.getDefault();
    }
}
