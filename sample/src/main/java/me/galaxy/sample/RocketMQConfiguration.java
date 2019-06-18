package me.galaxy.sample;

import me.galaxy.rocketmq.RocketConfiguration;
import me.galaxy.rocketmq.annotation.EnableRocket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * @Description
 * @Author galaxy-captain
 * @Date 2019-06-18 13:11
 **/
@EnableRocket
@Configuration
public class RocketMQConfiguration {

    @PostConstruct
    public void postConstruct() {
        System.out.println("post construct");
    }

    @Bean
    public RocketConfiguration rocketConfiguration() {

        RocketConfiguration rocketConfiguration = new RocketConfiguration();
        rocketConfiguration.setNameServer("127.0.0.1:9876");

        return rocketConfiguration;
    }

}
