package org.dromara.camera.client;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 视频分析客户端
 * 用于调用外部AI视频分析服务
 *
 * @author LionLi
 */
@Slf4j
@Component
public class VideoAnalysisClient {

//    @Value("${camera.analysis.url:http://192.168.26.28:8100/upload-video}")
    @Value("${camera.analysis.url:http://localhost:8100/upload-video}")
    private String analysisServiceUrl;

    @Value("${camera.analysis.connect-timeout:60000}")
    private int connectTimeout;

    @Value("${camera.analysis.socket-timeout:300000}")
    private int socketTimeout;

    /**
     * 分析视频并返回原始JSON响应
     *
     * @param file 视频文件
     * @return JSON响应字符串
     * @throws IOException 如果请求失败
     */
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
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(connectTimeout)
            .setSocketTimeout(socketTimeout)
            .setConnectionRequestTimeout(connectTimeout)
            .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);

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

        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
            .addPart("file", new ByteArrayBody(
                file.getBytes(),
                ContentType.DEFAULT_BINARY,
                file.getOriginalFilename()
            ))
            .addTextBody("timestamp", String.valueOf(System.currentTimeMillis()));

        HttpPost httpPost = new HttpPost(analysisServiceUrl);
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

        if (statusCode != 200) {
            handleErrorResponse(statusCode, responseBody);
        }

        return responseBody;
    }

    /**
     * 处理错误响应
     */
    @SuppressWarnings("unchecked")
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
