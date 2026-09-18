package br.car.dsp_batch.kpi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KpisProperties.class)
public class KpisConfiguration {
}
