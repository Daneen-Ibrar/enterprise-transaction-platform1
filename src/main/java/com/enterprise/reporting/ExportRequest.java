package com.enterprise.reporting;

import java.util.List;

public class ExportRequest {
    private String format; // csv, json, xml, excel, pdf
    private List<String> fields; // list of column names to include
    private String startDate;
    private String endDate;

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
}