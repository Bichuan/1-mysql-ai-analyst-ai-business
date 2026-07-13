package com.aianalyst.service.impl;

import com.aianalyst.config.BusinessMetadataProperties;
import com.aianalyst.service.BusinessMetadataService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Turns the YAML business dictionary into concise, deterministic prompt context. */
@Service
public class BusinessMetadataServiceImpl implements BusinessMetadataService {

    private final BusinessMetadataProperties metadata;

    public BusinessMetadataServiceImpl(BusinessMetadataProperties metadata) {
        this.metadata = metadata;
    }

    @Override
    public String buildPromptContext() {
        StringBuilder context = new StringBuilder("可用业务表：\n");
        for (BusinessMetadataProperties.Table table : safeList(metadata.getTables())) {
            context.append("- ")
                    .append(table.getName())
                    .append("：")
                    .append(table.getDescription())
                    .append("；字段：")
                    .append(safeList(table.getColumns()).stream()
                            .map(column -> column.getName() + "（" + column.getDescription() + "）")
                            .collect(Collectors.joining("，")))
                    .append('\n');
        }

        context.append("表关系：\n");
        safeList(metadata.getRelationships()).forEach(relationship -> context.append("- ").append(relationship).append('\n'));

        context.append("业务术语：\n");
        for (BusinessMetadataProperties.BusinessTerm term : safeList(metadata.getBusinessTerms())) {
            context.append("- ")
                    .append(term.getTerm())
                    .append("：")
                    .append(term.getMeaning())
                    .append('\n');
        }
        return context.toString();
    }

    private static <T> List<T> safeList(List<T> values) {
        return Objects.requireNonNullElse(values, List.of());
    }
}
