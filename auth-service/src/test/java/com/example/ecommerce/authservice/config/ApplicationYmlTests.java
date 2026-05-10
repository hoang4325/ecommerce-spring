package com.example.ecommerce.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ApplicationYmlTests {

    @Test
    void defaultDdlAutoIsValidateAndLocalProfileUsesUpdate() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));

        assertThat(propertySources.getFirst().getProperty("spring.jpa.hibernate.ddl-auto"))
            .isEqualTo("validate");

        assertThat(propertySources)
            .anySatisfy(propertySource -> {
                assertThat(propertySource.getProperty("spring.config.activate.on-profile")).isEqualTo("local");
                assertThat(propertySource.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
            });
    }
}
