package org.example.dns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows DNS设置管理器
 */
public class WindowsDnsManager {

    /**
     * 获取所有网络接口 - 改进版本
     */
    public List<NetworkInterface> getNetworkInterfaces() {
        List<NetworkInterface> interfaces = new ArrayList<>();

        // 尝试多种方法获取网络接口

        // 方法1: 使用ipconfig命令
        if (interfaces.isEmpty()) {
            interfaces = getNetworkInterfacesFromIpconfig();
        }

        // 方法2: ���用wmic命令
        if (interfaces.isEmpty()) {
            interfaces = getNetworkInterfacesFromWmic();
        }

        // 方法3: 尝试使用netsh（如果可用）
        if (interfaces.isEmpty()) {
            interfaces = getNetworkInterfacesFromNetsh();
        }

        // 方法4: 最后的备用选项
        if (interfaces.isEmpty()) {
            interfaces = getNetworkInterfacesAlternative();
        }

        return interfaces;
    }

    /**
     * 使用ipconfig命令获取网络接口
     */
    private List<NetworkInterface> getNetworkInterfacesFromIpconfig() {
        List<NetworkInterface> interfaces = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder("C:\\Windows\\System32\\ipconfig.exe", "/all");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            String currentAdapter = null;
            boolean isConnected = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // 查找网络适配器名称（中文��统）
                if (line.contains("适配器") && line.contains(":")) {
                    if (currentAdapter != null && !currentAdapter.isEmpty()) {
                        // 保存前一个适配器
                        interfaces.add(new NetworkInterface(currentAdapter, isConnected));
                    }

                    // 提取适配器名称
                    int colonIndex = line.indexOf(":");
                    if (colonIndex > 0) {
                        String fullLine = line.substring(0, colonIndex).trim();
                        // 移除各种适配器类型前缀
                        currentAdapter = fullLine.replace("无线局域网适配器", "").replace("以太网适配器", "")
                                                .replace("未知适配器", "").replace("适配器", "").trim();
                        isConnected = false;
                    }
                } else if (line.contains("Ethernet adapter") && line.contains(":")) {
                    if (currentAdapter != null && !currentAdapter.isEmpty()) {
                        interfaces.add(new NetworkInterface(currentAdapter, isConnected));
                    }

                    int colonIndex = line.indexOf(":");
                    if (colonIndex > 0) {
                        currentAdapter = line.substring("Ethernet adapter".length(), colonIndex).trim();
                        isConnected = false;
                    }
                } else if (line.contains("Wireless LAN adapter") && line.contains(":")) {
                    if (currentAdapter != null && !currentAdapter.isEmpty()) {
                        interfaces.add(new NetworkInterface(currentAdapter, isConnected));
                    }

                    int colonIndex = line.indexOf(":");
                    if (colonIndex > 0) {
                        currentAdapter = line.substring("Wireless LAN adapter".length(), colonIndex).trim();
                        isConnected = false;
                    }
                }

                // 检查是否已连接（有IP地址，且不是断开连接状态）
                if (currentAdapter != null && !line.contains("媒体已断开连接") && !line.contains("Media disconnected")) {
                    if (line.contains("IPv4 地址") || line.contains("IP Address")) {
                        if (line.contains("192.") || line.contains("10.") || line.contains("172.") ||
                            line.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*")) {
                            isConnected = true;
                        }
                    }
                }

                // 检查媒体断开连接状态
                if (currentAdapter != null && (line.contains("媒体已断开连接") || line.contains("Media disconnected"))) {
                    isConnected = false;
                }
            }

            // 保存最后一个适配器
            if (currentAdapter != null && !currentAdapter.isEmpty()) {
                interfaces.add(new NetworkInterface(currentAdapter, isConnected));
            }

