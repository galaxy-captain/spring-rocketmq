package me.galaxy.rocket.annotation;

import me.galaxy.rocket.config.ExceptionIgnore;
import org.apache.rocketmq.remoting.RPCHook;

import java.lang.annotation.*;

/**
 * @Description
 * @Author galaxy-captain
 * @Date 2019-06-21 14:11
 **/
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RocketListener {

    boolean orderly() default false;

    String nameServer() default "";

    String instance() default "";

    String consumerGroup();

    String topic() default "";

    String tag() default "";

    int maxBatchSize() default 1;

    int retryTimes() default Integer.MIN_VALUE;

    int delayTimeLevel() default Integer.MIN_VALUE;

    int suspendTimeMillis() default Integer.MIN_VALUE;

    Class<? extends Throwable>[] ignoredExceptions() default {};

    Class<? extends ExceptionIgnore>[] exceptionIgnores() default {};

    Class<? extends RPCHook>[] hook() default {};

}
