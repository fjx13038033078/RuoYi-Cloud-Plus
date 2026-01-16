package org.dromara.camera.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    private final String videoUploadQueue;
    private final String videoUploadExchange;
    private final String videoUploadRoutingKey;

    /**
     * 使用构造函数注入，避免循环依赖和字段注入问题
     */
    public RabbitMQConfig(
            @Value("${spring.rabbitmq.video-upload.queue}") String videoUploadQueue,
            @Value("${spring.rabbitmq.video-upload.exchange}") String videoUploadExchange,
            @Value("${spring.rabbitmq.video-upload.routing-key}") String videoUploadRoutingKey) {
        this.videoUploadQueue = videoUploadQueue;
        this.videoUploadExchange = videoUploadExchange;
        this.videoUploadRoutingKey = videoUploadRoutingKey;
    }

    /**
     * 视频上传队列
     * 添加死信队列配置
     */
    @Bean
    public Queue videoUploadQueue() {
        return QueueBuilder.durable(videoUploadQueue)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", videoUploadQueue + ".dlq")
                .withArgument("x-max-length", 10000)  // 可选：限制队列长度
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue videoUploadDlqQueue() {
        return QueueBuilder.durable(videoUploadQueue + ".dlq")
                .withArgument("x-message-ttl", 60000)  // 消息存活时间60秒
                .build();
    }

    /**
     * 视频上传交换机
     */
    @Bean
    public DirectExchange videoUploadExchange() {
        return ExchangeBuilder
                .directExchange(videoUploadExchange)
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

    /**
     * JSON消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 可选配置：处理LocalDateTime等Java 8时间类型
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

        // 开启消息返回机制
        rabbitTemplate.setMandatory(true);

        // 设置确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("消息发送到交换机成功: {}", correlationData);
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
     * 消息监听容器配置（可选）
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(100);

        // 使用Spring Boot自动配置的重试
        factory.setAdviceChain(org.springframework.amqp.rabbit.config.RetryInterceptorBuilder
                .stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .build());

        return factory;
    }

}