            reader.close();
            process.waitFor();

        } catch (Exception e) {
            System.err.println("使用ipconfig获取网络接口失败: " + e.getMessage());
        }

        return interfaces;
    }

    /**
     * 使用wmic命令获取网络接口
     */
    private List<NetworkInterface> getNetworkInterfacesFromWmic() {
        List<NetworkInterface> interfaces = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder("C:\\Windows\\System32\\wbem\\wmic.exe", "nic", "where", "NetEnabled=true", "get", "Name,NetConnectionStatus", "/format:csv");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (firstLine || line.isEmpty()) {
                    firstLine = false;
                    continue;
                }

                // CSV格式解析
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String name = parts[1].trim();
                    String status = parts[2].trim();

                    if (!name.isEmpty() && !name.equalsIgnoreCase("Name")) {
                        // 过滤掉一些不需要的接口
                        if (!name.toLowerCase().contains("loopback") &&
                            !name.toLowerCase().contains("isatap") &&
                            !name.toLowerCase().contains("teredo")) {

                            // NetConnectionStatus: 2 = Connected
                            boolean isConnected = "2".equals(status);
                            interfaces.add(new NetworkInterface(name, isConnected));
                        }
                    }
                }
            }

            reader.close();
            process.waitFor();

        } catch (Exception e) {
            System.err.println("使用wmic获取网络接口失败: " + e.getMessage());
        }

        return interfaces;
    }

    /**
     * 使用netsh命令获取网络接口（原有方法）
     */
    private List<NetworkInterface> getNetworkInterfacesFromNetsh() {
        List<NetworkInterface> interfaces = new ArrayList<>();

        try {
            // 尝试使用完整路径的netsh
            ProcessBuilder pb = new ProcessBuilder("C:\\Windows\\System32\\netsh.exe", "interface", "show", "interface");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            boolean headerPassed = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // 跳过标题行
                if (!headerPassed) {
                    if (line.contains("状态") || line.contains("Admin State") || line.contains("---")) {
                        headerPassed = true;
                    }
                    continue;
                }

                if (!line.isEmpty() && !line.startsWith("-")) {
                    // 格式通常是: 状态  类型  接口名称
                    String[] parts = line.split("\\s+", 4);
                    if (parts.length >= 4) {
                        String status = parts[1].trim();
                        String interfaceName = parts[3].trim();

                        // 检查是否为已连接状态
                        boolean isConnected = "已连接".equals(status) || "Connected".equals(status) || "Enabled".equals(status);

                        interfaces.add(new NetworkInterface(interfaceName, isConnected));
                    }
                }
            }

            reader.close();
            process.waitFor();

        } catch (Exception e) {
            System.err.println("使用netsh获取网络接口失败: " + e.getMessage());
        }

        return interfaces;
    }

    /**
     * 备用方法获取网络接口
     */
    private List<NetworkInterface> getNetworkInterfacesAlternative() {
        List<NetworkInterface> interfaces = new ArrayList<>();

        System.err.println("所有方法都失败，使用备用网络接口列表");

        // 添加常见的网络接口名称
        interfaces.add(new NetworkInterface("以太网", true));
        interfaces.add(new NetworkInterface("WLAN", true));
        interfaces.add(new NetworkInterface("Wi-Fi", true));
        interfaces.add(new NetworkInterface("Local Area Connection", true));
        interfaces.add(new NetworkInterface("以太网 2", false));
        interfaces.add(new NetworkInterface("以太网 3", false));

        return interfaces;
    }

    /**
     * 刷新DNS缓存
     */
    public boolean flushDnsCache() {
        try {
            ProcessBuilder pb = new ProcessBuilder("C:\\Windows\\System32\\ipconfig.exe", "/flushdns");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("刷新DNS缓存失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 设置DNS服务器
     */
    public boolean setDns(String interfaceName, String primaryDns, String secondaryDns) {
        try {
            // 尝试使用完整路径的netsh
            ProcessBuilder pb1 = new ProcessBuilder("C:\\Windows\\System32\\netsh.exe", "interface", "ip", "set", "dns",
                interfaceName, "static", primaryDns);
            Process process1 = pb1.start();
            int exitCode1 = process1.waitFor();

            if (exitCode1 != 0) {
                // 如果失败，尝试使用PowerShell方法
                return setDnsUsingPowerShell(interfaceName, primaryDns, secondaryDns);
            }

            // 设置辅助DNS（如果提供）
            if (secondaryDns != null && !secondaryDns.trim().isEmpty()) {
                ProcessBuilder pb2 = new ProcessBuilder("C:\\Windows\\System32\\netsh.exe", "interface", "ip", "add", "dns",
                    interfaceName, secondaryDns, "index=2");
                Process process2 = pb2.start();
                process2.waitFor(); // 辅助DNS设置失败不影响主要功能
            }

            return true;
        } catch (Exception e) {
            System.err.println("设置DNS失败: " + e.getMessage());
            return setDnsUsingPowerShell(interfaceName, primaryDns, secondaryDns);
        }
    }

    /**
     * 使用PowerShell设置DNS（备用方法）
     */
    private boolean setDnsUsingPowerShell(String interfaceName, String primaryDns, String secondaryDns) {
        try {
            String command = String.format("Set-DnsClientServerAddress -InterfaceAlias \\\"%s\\\" -ServerAddresses \\\"%s\\\"",
                interfaceName, primaryDns);

            if (secondaryDns != null && !secondaryDns.trim().isEmpty()) {
                command = String.format("Set-DnsClientServerAddress -InterfaceAlias \\\"%s\\\" -ServerAddresses @(\\\"%s\\\",\\\"%s\\\")",
                    interfaceName, primaryDns, secondaryDns);
            }

            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", command);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("使用PowerShell设置DNS失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 设置DNS为自动获取
     */
    public boolean setDnsToAuto(String interfaceName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("C:\\Windows\\System32\\netsh.exe", "interface", "ip", "set", "dns",
                interfaceName, "dhcp");
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                // 使用PowerShell备用方法
                return setDnsToAutoUsingPowerShell(interfaceName);
            }

            return true;
        } catch (Exception e) {
            System.err.println("设置DNS为自动获取失败: " + e.getMessage());
            return setDnsToAutoUsingPowerShell(interfaceName);
        }
    }

    /**
     * 使用PowerShell设置DNS为自动获取
     */
    private boolean setDnsToAutoUsingPowerShell(String interfaceName) {
        try {
            String command = String.format("Set-DnsClientServerAddress -InterfaceAlias \\\"%s\\\" -ResetServerAddresses",
                interfaceName);

            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", command);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("使用PowerShell设置DNS为自动获取失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 为所有活动网络接口设置DNS
     */
    public boolean setDnsForAllInterfaces(String primaryDns, String secondaryDns) {
        boolean success = false;
        List<NetworkInterface> interfaces = getNetworkInterfaces();

        for (NetworkInterface intf : interfaces) {
            if (intf.isConnected()) {
                boolean result = setDns(intf.getName(), primaryDns, secondaryDns);
                if (result) {
                    success = true;
                }
            }
        }

        return success;
    }

    /**
     * 设置DNS服务器 - 修复方法签名
     */
    public boolean setDns(int interfaceIndex, String primaryDns, String secondaryDns) {
        List<NetworkInterface> interfaces = getNetworkInterfaces();
        if (interfaceIndex >= 0 && interfaceIndex < interfaces.size()) {
            return setDns(interfaces.get(interfaceIndex).getName(), primaryDns, secondaryDns);
        }
        return false;
    }

    /**
     * 设置DNS为自动获取 - 修复方法签名
     */
    public boolean setAutoDns(int interfaceIndex) {
        List<NetworkInterface> interfaces = getNetworkInterfaces();
        if (interfaceIndex >= 0 && interfaceIndex < interfaces.size()) {
            return setDnsToAuto(interfaces.get(interfaceIndex).getName());
        }
        return false;
    }

    /**
     * 网络接口信息类
     */
    public static class NetworkInterface {
        private final String name;
        private final boolean connected;
        private final int index;

        public NetworkInterface(String name, boolean connected) {
            this.name = name;
            this.connected = connected;
            this.index = 0; // 默认索引
        }

        public NetworkInterface(String name, boolean connected, int index) {
            this.name = name;
            this.connected = connected;
            this.index = index;
        }

        public String getName() {
            return name;
        }

        public boolean isConnected() {
            return connected;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return name + (connected ? " (已连接)" : " (未连接)");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            NetworkInterface that = (NetworkInterface) obj;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
