package org.grnet.status.services;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.grnet.status.dtos.argo.ArgoStatusGroupsResponse;
import org.grnet.status.dtos.status.*;
import org.grnet.status.dtos.statuspage.StatusPageConfigDto;
import org.grnet.status.dtos.statuspage.StatusPageConfigResponse;
import org.grnet.status.mappers.StatusPageMapper;
import org.grnet.status.repositories.StatusPageRepository;
import org.grnet.status.services.clients.ArgoWebApiClient;
import org.grnet.status.services.clients.WebApiService;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for retrieving status groups and status page configuration.
 */
@ApplicationScoped
public class StatusService {

    @Inject
    ReportService reportService;

    @Inject
    @RestClient
    ArgoWebApiClient argoWebApiClient;

    @Inject
    StatusPageRepository statusPageRepository;

    @Inject
    WebApiService webApiService;

    @ConfigProperty(name = "web.api.access.token")
    String accessToken;

    /**
     * Retrieves status groups for the given tenant and report.
     *
     * @param tenantId tenant identifier
     * @param reportId report identifier
     * @return list of status groups
     */
    public List<StatusGroupResponseDto> getStatusGroups(String tenantId, String reportId) {

        //FullReportResponseDto report=null;
        webApiService.validateTenantInitialized(tenantId, "Status Groups");

        var report = reportService.fetchReportById(tenantId, reportId);

        ArgoStatusGroupsResponse argoGroups = null;
        var list = new ArrayList<StatusGroupResponseDto>();

        try {
            argoGroups = argoWebApiClient.fetchStatusGroupsSuperAdmin(accessToken, tenantId, report.info.name);
        } catch (WebApplicationException e) {
            Log.error("Argo Web Api returned HTTP error: {}", e.getResponse().getStatus(), e);
            throw new NotFoundException(
                    "Fetching Report Groups... No groups retrieved from Argo Web Api for report: " + report.info.name
            );
        } catch (ProcessingException e) {
            Log.error("Argo Web Api is unreachable", e);
            throw new RuntimeException("Fetching Report Groups... Argo Web Api is unreachable", e);
        }

        if (argoGroups != null && argoGroups.groups != null) {
            for (var group : argoGroups.groups) {
                var dto = new StatusGroupResponseDto();
                dto.name = group.name;

                if (group.statuses != null && !group.statuses.isEmpty()) {
                    dto.status = group.statuses.get(group.statuses.size() - 1).value;
                }
                list.add(dto);
            }
        }

        return list;
    }


    /**
     * Retrieves the status page configuration by slug with updated live group statuses.
     *
     * @param slug status page slug
     * @return status page configuration
     */
    @Transactional
    public StatusPageConfigResponse getConfigBySlug(String slug) {

        var statusPage = statusPageRepository.find("slug", slug)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Status page not found for slug: " + slug));

        var statusPageDto = StatusPageMapper.INSTANCE.entityToDto(statusPage);
        var config = statusPageDto.config;
        var configResponse = StatusPageMapper.INSTANCE.configToResponse(config);

        var tenantName = statusPage.getTenant().getName();
        var tenantImage = statusPage.getTenant().getImage();

        webApiService.validateTenantInitialized(statusPage.getTenant().id, "Status Groups");

        ArgoStatusGroupsResponse argoGroups = null;
        try {
            argoGroups = argoWebApiClient.fetchStatusGroupsSuperAdmin(
                    accessToken,
                    statusPage.getTenant().id,
                    statusPage.getReport()
            );
        } catch (WebApplicationException e) {
            Log.errorf(e, "Argo Web API returned HTTP error while fetching status groups. Status: %s", e.getResponse().getStatus());
            throw new NotFoundException(
                    "Fetching Status Groups... No status groups retrieved from Argo Web API for report: " + statusPage.getReport()
            );
        } catch (ProcessingException e) {
            Log.error("Argo Web API is unreachable", e);
            throw new RuntimeException("Fetching Status Groups... Argo Web API is unreachable", e);
        }

        var liveGroups = requireGroups(argoGroups, statusPage.getReport());

        if (configResponse.groups == null) {
            return configResponse;
        }

        for (var group : configResponse.groups) {
            if (group == null || group.list == null) {
                continue;
            }

            for (var item : group.list) {
                if (item == null || item.name == null) {
                    continue;
                }

                liveGroups.stream()
                        .filter(live -> live != null && item.name.equals(live.name))
                        .findFirst()
                        .ifPresent(live -> {
                            if (live.statuses != null && !live.statuses.isEmpty()) {
                                var last = live.statuses.get(live.statuses.size() - 1);
                                if (last != null && last.value != null) {
                                    item.status = last.value;
                                }
                            }
                        });
            }
        }
        configResponse.tenantName = tenantName;
        configResponse.tenantImage = tenantImage;
        return configResponse;
    }


