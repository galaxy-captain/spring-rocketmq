package me.galaxy.rocket;

import me.galaxy.rocket.annotation.RocketACL;
import me.galaxy.rocket.annotation.RocketConsumer;
import me.galaxy.rocket.annotation.RocketListener;
import me.galaxy.rocket.config.ConsumerConfig;
import me.galaxy.rocket.config.InfoConfig;
import me.galaxy.rocket.exception.DetectGenericTypeException;
import me.galaxy.rocket.listener.AbstractMessageListener;
import me.galaxy.rocket.listener.ConvertMessageListener;
import me.galaxy.rocket.listener.wrapper.ConcurrentlyListenerWrapper;
import me.galaxy.rocket.listener.wrapper.OrderlyListenerWrapper;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

import static me.galaxy.rocket.ConsumerConfigDetector.buildConsumerConfig;
import static me.galaxy.rocket.ConsumerConfigDetector.resolvePlaceholderWithProperties;
import static me.galaxy.rocket.RocketAnnotationDetector.*;

/**
 * @Description
 * @Author galaxy-captain
 * @Date 2019-06-21 12:08
 **/
public class RocketAnnotationBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private static final Logger logger = LoggerFactory.getLogger(RocketAnnotationBeanPostProcessor.class);

    private DefaultListableBeanFactory beanFactory;

    private volatile boolean hasInitConfiguration = false;

    private RocketConfiguration configuration;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (DefaultListableBeanFactory) beanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

        // 根据注解构建Consumer监听器
        this.buildRocketMQConsumerWithAnnotation(this.beanFactory, bean, beanName);

        return bean;
    }

    @Deprecated
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // do nothing
        return bean;
    }

    private void buildRocketMQConsumerWithAnnotation(DefaultListableBeanFactory beanFactory, Object bean, String beanName) {

        // 根据@RocketListener注解注册Consumer
        buildConsumerWithRocketListenerAnnotation(beanFactory, bean, beanName);

    }

    /**
     * 获取全局配置
     */
    private RocketConfiguration getRocketConfiguration() {

        if (!this.hasInitConfiguration) {
            synchronized (this) {
                if (!this.hasInitConfiguration) {
                    this.configuration = RocketAnnotationDetector.getRocketConfiguration(this.beanFactory);
                    hasInitConfiguration = true;
                }
            }
        }

        return this.configuration;
    }

    /**
     * 根据@RocketListener注解信息自动注册Consumer
     */
    private void buildConsumerWithRocketListenerAnnotation(DefaultListableBeanFactory beanFactory, Object bean, String beanName) {

        // 获取类中使用@RocketListener注解的方法
        Map<Method, RocketListener> annotatedMethods = rocketListenerAnnotatedMethod(bean.getClass());

        // 没有找到需要解析的注解
        if (annotatedMethods.isEmpty()) {
            return;
        }

        // 获取全局配置类
        RocketConfiguration configuration = getRocketConfiguration();

        // 获取Spring增强过的Bean
        Object cglibEnhancedObject = beanFactory.getBean(beanName);

        // 获取类上的配置项
        RocketConsumer rocketConsumer = getRocketMQAnnotation(bean);
        RocketACL acl = getAclConfigurationAnnotation(bean);

        AclClientRPCHook aclHook = generateRocketAclHook(acl);

        for (Map.Entry<Method, RocketListener> entry : annotatedMethods.entrySet()) {

            Method method = entry.getKey();
            RocketListener rocketListener = entry.getValue();

            ConsumerConfig config = buildConsumerConfig(beanFactory, configuration, rocketConsumer, rocketListener);
            config.setSimpleName(generateMessageListenerSimpleName(bean, method, config));

            // 如果aclHook存在，则覆盖全局aclHook
            if (aclHook != null) {
                config.setAclHook(aclHook);
            }

            DefaultMQPushConsumer consumer = buildRocketPushConsumer(config, cglibEnhancedObject, method);

            // 注册Consumer到BeanFactory容器
            registerConsumer(beanFactory, generateConsumerName(beanName, bean, method, config), consumer);

            if (logger.isInfoEnabled()) {
                logger.info("初始化RocketMQ Consumer监听器[consumerGroup={},topic={},tag={}]", config.getConsumerGroup(), config.getTopic(), config.getTag());
            }

        }

    }

    private String generateMessageListenerSimpleName(Object object, Method method, ConsumerConfig config) {
        return String.format("%s.%s[Topic=%s,Tag=%s]", object.getClass().getName(), method.getName(), config.getTopic(), config.getTag());
    }

    /**
     * 根据配置获取注册用的beanName
     */
    private String generateConsumerName(String beanName, Object bean, Method method, ConsumerConfig config) {
        return beanName + "-" + bean.getClass().getName() + "-" + method.getName() + "-" + config.getTopic() + "-" + config.getTag();
    }

    /**
     * 注册Consumer监听器到BeanFactory容器中
     */
    private void registerConsumer(DefaultListableBeanFactory beanFactory, String beanName, DefaultMQPushConsumer consumer) {
        beanFactory.registerSingleton(beanName, consumer);
    }

    /**
     * 根据ConsumerConfig的配置构建Consumer监听器
     */
    private DefaultMQPushConsumer buildRocketPushConsumer(ConsumerConfig cfg, Object object, Method method) {

        try {

            // abstract message listener
            AbstractMessageListener listener = new ConvertMessageListener(object, method, cfg);

            // RocketMQ Push Consumer
            DefaultMQPushConsumer consumer;

            // RPCHook
            if (cfg.getAclHook() != null) {
                consumer = new DefaultMQPushConsumer(cfg.getAclHook());
            } else if (cfg.getHook().length > 0) {
                consumer = new DefaultMQPushConsumer(cfg.getHook()[0].newInstance());
            } else {
                consumer = new DefaultMQPushConsumer();
            }

            // NameServer Address
            consumer.setNamesrvAddr(cfg.getNameServer());

            // Consumer Group
            consumer.setConsumerGroup(cfg.getConsumerGroup());

            // Batch Consume Max Size
            consumer.setConsumeMessageBatchMaxSize(cfg.getMaxBatchSize());

            // retry times
            consumer.setMaxReconsumeTimes(cfg.getRetryConsumeTimes());

            // Instance
            if (!StringUtils.isEmpty(cfg.getInstance())) {
                consumer.setInstanceName(cfg.getInstance());
            }

            // Orderly Listener or Concurrently Listener
            if (cfg.isOrderly()) {
                consumer.setMessageListener(new OrderlyListenerWrapper(listener));
            } else {
                consumer.setMessageListener(new ConcurrentlyListenerWrapper(listener));
            }

            // Subscribe Topic&Tag
            consumer.subscribe(cfg.getTopic(), cfg.getTag());

            return consumer;

        } catch (MQClientException e) {

            String msg = String.format(
                    "创建RocketMQ Consumer过程中，执行subscribe(topic,tag)失败，NameServer=%s，ConsumerGroup=%s，Topic=%s，Tag=%s",
                    cfg.getNameServer(),
                    cfg.getConsumerGroup(),
                    cfg.getTopic(),
                    cfg.getTag()
            );

            logger.error(msg, e);
        } catch (IllegalAccessException | InstantiationException e) {

            String msg = String.format(
                    "创建RocketMQ Consumer过程中，初始化RPCHook失败，NameServer=%s，ConsumerGroup=%s，Topic=%s，Tag=%s",
                    cfg.getNameServer(),
                    cfg.getConsumerGroup(),
                    cfg.getTopic(),
                    cfg.getTag()
            );

            logger.error(msg, e);
        } catch (DetectGenericTypeException e) {

            String msg = String.format(
                    "创建RocketMQ Consumer过程中，获取消息转换类型失败，NameServer=%s，ConsumerGroup=%s，Topic=%s，Tag=%s",
                    cfg.getNameServer(),
                    cfg.getConsumerGroup(),
                    cfg.getTopic(),
                    cfg.getTag()
            );

            logger.error(msg, e);
        }

        return null;
    }

    /**
     * 构建RocketMQ的ACL
     */
    private AclClientRPCHook generateRocketAclHook(RocketACL acl) {

        if (acl == null) {
            return null;
        }

        String accessKey = resolvePlaceholderWithProperties(beanFactory, acl.accessKey());
        String secretKey = resolvePlaceholderWithProperties(beanFactory, acl.secretKey());

        if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(secretKey)) {
            return null;
        }

        SessionCredentials credentials = new SessionCredentials();
        credentials.setAccessKey(accessKey);
        credentials.setSecretKey(secretKey);

        return new AclClientRPCHook(credentials);
    }

}
