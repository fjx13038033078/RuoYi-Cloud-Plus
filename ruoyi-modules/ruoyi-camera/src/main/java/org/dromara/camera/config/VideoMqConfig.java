package org.dromara.camera.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 视频消息队列配置
 *
 * @author LionLi
 */
@Slf4j
@Configuration
public class VideoMqConfig {

    // 视频上传队列配置（发送给Python）
    private final String videoUploadQueue;
    private final String videoUploadExchange;
    private final String videoUploadRoutingKey;

    // 结果队列配置（接收Python回传的结果）
    @Value("${spring.rabbitmq.video-result.queue:video.result.queue}")
    private String videoResultQueue;

    @Value("${spring.rabbitmq.video-result.exchange:video.result.exchange}")
    private String videoResultExchange;

    @Value("${spring.rabbitmq.video-result.routing-key:video.result.finish}")
    private String videoResultRoutingKey;

    // 切割任务队列配置（发给 Python）
    @Value("${spring.rabbitmq.video-clip.queue:video.clip.queue}")
    private String videoClipQueue;

    @Value("${spring.rabbitmq.video-clip.exchange:video.clip.exchange}")
    private String videoClipExchange;

    @Value("${spring.rabbitmq.video-clip.routing-key:video.clip.task}")
    private String videoClipRoutingKey;

    // 切割结果队列配置（接收Python回传的切割结果）
    @Value("${spring.rabbitmq.video-clip-result.queue:video.clip.result.queue}")
    private String videoClipResultQueue;

    @Value("${spring.rabbitmq.video-clip-result.exchange:video.clip.result.exchange}")
    private String videoClipResultExchange;

    @Value("${spring.rabbitmq.video-clip-result.routing-key:video.clip.result.finish}")
    private String videoClipResultRoutingKey;

    public VideoMqConfig(
        @Value("${spring.rabbitmq.video-upload.queue}") String videoUploadQueue,
        @Value("${spring.rabbitmq.video-upload.exchange}") String videoUploadExchange,
        @Value("${spring.rabbitmq.video-upload.routing-key}") String videoUploadRoutingKey) {
        this.videoUploadQueue = videoUploadQueue;
        this.videoUploadExchange = videoUploadExchange;
        this.videoUploadRoutingKey = videoUploadRoutingKey;
    }

    /**
     * 视频上传队列（添加死信队列配置）
     */
    @Bean
    public Queue videoUploadQueue() {
        return QueueBuilder.durable(videoUploadQueue)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", videoUploadQueue + ".dlq")
            .withArgument("x-max-length", 10000)
            .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue videoUploadDlqQueue() {
        return QueueBuilder.durable(videoUploadQueue + ".dlq")
            .withArgument("x-message-ttl", 60000)
            .build();
    }

    /**
     * 视频检测交换机（Topic Exchange，支持通配符路由）
     */
    @Bean
    public TopicExchange videoUploadExchange() {
        return ExchangeBuilder
            .topicExchange(videoUploadExchange)
            .durable(true)
            .build();
    }

    /**
     * 绑定队列和交换机
     */
    @Bean
    public Binding videoUploadBinding() {
        return BindingBuilder
            .bind(videoUploadQueue())
            .to(videoUploadExchange())
            .with(videoUploadRoutingKey);
    }

    // ==================== 结果队列配置（接收Python回传的检测结果） ====================

    /**
     * 视频结果队列（接收AI检测结果）
     */
    @Bean
    public Queue videoResultQueue() {
        return QueueBuilder.durable(videoResultQueue)
            .withArgument("x-max-length", 10000)
            .build();
    }

    /**
     * 视频结果交换机（Topic Exchange）
     */
    @Bean
    public TopicExchange videoResultExchange() {
        return ExchangeBuilder
            .topicExchange(videoResultExchange)
            .durable(true)
            .build();
    }

    /**
     * 绑定结果队列和交换机
     */
    @Bean
    public Binding videoResultBinding() {
        return BindingBuilder
            .bind(videoResultQueue())
            .to(videoResultExchange())
            .with(videoResultRoutingKey);
    }

    // ==================== 切割任务队列配置（发给 Python） ====================

    /** 切割任务队列 */
    @Bean
    public Queue videoClipQueue() {
        return QueueBuilder.durable(videoClipQueue)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", videoClipQueue + ".dlq")
            .withArgument("x-max-length", 1000)
            .build();
    }

    /** 切割任务死信队列 */
    @Bean
    public Queue videoClipDlqQueue() {
        return QueueBuilder.durable(videoClipQueue + ".dlq").build();
    }

    /** 切割任务交换机 */
    @Bean
    public TopicExchange videoClipExchange() {
        return ExchangeBuilder.topicExchange(videoClipExchange).durable(true).build();
    }

    /** 绑定切割任务队列 */
    @Bean
    public Binding videoClipBinding() {
        return BindingBuilder.bind(videoClipQueue()).to(videoClipExchange()).with(videoClipRoutingKey);
    }

    // ==================== 切割结果队列配置（接收 Python 回传） ====================

    /** 切割结果队列 */
    @Bean
    public Queue videoClipResultQueue() {
        return QueueBuilder.durable(videoClipResultQueue)
            .withArgument("x-max-length", 10000)
            .build();
    }

    /** 切割结果交换机 */
    @Bean
    public TopicExchange videoClipResultExchange() {
        return ExchangeBuilder.topicExchange(videoClipResultExchange).durable(true).build();
    }

    /** 绑定切割结果队列 */
    @Bean
    public Binding videoClipResultBinding() {
        return BindingBuilder.bind(videoClipResultQueue()).to(videoClipResultExchange()).with(videoClipResultRoutingKey);
    }

    /**
     * JSON消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setMandatory(true);

        // 设置确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("消息发送到交换机成功: {}", correlationData);
            } else {
                log.error("消息发送到交换机失败: {}, 原因: {}", correlationData, cause);
            }
        });

        // 设置返回回调
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("消息路由到队列失败: 消息: {}, 响应码: {}, 原因: {}, 交换机: {}, 路由键: {}",
                returned.getMessage(), returned.getReplyCode(),
                returned.getReplyText(), returned.getExchange(),
                returned.getRoutingKey());
        });

        return rabbitTemplate;
    }

    /**
     * 消息监听容器配置
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(100);

        factory.setAdviceChain(org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
            .stateless()
            .maxAttempts(3)
            .backOffOptions(1000, 2.0, 10000)
            .build());

        return factory;
    }
}