    private List<ArgoStatusGroupsResponse.Group> requireGroups(ArgoStatusGroupsResponse argoGroups, String report) {

        if (argoGroups == null || argoGroups.groups == null || argoGroups.groups.isEmpty()) {
            throw new NotFoundException(
                    "Fetching Status Groups... No status groups found in Argo Web API for report: " + report
            );
        }

        return argoGroups.groups;
    }

    public TenantWebApiGroupStatusTimelineResponse retrieveStatusTimelineGroupsByReport(String tenantId, String reportName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineGroupsByReport(tenantId, reportName, startTime, endTime);
    }

    public TenantWebApiGroupStatusTimelineResponse retrieveStatusTimelineGroupByNameByReport(String tenantId, String reportName, String groupName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineGroupByNameByReport(tenantId, reportName, groupName, startTime, endTime);
    }

    public TenantWebApiServiceTypeStatusTimelineResponse retrieveStatusTimelineServiceTypesByGroup(String tenantId, String reportName, String groupName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineServiceTypesByGroup(tenantId, reportName, groupName, startTime, endTime);
    }

    public TenantWebApiServiceTypeStatusTimelineResponse retrieveStatusTimelineServiceTypeByName(String tenantId, String reportName, String groupName, String serviceTypeName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineServiceTypeByName(tenantId, reportName, groupName, serviceTypeName, startTime, endTime);
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointsByGroup(String tenantId, String reportName, String groupName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineEndpointsByGroup(tenantId, reportName, groupName, startTime, endTime);
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointByGroupAndName(String tenantId, String reportName, String groupName, String endpointName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineEndpointByGroupAndName(tenantId, reportName, groupName, endpointName, startTime, endTime);
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointsByServiceType(String tenantId, String reportName, String groupName, String serviceTypeName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineEndpointsByServiceType(tenantId, reportName, groupName, serviceTypeName, startTime, endTime);
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointByServiceTypeAndName(String tenantId, String reportName, String groupName, String serviceTypeName, String endpointName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineEndpointByServiceTypeAndName(tenantId, reportName, groupName, serviceTypeName, endpointName, startTime, endTime);
    }

    public TenantWebApiMetricStatusTimelineResponse retrieveStatusTimelineMetricsByEndpoint(String tenantId, String reportName, String groupName, String endpointName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineMetricsByEndpoint(tenantId, reportName, groupName, endpointName, startTime, endTime);
    }

    public TenantWebApiMetricStatusTimelineResponse retrieveStatusTimelineMetricByEndpointAndName(String tenantId, String reportName, String groupName, String endpointName, String metricName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineMetricByEndpointAndName(tenantId, reportName, groupName, endpointName, metricName, startTime, endTime);
    }

    public TenantWebApiMetricStatusTimelineResponse retrieveStatusTimelineMetricsByServiceTypeAndEndpoint(String tenantId, String reportName, String groupName, String serviceTypeName, String endpointName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineMetricsByServiceTypeAndEndpoint(tenantId, reportName, groupName, serviceTypeName, endpointName, startTime, endTime);
    }

    public TenantWebApiMetricStatusTimelineResponse retrieveStatusTimelineMetricByServiceTypeEndpointAndName(String tenantId, String reportName, String groupName, String serviceTypeName, String endpointName, String metricName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineMetricByServiceTypeEndpointAndName(tenantId, reportName, groupName, serviceTypeName, endpointName, metricName, startTime, endTime);
    }

    public TenantWebApiMetricStatusDetailsResponse retrieveStatusMetricDetailsByServiceTypeEndpointAndName(String tenantId, String reportName, String groupName, String serviceTypeName, String endpointName, String metricName, String timestamp) {

        return webApiService.retrieveStatusMetricDetailsByServiceTypeEndpointAndName(tenantId, reportName, groupName, serviceTypeName, endpointName, metricName, timestamp);
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointsByReport(String tenantId, String reportName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineEndpointByReport(tenantId, reportName, startTime, endTime);
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointByNameByReport(String tenantId, String reportName, String endpointName, String startTime, String endTime) {

        return webApiService.retrieveStatusTimelineEndpointByNameByReport(tenantId, reportName, endpointName, startTime, endTime);
    }


}