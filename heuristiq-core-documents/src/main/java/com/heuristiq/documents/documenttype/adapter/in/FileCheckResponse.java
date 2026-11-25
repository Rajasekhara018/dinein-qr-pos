package com.heuristiq.documents.documenttype.adapter.in;

import java.util.List;

/** Verdict of "would this file be accepted?", with the rule that was applied. Nothing was stored. */
public record FileCheckResponse(boolean accepted, List<Reason> reasons, Rule rule) {

    /** One reason the file was refused. */
    public record Reason(String code, String message) {
    }

    /** The effective rule of the document type. */
    public record Rule(int maxFileSizeMb, List<String> allowedFormats, List<String> allowedExtensions) {
    }
}
