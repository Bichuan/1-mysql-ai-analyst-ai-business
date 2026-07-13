package com.aianalyst.config;

import com.aianalyst.service.BusinessMetadataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in test verifying that business-metadata.yml is loaded into prompt context. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "runMetadataIT", matches = "true")
class BusinessMetadataIntegrationTest {

    @Autowired
    private BusinessMetadataService businessMetadataService;

    @Test
    void shouldBuildContextFromBusinessMetadataYaml() {
        String context = businessMetadataService.buildPromptContext();

        assertThat(context)
                .contains("biz_customer")
                .contains("biz_order")
                .contains("biz_product")
                .contains("biz_order_item")
                .contains("销售额")
                .contains("biz_customer.id = biz_order.customer_id");
    }
}
