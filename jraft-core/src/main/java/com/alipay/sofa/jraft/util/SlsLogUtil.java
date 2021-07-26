package com.alipay.sofa.jraft.util;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogItem;
import org.apache.commons.logging.impl.Log4JLogger;
import org.slf4j.Logger;

public class SlsLogUtil {

    private static final String CONFIG_ACCESS_ID_KEY = "accessId";
    private static final String CONFIG_ACCESS_KEY_KEY = "accessKey";
    private static final String CONFIG_PROJECT_KEY = "project";
    private static final String CONFIG_HOST_KEY = "host";
    private static final String CONFIG_LOGSTORE_KEY = "logStore";

    private static Map<String, String> aliLogAccountConfig() {
        try {
            return FileUtil.readConfigFromFile("/Users/guwei/rumwei/ali-log-account-config", "=");
        } catch (Exception e) {
            throw new RuntimeException("获取阿里云日志配置失败, e=" + e.getMessage());
        }
    }

    private static Client client() {
        Map<String, String> aliLogAccountConfig = aliLogAccountConfig();
        return new Client(
                aliLogAccountConfig.get(CONFIG_HOST_KEY),
                aliLogAccountConfig.get(CONFIG_ACCESS_ID_KEY),
                aliLogAccountConfig.get(CONFIG_ACCESS_KEY_KEY));
    }

    public static void info(String topic, String traceId, String message) {
        log(topic, "info", traceId, message);
    }

    public static void warn(String topic, String traceId, String message) {
        log(topic, "warn", traceId, message);
    }

    public static void error(String topic, String traceId, String message) {
        log(topic, "error", traceId, message);
    }

    private static void log(String topic, String level, String traceId, String message) {
        String timestamp = LocalDateTime.now().toString();
        String service = "sofa";
        String threadName = Thread.currentThread().getName();
        LogItem logItem = new LogItem();
        logItem.PushBack("level", level);
        logItem.PushBack("class", Thread.currentThread().getStackTrace()[3].getClassName());
        logItem.PushBack("timestamp", timestamp);
        logItem.PushBack("service", service);
        logItem.PushBack("thread", threadName);
        logItem.PushBack("traceId", traceId);
        logItem.PushBack("message", message);
        try {
            client().PutLogs(
                    aliLogAccountConfig().get(CONFIG_PROJECT_KEY),
                    aliLogAccountConfig().get(CONFIG_LOGSTORE_KEY),
                    topic,
                    Collections.singletonList(logItem),
                    ""
            );
        } catch (Exception e) {
            throw new RuntimeException("日志上传阿里云失败，e=" + e.getMessage());
        }

    }
}
