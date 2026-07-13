package com.aianalyst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Business schema and terminology that are injected into Text-to-SQL prompts. */
@ConfigurationProperties(prefix = "metadata")
public class BusinessMetadataProperties {

    private List<Table> tables = new ArrayList<>();
    private List<String> relationships = new ArrayList<>();
    private List<BusinessTerm> businessTerms = new ArrayList<>();

    public List<Table> getTables() {
        return tables;
    }

    public void setTables(List<Table> tables) {
        this.tables = tables;
    }

    public List<String> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<String> relationships) {
        this.relationships = relationships;
    }

    public List<BusinessTerm> getBusinessTerms() {
        return businessTerms;
    }

    public void setBusinessTerms(List<BusinessTerm> businessTerms) {
        this.businessTerms = businessTerms;
    }

    public static class Table {
        private String name;
        private String description;
        private List<Column> columns = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<Column> getColumns() {
            return columns;
        }

        public void setColumns(List<Column> columns) {
            this.columns = columns;
        }
    }

    public static class Column {
        private String name;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class BusinessTerm {
        private String term;
        private String meaning;

        public String getTerm() {
            return term;
        }

        public void setTerm(String term) {
            this.term = term;
        }

        public String getMeaning() {
            return meaning;
        }

        public void setMeaning(String meaning) {
            this.meaning = meaning;
        }
    }
}
