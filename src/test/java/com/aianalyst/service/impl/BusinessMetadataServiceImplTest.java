package com.aianalyst.service.impl;

import com.aianalyst.config.BusinessMetadataProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetadataServiceImplTest {

    @Test
    void shouldBuildDeterministicPromptContextFromYamlModel() {
        BusinessMetadataProperties metadata = new BusinessMetadataProperties();
        BusinessMetadataProperties.Column id = column("id", "客户ID");
        BusinessMetadataProperties.Column name = column("customer_name", "客户名称");
        BusinessMetadataProperties.Table customer = table("biz_customer", "客户信息表", List.of(id, name));
        BusinessMetadataProperties.BusinessTerm sales = term("销售额", "已完成订单金额之和");
        metadata.setTables(List.of(customer));
        metadata.setRelationships(List.of("biz_customer.id = biz_order.customer_id"));
        metadata.setBusinessTerms(List.of(sales));

        String context = new BusinessMetadataServiceImpl(metadata).buildPromptContext();

        assertThat(context)
                .contains("biz_customer：客户信息表")
                .contains("id（客户ID）")
                .contains("customer_name（客户名称）")
                .contains("biz_customer.id = biz_order.customer_id")
                .contains("销售额：已完成订单金额之和");
    }

    @Test
    void shouldHandleMissingOptionalMetadataLists() {
        BusinessMetadataProperties metadata = new BusinessMetadataProperties();
        metadata.setTables(null);
        metadata.setRelationships(null);
        metadata.setBusinessTerms(null);

        String context = new BusinessMetadataServiceImpl(metadata).buildPromptContext();

        assertThat(context).isEqualTo("可用业务表：\n表关系：\n业务术语：\n");
    }

    private BusinessMetadataProperties.Column column(String name, String description) {
        BusinessMetadataProperties.Column column = new BusinessMetadataProperties.Column();
        column.setName(name);
        column.setDescription(description);
        return column;
    }

    private BusinessMetadataProperties.Table table(String name, String description,
                                                   List<BusinessMetadataProperties.Column> columns) {
        BusinessMetadataProperties.Table table = new BusinessMetadataProperties.Table();
        table.setName(name);
        table.setDescription(description);
        table.setColumns(columns);
        return table;
    }

    private BusinessMetadataProperties.BusinessTerm term(String name, String meaning) {
        BusinessMetadataProperties.BusinessTerm term = new BusinessMetadataProperties.BusinessTerm();
        term.setTerm(name);
        term.setMeaning(meaning);
        return term;
    }
}
