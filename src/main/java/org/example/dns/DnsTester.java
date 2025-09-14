package org.example.dns;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DNS延迟测试器 - 使用dig命令测试DNS服务器延迟
 */
public class DnsTester {
    private static final String TEST_DOMAIN = "www.baidu.com";
    private static final int TIMEOUT_MS = 300; // 改为300ms超时
    private static final int TEST_COUNT = 3; // 每个DNS服务器测试3次取平均值

    private String digExecutablePath;

    public DnsTester() {
        initializeDigPath();
    }

    /**
     * 初始化dig可执行文件路径
     */
    private void initializeDigPath() {
        try {
            // 首先尝试从resources中提取dig.exe
            digExecutablePath = extractDigFromResources();

            if (digExecutablePath == null) {
                // 如果提取失败，尝试系统路径
                digExecutablePath = findSystemDigPath();
            }

            if (digExecutablePath != null) {
                System.out.println("找到dig可执行文件: " + digExecutablePath);
            } else {
                System.out.println("警告: 未找到dig可执行文件，将使用备用DNS查询方法");
            }
        } catch (Exception e) {
            System.out.println("初始化dig路径时出错: " + e.getMessage());
            digExecutablePath = null;
        }
    }

    /**
     * 从resources中提取dig可执行文件到临时目录
     */
    private String extractDigFromResources() {
        try {
            // 获取dig.exe的资源路径
            URL digResource = getClass().getClassLoader().getResource("dig_path/dig.exe");
            if (digResource == null) {
                System.out.println("未在resources/dig_path中找到dig.exe");
                return null;
            }

            // 创建临时目录
            Path tempDir = Files.createTempDirectory("dns-tool-dig");
            Path digPath = tempDir.resolve("dig.exe");

            // 提取dig.exe到临时目录
            try (InputStream inputStream = digResource.openStream()) {
                Files.copy(inputStream, digPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 同时提取所需的DLL文件
            String[] requiredFiles = {
                "libbind9.dll", "libdns.dll", "libisc.dll", "libisccc.dll",
                "libisccfg.dll", "libirs.dll", "libns.dll", "libcrypto-1_1-x64.dll",
                "libssl-1_1-x64.dll", "libxml2.dll", "nghttp2.dll", "uv.dll"
            };

            for (String fileName : requiredFiles) {
                URL resourceUrl = getClass().getClassLoader().getResource("dig_path/" + fileName);
                if (resourceUrl != null) {
                    Path filePath = tempDir.resolve(fileName);
                    try (InputStream inputStream = resourceUrl.openStream()) {
                        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // 设置可执行权限（Windows上通常不需要，但为了兼容性）
            digPath.toFile().setExecutable(true);

            // 注册关闭钩子以清理临时文件
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(digPath);
                    Files.deleteIfExists(tempDir);
                } catch (Exception e) {
                    // 忽略清理错误
                }
            }));

            return digPath.toString();
        } catch (Exception e) {
            System.out.println("从resources提取dig.exe失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 查找系统中的dig可执行文件
     */
    private String findSystemDigPath() {
        String[] digPaths = {
            "dig",
            "dig.exe",
            "C:\\Program Files\\BIND\\bin\\dig.exe",
            "C:\\Tools\\dig.exe",
            System.getProperty("user.dir") + "\\dig.exe"
        };

        for (String path : digPaths) {
            try {
                ProcessBuilder pb = new ProcessBuilder(path, "-v");
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return path;
                }
            } catch (Exception e) {
                // 继续尝试下一个路径
            }
        }

        return null;
    }

    /**
     * 测试单个DNS服务器的延迟 - 使用dig命令
     */
    public DnsTestResult testSingleDns(String dnsServer) {
        try {
            List<Long> latencies = new ArrayList<>();

            for (int i = 0; i < TEST_COUNT; i++) {
                Long latency = performDigTest(dnsServer);

                if (latency != null && latency > 0) {
                    // 检查延迟是否超过300ms
                    if (latency > TIMEOUT_MS) {
                        return new DnsTestResult(dnsServer, latency, false, "延迟超过300ms");
                    }
                    latencies.add(latency);
                } else {
                    // 如果dig失败，尝试使用Java DNS查询作为备用
                    Long fallbackLatency = performJavaDnsTest(dnsServer);
                    if (fallbackLatency != null && fallbackLatency > 0) {
                        // 检查备用测试的延迟是否超过300ms
                        if (fallbackLatency > TIMEOUT_MS) {
                            return new DnsTestResult(dnsServer, fallbackLatency, false, "延迟超过300ms");
                        }
                        latencies.add(fallbackLatency);
                    } else {
                        return new DnsTestResult(dnsServer, 0, false, "DNS查询失败");
                    }
                }
            }

            if (latencies.isEmpty()) {
                return new DnsTestResult(dnsServer, 0, false, "所有测试都失败");
            }

            // 计算平均延迟
            long avgLatency = (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);

            // 最终检查平均延迟是否超过300ms
            if (avgLatency > TIMEOUT_MS) {
                return new DnsTestResult(dnsServer, avgLatency, false, "延迟超过300ms");
            }

            return new DnsTestResult(dnsServer, avgLatency, true, null);

        } catch (Exception e) {
            return new DnsTestResult(dnsServer, 0, false, e.getMessage());
        }
    }

    /**
     * 使用dig命令测试DNS延迟
     */
    private Long performDigTest(String dnsServer) {
        if (digExecutablePath == null) {
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            // 修改dig命令参数，将超时时间改为1秒，重试次数为1次
            pb.command(digExecutablePath, "@" + dnsServer, TEST_DOMAIN, "A", "+time=1", "+tries=1");
            pb.redirectErrorStream(true);

            long startTime = System.currentTimeMillis();
            Process process = pb.start();

            // 使用Future来控制进程超时
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Integer> future = executor.submit(() -> {
                try {
                    return process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            });

            // 读取命令输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            try {
                // 等待进程完成，最多等待500ms（给dig命令一些缓冲时间）
                int exitCode = future.get(500, TimeUnit.MILLISECONDS);

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                long endTime = System.currentTimeMillis();
                long actualLatency = endTime - startTime;

                reader.close();
                executor.shutdown();

                if (exitCode == 0) {
                    // 解析dig输出中的查询时间，如果没有则使用实际测量时间
                    Long parsedLatency = parseDigOutput(output.toString());
                    return parsedLatency != null ? parsedLatency : actualLatency;
                } else {
                    return null;
                }

            } catch (TimeoutException e) {
                // 超时，强制终止进程
                process.destroyForcibly();
                future.cancel(true);
                executor.shutdown();
                reader.close();
                return null; // 超时返回null，会触发备用测试
            }

        } catch (Exception e) {
            System.out.println("dig命令执行失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析dig命令输出，提取查询时间
     */
    private Long parseDigOutput(String output) {
        try {
            // 查找查询时间行，格式通常是：";; Query time: 23 msec"
            Pattern pattern = Pattern.compile(";; Query time: (\\d+) msec");
            Matcher matcher = pattern.matcher(output);

            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }

            // 备用模式：查找包含"msec"的行
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.contains("msec") && line.contains("Query time")) {
                    // 尝试提取数字
                    Pattern numberPattern = Pattern.compile("(\\d+)\\s*msec");
                    Matcher numberMatcher = numberPattern.matcher(line);
                    if (numberMatcher.find()) {
                        return Long.parseLong(numberMatcher.group(1));
                    }
                }
            }

            // 如果找到了答案记录，但没有查询时间，假设延迟很低
            if (output.contains("ANSWER SECTION") || output.contains("IN\\s+A\\s+")) {
                return 1L; // 返回1ms作为成功但无法测量的标识
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Java DNS查询备用方案（保留原有逻辑）
     */
    private Long performJavaDnsTest(String dnsServer) {
        try {
            long startTime = System.currentTimeMillis();

            // 使用Runtime执行nslookup作为备用方案
            ProcessBuilder pb = new ProcessBuilder("nslookup", TEST_DOMAIN, dnsServer);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 使用Future来控制进程超时
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Integer> future = executor.submit(() -> {
                try {
                    return process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            });

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            try {
                // 等待进程完成，最多等待500ms
                int exitCode = future.get(500, TimeUnit.MILLISECONDS);

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                long endTime = System.currentTimeMillis();
                long actualLatency = endTime - startTime;

                reader.close();
                executor.shutdown();

                if (exitCode == 0 && (output.toString().contains("Address") || output.toString().contains("地址"))) {
                    return actualLatency;
                }

                return null;

            } catch (TimeoutException e) {
                // 超时，强制终止进程
                process.destroyForcibly();
                future.cancel(true);
                executor.shutdown();
                reader.close();
                return null;
            }

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 并发测试多个DNS服务器
     */
    public List<DnsTestResult> testMultipleDns(List<String> dnsServers) {
        List<DnsTestResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(10, dnsServers.size()));
        List<Future<DnsTestResult>> futures = new ArrayList<>();

        for (String dnsServer : dnsServers) {
            Future<DnsTestResult> future = executor.submit(() -> testSingleDns(dnsServer));
            futures.add(future);
        }

        for (Future<DnsTestResult> future : futures) {
            try {
                results.add(future.get(15, TimeUnit.SECONDS)); // 15秒超时
            } catch (Exception e) {
                // 如果获取结果失败，添加一个失败的结果
                results.add(new DnsTestResult("unknown", 0, false, "测试超时或失败"));
            }
        }

        executor.shutdown();

        // 按延迟排序
        Collections.sort(results);

        return results;
    }

    /**
     * 获取常用的DNS服务器列表
     */
    public static List<String> getCommonDnsServers() {
        List<String> dnsServers = new ArrayList<>();

        // 国内DNS
        dnsServers.add("223.5.5.5");      // 阿里云 DNS
        dnsServers.add("223.6.6.6");      // 阿里云 DNS
        dnsServers.add("114.114.114.114"); // 114 DNS
        dnsServers.add("114.114.115.115"); // 114 DNS
        dnsServers.add("119.29.29.29");   // 腾讯 DNS
        dnsServers.add("182.254.116.116"); // 腾讯 DNS
        dnsServers.add("180.76.76.76");   // 百度 DNS
        dnsServers.add("1.2.4.8");        // sDNS
        dnsServers.add("210.2.4.8");      // sDNS
        dnsServers.add("101.226.4.6");    // 电信 DNS
        dnsServers.add("218.30.118.6");   // 电信 DNS
        dnsServers.add("123.125.81.6");   // 联通 DNS
        dnsServers.add("140.207.198.6");  // 联通 DNS
        dnsServers.add("121.4.4.201");    // 中移动 DNS
        dnsServers.add("121.4.4.40");     // 中移动 DNS

        // 国外DNS
        dnsServers.add("8.8.8.8");        // Google DNS
        dnsServers.add("8.8.4.4");        // Google DNS
        dnsServers.add("1.1.1.1");        // Cloudflare DNS
        dnsServers.add("1.0.0.1");        // Cloudflare DNS
        dnsServers.add("208.67.222.222");  // OpenDNS
        dnsServers.add("208.67.220.220");  // OpenDNS
        dnsServers.add("9.9.9.9");        // Quad9 DNS
        dnsServers.add("149.112.112.112"); // Quad9 DNS

        return dnsServers;
    }
}
