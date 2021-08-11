package org.apache.dubbo.demo.consumer;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

public class YamlMain {

    private static String zookeeperHost = System.getProperty("zookeeper.address", "127.0.0.1");
    private static CuratorFramework client;

    public static void main(String[] args) {
        initClient();
        generateAppLevelOverride(200);
    }

    public static void initClient() {
        client = CuratorFrameworkFactory.newClient(zookeeperHost + ":2181", 60 * 1000, 60 * 1000,
                new ExponentialBackoffRetry(1000, 3));
        client.start();
    }

    public static void generateAppLevelOverride(int weight) {
        String str = "" +
                "# Execute on demo-provider.\n" +
                "# This will take effect on all services in demo-provider.\n" +
                "---\n"
                + "configVersion: v2.7\n" +
                "scope: application\n" +
                "key: demo-provider\n" +
                "enabled: true\n" +
                "configs:\n" +
                "- addresses: [\"0.0.0.0\"]\n" +
                "  side: provider\n" +
                "  parameters:\n" +
                "    weight: " + weight;

        System.out.println(str);

        try {
            String path = "/dubbo/config/dubbo/demo-provider.configurators";
            if (client.checkExists().forPath(path) == null) {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path);
            }
            setData(path, str);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setData(String path, String data) throws Exception {
        client.setData().forPath(path, data.getBytes());
    }
}