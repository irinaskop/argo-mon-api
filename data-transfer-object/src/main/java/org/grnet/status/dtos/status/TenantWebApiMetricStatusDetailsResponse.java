package org.grnet.status.dtos.status;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class TenantWebApiMetricStatusDetailsResponse {

    public String timestamp;
    public String group;
    public String hostname;

    @JsonProperty("service_type")
    public String serviceType;

    public String metric;
    public String status;
    public String summary;
    public String message;

    public Map<String, String> info;
}
