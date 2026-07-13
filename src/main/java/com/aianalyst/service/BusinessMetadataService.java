package com.aianalyst.service;

/** Builds the schema and business-term context used by Text-to-SQL prompts. */
public interface BusinessMetadataService {

    String buildPromptContext();
}
