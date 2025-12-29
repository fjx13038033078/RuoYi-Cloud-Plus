package org.dromara.camera.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;


@Component
@Slf4j
@RequiredArgsConstructor
public class ExternalAnalysisClient {

    private static final String ANALYSIS_SERVICE_URL = "http://192.168.26.28:8000/upload-video";

    public String analyzeVideoRaw(MultipartFile file) throws IOException {
        try (CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost httpPost = createMultipartRequest(file);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                return handleRawResponse(response);
            }
        } catch (SocketTimeoutException e) {
            log.error("连接外部服务超时", e);
            throw new IOException("连接外部服务超时: " + e.getMessage());
        } catch (ConnectException e) {
            log.error("连接外部服务失败", e);
            throw new IOException("无法连接到分析服务，请检查服务是否正常运行");
        }
    }

    /**
     * 创建HTTP客户端（配置连接池和超时）
     */
    private CloseableHttpClient createHttpClient() {
        // 配置超时参数
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(60000)      // 连接超时 60秒
            .setSocketTimeout(300000)      // 读取超时 300秒（5分钟）
            .setConnectionRequestTimeout(60000) // 获取连接超时 60秒
            .build();

        // 配置连接池
        PoolingHttpClientConnectionManager connectionManager =
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);           // 最大连接数
        connectionManager.setDefaultMaxPerRoute(20);  // 每个路由最大连接数

        return HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();
    }

    /**
     * 创建multipart请求
     */
    private HttpPost createMultipartRequest(MultipartFile file) throws IOException {
        log.info("开始上传视频文件: {}, 大小: {} MB",
            file.getOriginalFilename(),
            String.format("%.2f", file.getSize() / (1024.0 * 1024.0)));

        // 构建multipart实体
        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
            .addPart("file", new ByteArrayBody(
                file.getBytes(),
                ContentType.DEFAULT_BINARY,
                file.getOriginalFilename()
            ))
            .addTextBody("timestamp", String.valueOf(System.currentTimeMillis()));

        // 创建请求
        HttpPost httpPost = new HttpPost(ANALYSIS_SERVICE_URL);
        httpPost.setEntity(builder.build());
        httpPost.setHeader("Accept", "application/json");

        return httpPost;
    }

    /**
     * 处理原始响应
     */
    private String handleRawResponse(CloseableHttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

        log.info("分析接口响应 - 状态码: {}, 响应体长度: {}", statusCode, responseBody.length());

        // 检查HTTP状态码
        if (statusCode != 200) {
            handleErrorResponse(statusCode, responseBody);
        }

        return responseBody;
    }

    /**
     * 处理错误响应
     */
    private void handleErrorResponse(int statusCode, String responseBody) throws IOException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> errorResponse = objectMapper.readValue(responseBody, Map.class);
            String errorMsg = (String) errorResponse.getOrDefault("message",
                "分析服务返回错误状态码: " + statusCode);
            log.error("分析服务返回错误: {}", errorMsg);
            throw new IOException(errorMsg);
        } catch (Exception e) {
            log.error("解析错误响应失败", e);
            throw new IOException("分析服务返回错误状态码: " + statusCode);
        }
    }
}

