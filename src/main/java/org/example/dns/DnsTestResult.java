package org.example.dns;

/**
 * DNS测试结果类
 */
public class DnsTestResult implements Comparable<DnsTestResult> {
    private String dnsServer;
    private long latency;
    private boolean success;
    private String errorMessage;

    public DnsTestResult(String dnsServer, long latency, boolean success, String errorMessage) {
        this.dnsServer = dnsServer;
        this.latency = latency;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getDnsServer() {
        return dnsServer;
    }

    public long getLatency() {
        return latency;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    // JavaFX TableView需要的格式化方法
    public String getLatencyString() {
        if (!success) {
            return "超时";
        }
        return latency + "ms";
    }

    public String getStatusString() {
        if (success) {
            if (latency < 50) {
                return "极佳";
            } else if (latency < 100) {
                return "良好";
            } else if (latency < 200) {
                return "一般";
            } else {
                return "较慢";
            }
        } else {
            return "失败";
        }
    }

    @Override
    public int compareTo(DnsTestResult other) {
        // 失败的结果排在后面
        if (this.success && !other.success) {
            return -1;
        }
        if (!this.success && other.success) {
            return 1;
        }

        // 都成功时按延迟排序
        if (this.success && other.success) {
            return Long.compare(this.latency, other.latency);
        }

        // 都失败时按DNS服务器名称排序
        return this.dnsServer.compareTo(other.dnsServer);
    }

    @Override
    public String toString() {
        return String.format("DnsTestResult{server='%s', latency=%d, success=%s}",
                dnsServer, latency, success);
    }
}
