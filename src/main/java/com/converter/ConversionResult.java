package com.converter;

import java.util.ArrayList;
import java.util.List;

public class ConversionResult {
    private String xml;
    private boolean success;
    private List<String> warnings;
    private List<String> errors;

    public ConversionResult() {
        this.warnings = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public List<String> getErrors() {
        return errors;
    }

    public void addError(String error) {
        this.errors.add(error);
    }
}
