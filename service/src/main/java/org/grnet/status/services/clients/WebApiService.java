package org.grnet.status.services.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.grnet.status.dtos.Status;
import org.grnet.status.dtos.argo.ArgoWebApiErrorResponse;
import org.grnet.status.dtos.readiness.WebApiTenantReadiness;
import org.grnet.status.dtos.report.WebApiReportResponse;
import org.grnet.status.dtos.status.*;
import org.grnet.status.dtos.tenant.node.*;
import org.grnet.status.dtos.tenant.webapi.*;
import org.grnet.status.dtos.topology.FeedTopologyDto;
import org.grnet.status.dtos.topology.WebApiFeedsTopologyResponse;
import org.grnet.status.repositories.TenantRepository;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WebApiService {

    @ConfigProperty(name = "web.api.access.token")
    String accessToken;

    @Inject
    @RestClient
    ArgoWebApiClient argoWebApiClient;

    @Inject
    TenantRepository tenantRepository;

    private static final Logger LOG = Logger.getLogger(WebApiService.class);

    public TenantWebApiGetResponse retrieveTenantWebApi(String id) {

        try {
            return argoWebApiClient.getTenant(accessToken, id);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            var errorMessage = logArgoError(e, "Retrieving Tenant", id);

            throw new WebApplicationException(
                    errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Tenant failed in Argo Web Api. tenantId=%s",
                    id
            );

            throw new WebApplicationException(
                    "Retrieving Tenant... tenant with id: " + id + " failed in Argo Web Api",
                    500
            );
        }
    }

    public void deleteTenant(String tenantId) {

        try {

            argoWebApiClient.deleteTenant(tenantId, accessToken);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Deleting Tenant", tenantId);

            throw new WebApplicationException(
                    "Deleting Tenant... failed to delete tenant with id: " + tenantId + " from Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Deleting Tenant failed in Argo Web Api. tenantId=%s",
                    tenantId
            );

            throw new WebApplicationException(
                    "Deleting Tenant... failed to delete tenant with id: " + tenantId + " from Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiCreateResponse createTenantInWebApi(TenantWebApiRequest webApiRequest) {

        try {

            return argoWebApiClient.createTenant(accessToken, webApiRequest);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Creating Tenant", webApiRequest.info.name);

            var message = e.getMessage();

            if (status == 409) {

                var optTenant = tenantRepository.fetchTenantByName(webApiRequest.info.name);

                if (optTenant.isPresent()) {

                    message = "Creating Tenant... Tenant already exists in Argo Monitoring Status with id: "
                            + optTenant.get().id;

                } else {

                    message = "Creating Tenant... Tenant exists in Argo Web Api but not in Argo Monitoring Status";
                }
            }

            throw new WebApplicationException(message, status);

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Creating Tenant failed in Argo Web Api. tenantName=%s",
                    webApiRequest.info.name
            );

            throw new WebApplicationException(
                    "Creating Tenant... failed in Argo Web Api",
                    500
            );
        }
    }

    public Status updateTenantWebApi(TenantWebApiRequest webApiRequest, String id) {

        try {

            argoWebApiClient.updateTenantInfo(id, accessToken, webApiRequest);
            argoWebApiClient.updateTenantTopology(id, accessToken, webApiRequest);

            if (webApiRequest.node != null) {
                var tenantNode = new TenantWebApiNodeRequest();
                tenantNode.node = webApiRequest.node;

                updateTenantNodeWebApi(id, tenantNode);
            }

            return argoWebApiClient.updateTenantDBConf(id, accessToken, webApiRequest);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Updating Tenant", id);

            throw new WebApplicationException(
                    "Updating Tenant... failed to update tenant with id: " + id + " in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Updating Tenant failed in Argo Web Api. tenantId=%s",
                    id
            );

            throw new WebApplicationException(
                    "Updating Tenant... failed to update tenant with id: " + id + " in Argo Web Api",
                    500
            );
        }
    }

    public WebApiNodeResponse updateTenantNodeWebApi(String tenantId,
                                                     TenantWebApiNodeRequest request) {

        LOG.info("Updating Tenant Node...");
        LOG.infof("REQUEST NODE VALUE = %s", request == null ? null : request.node);

        try {

            if (request != null && Boolean.TRUE.equals(request.node)) {


                return argoWebApiClient.setTenantNode(tenantId, accessToken);
            }
            return argoWebApiClient.unsetTenantNode(tenantId, accessToken);


        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Updating Tenant Node", tenantId);

            throw new WebApplicationException(
                    "Updating Tenant Node... failed to update tenant node for tenant with id: "
                            + tenantId + " in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Updating Tenant Node failed in Argo Web Api. tenantId=%s",
                    tenantId
            );

            throw new WebApplicationException(
                    "Updating Tenant Node... failed to update tenant node for tenant with id: "
                            + tenantId + " in Argo Web Api",
                    500
            );
        }
    }

    public WebApiTenantReadiness retrieveTenantReadinessWebApi(String id) {

        try {

            return argoWebApiClient.getTenantReadiness(id, accessToken);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Tenant Readiness", id);

            throw new WebApplicationException(
                    "Retrieving Tenant Readiness... tenant with id: " + id + " failed in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Tenant Readiness failed in Argo Web Api. tenantId=%s",
                    id
            );

            throw new WebApplicationException(
                    "Retrieving Tenant Readiness... tenant with id: " + id + " failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiGetResponse retrieveTenantsWebApi() {

        try {

            return argoWebApiClient.getTenants(accessToken);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Tenants", "all");

            throw new WebApplicationException(
                    "Retrieving Tenants... failed in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.error(e);

            throw new WebApplicationException(
                    "Retrieving Tenants... failed in Argo Web Api",
                    500
            );
        }
    }

    public void validateTenantInitialized(String tenantId, String resourceName) {

        LOG.info("Checking if Tenant is initialized...");

        var tenant = argoWebApiClient.getTenant(accessToken, tenantId);
        var dbConf = tenant.getData().get(0).getDb_conf();
        var mongodbReady = dbConf != null && !dbConf.isEmpty();

        if (!mongodbReady) {

            throw new WebApplicationException(
                    resourceName + " are not available. The tenant is still initializing.",
                    400
            );
        }
    }


    /**
     * Sets the default node report in Argo Web Api.
     *
     * @param reportId report identifier
     * @return status response
     */
    public WebApiNodeReportResponse setNodeReportWebApi(String reportId, String tenantId) {
        try {

            return argoWebApiClient.setNodeReport(reportId, accessToken, tenantId);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Updating Report", reportId);

            throw new WebApplicationException(
                    "Updating Report... failed to set node report with id: "
                            + reportId + " in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Updating Report failed in Argo Web Api. reportId=%s",
                    reportId
            );

            throw new WebApplicationException(
                    "Updating Report... failed to set node report with id: "
                            + reportId + " in Argo Web Api",
                    500
            );
        }
    }

    public WebApiNodeAvailabilityResponse retrieveNodeAvailability(String nodeName, String item, String date, String startTime, String endTime, String startDate, String endDate, String granularity) {
        try {
            if (StringUtils.isBlank(item)) {
                return argoWebApiClient.getNodeAvailabilityCapability(accessToken, nodeName, date, startTime, endTime, startDate, endDate, granularity);
            }

            return argoWebApiClient.getNodeAvailabilityCapabilityByService(
                    accessToken, nodeName, item, date, startTime, endTime, startDate, endDate, granularity);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Node Availability", nodeName);

            throw new WebApplicationException(
                    "Retrieving Node Availability... node with name " + nodeName + " failed in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Node Availability failed in Argo Web Api. nodeName=%s, item=%s",
                    nodeName,
                    item
            );

            throw new WebApplicationException(
                    "Retrieving Node Availability... node with name " + nodeName + " failed in Argo Web Api",
                    500
            );
        }
    }

    public WebApiNodeStatusResponse retrieveNodeStatus(String nodeName, String item, String startTime, String endTime, Boolean history) {
        try {
            if (StringUtils.isBlank(item)) {
                return argoWebApiClient.getNodeStatus(accessToken, nodeName, startTime, endTime, history);
            }

            return argoWebApiClient.getNodeStatusByService(accessToken, nodeName, item, startTime, endTime, history);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Node Status", nodeName);

            throw new WebApplicationException(
                    "Retrieving Node Status... node with name " + nodeName + " failed in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Node Status failed in Argo Web Api. nodeName=%s, item=%s",
                    nodeName,
                    item
            );

            throw new WebApplicationException(
                    "Retrieving Node Status... node with name " + nodeName + " failed in Argo Web Api",
                    500
            );
        }
    }

    public WebApiNodeMonitoringMetricResponse retrieveNodeMonitoringMetrics(String nodeName,String item, String startDate, String endDate, String granularity) {
        try {

            if (StringUtils.isBlank(item)) {
                return argoWebApiClient.getNodeMonitoringMetrics(accessToken, nodeName, startDate, endDate, granularity);
            }

            return argoWebApiClient.getNodeMonitoringMetricsByMetric(accessToken, nodeName, item, startDate, endDate, granularity);

           } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Node Status", nodeName);

            throw new WebApplicationException(
                    "Retrieving Node Status... node with name " + nodeName + " failed in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Node Status failed in Argo Web Api. nodeName=%s",
                    nodeName
            );

            throw new WebApplicationException(
                    "Retrieving Node Status... node with name " + nodeName + " failed in Argo Web Api",
                    500
            );
        }
    }

    public WebApiNodeMonitoringMetricResponse retrieveNodeMonitoringMetricByService(String nodeName, String serviceId, String startDate, String endDate, String granularity) {

        try {

            return argoWebApiClient.getNodeMonitoringMetricsByService(accessToken, nodeName, serviceId, startDate, endDate, granularity);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Node Monitoring Metrics", nodeName);

            throw new WebApplicationException(
                    "Retrieving Node Monitoring Metrics for service " + serviceId
                            + " on node " + nodeName + " failed in Argo Web Api", status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Node Monitoring Metrics failed in Argo Web Api. nodeName=%s, serviceId=%s",
                    nodeName,
                    serviceId
            );

            throw new WebApplicationException(
                    "Retrieving Node Monitoring Metrics for service "
                            + serviceId + " on node " + nodeName + " failed in Argo Web Api",
                    500
            );
        }
    }

    public WebApiFeedsTopologyResponse retrieveFeedTopologyWebApi(String tenantId) {
        try {

            return argoWebApiClient.getFeedTopology(accessToken, tenantId);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Feed Topology", tenantId);

            if (status == 404) {

                throw new WebApplicationException(
                        "Retrieving Feed Topology... topology feed has not been configured for tenant with id: "
                                + tenantId,
                        404
                );
            }

            throw new WebApplicationException(
                    "Retrieving Feed Topology... failed to retrieve topology feed for tenant with id: "
                            + tenantId + " from Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Feed Topology failed in Argo Web Api. tenantId=%s",
                    tenantId
            );

            throw new WebApplicationException(
                    "Retrieving Feed Topology... failed to retrieve topology feed for tenant with id: "
                            + tenantId + " from Argo Web Api",
                    500
            );
        }
    }

    public WebApiFeedsTopologyResponse updateFeedTopologyWebApi(String tenantId, FeedTopologyDto request) {
        try {

            return argoWebApiClient.updateFeedTopology(accessToken, tenantId, request);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Updating Feed Topology", tenantId);

            throw new WebApplicationException(
                    "Updating Feed Topology... failed to update topology feed for tenant with id: "
                            + tenantId + " in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Updating Feed Topology failed in Argo Web Api. tenantId=%s",
                    tenantId
            );

            throw new WebApplicationException(
                    "Updating Feed Topology... failed to update topology feed for tenant with id: "
                            + tenantId + " in Argo Web Api",
                    500
            );
        }
    }

    public WebApiNodeSummaryResponse retrieveNodeSummary(String nodeName, String item, String startDate, String endDate, String granularity) {

        try {
            return argoWebApiClient.getNodeSummaryCapability(accessToken, nodeName, item, startDate, endDate, granularity);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Node Summary", nodeName);

            throw new WebApplicationException(
                    "Retrieving Node Summary... node with name " + nodeName +
                            " and service " + item +
                            " failed in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Node Summary failed in Argo Web Api. nodeName=%s, item=%s",
                    nodeName,
                    item
            );

            throw new WebApplicationException(
                    "Retrieving Node Summary... node with name " + nodeName +
                            " and service " + item +
                            " failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiGroupResultsResponse retrieveGroupResults(String groupName, String id, String date, String period, String startTime, String endTime, String startDate, String endDate, String granularity, String report) {

        try {
            if (StringUtils.isBlank(groupName)) {
                return argoWebApiClient.getGroupResultsSuperAdmin(accessToken, id, date, period, startTime, endTime, startDate, endDate, granularity, report);
            }

            return argoWebApiClient.getGroupResultsByGroupSuperAdmin(accessToken, id, groupName, date, period, startTime, endTime, startDate, endDate, granularity, report);

        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();

            var errorMessage = logArgoError(e, "Retrieving Group Results", StringUtils.defaultIfBlank(groupName, "all"));

            throw new WebApplicationException(
                    "Retrieving Group Results... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Group Results failed in Argo Web Api. groupName=%s, report=%s", groupName, report);

            throw new WebApplicationException(
                    "Retrieving Group Results... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiGroupStatusResponse retrieveGroupStatus(String groupName, String id, String startTime, String endTime, Boolean history, String report) {

        try {
            if (StringUtils.isBlank(groupName)) {
                return argoWebApiClient.getGroupStatusSuperAdmin(accessToken, id, startTime, endTime, history, report);
            }

            return argoWebApiClient.getGroupStatusByGroupSuperAdmin(accessToken, id, groupName, startTime, endTime, history, report);

        } catch (WebApplicationException e) {
            var status = e.getResponse().getStatus();

            var errorMessage = logArgoError(e, "Retrieving Group Status", StringUtils.defaultIfBlank(groupName, "all"));

            throw new WebApplicationException(
                    "Retrieving Group Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Group Status failed in Argo Web Api. groupName=%s, report=%s", groupName, report);

            throw new WebApplicationException(
                    "Retrieving Group Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiSupergroupsResponse retrieveResultsSupergroupsByReport(String tenantId, String reportName, String startTime, String endTime, String granularity) {

        try {
            return argoWebApiClient.getSupergroupsResultsByReport(accessToken, tenantId, reportName, startTime, endTime, granularity);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Supergroups by Report", StringUtils.defaultIfBlank(reportName, "all"));

            throw new WebApplicationException(
                    "Retrieving Supergroups... " + errorMessage,
                    status
            );
        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Supergroups by Report failed in Argo Web Api. reportName=%s, groupType=%s", reportName);

            throw new WebApplicationException(
                    "Retrieving Supergroups by Report... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiSupergroupsResponse retrieveResultsSupergroupByNameByReport(String tenantId, String reportName, String supergroupName, String startTime, String endTime, String granularity) {

        try {
            return argoWebApiClient.getSupergroupByNameResultsByReport(accessToken, tenantId, reportName, supergroupName, startTime, endTime, granularity);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Supergroups by Report", StringUtils.defaultIfBlank(reportName, "all"));

            throw new WebApplicationException(
                    "Retrieving Supergroups... " + errorMessage,
                    status
            );
        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Supergroups by Report failed in Argo Web Api. reportName=%s, groupType=%s", reportName);

            throw new WebApplicationException(
                    "Retrieving Supergroups by Report... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiGroupResultsByReportResponse retrieveResultsGroupsByReport(String tenantId, String reportName, String startTime, String endTime, String granularity) {

        try {
            return argoWebApiClient.getGroupsResultsByReport(accessToken, tenantId, reportName, startTime, endTime, granularity);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Groups by Report", StringUtils.defaultIfBlank(reportName, "all"));

            throw new WebApplicationException(
                    "Retrieving Groups... " + errorMessage,
                    status
            );
        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Groups services by Report failed in Argo Web Api. reportName=%s, groupType=%s", reportName);

            throw new WebApplicationException(
                    "Retrieving Groups Services... failed in Argo Web Api",
                    500
            );
        }
    }

    //get endpoint results by group
    public TenantWebApiGroupEndpointResultsByReportResponse retrieveResultsEndpointsByReportAndGroup(String tenantId, String reportName, String groupName, String startTime, String endTime, String granularity) {

        try {
            return argoWebApiClient.getResultsEndpointsByReportAndGroup(accessToken, tenantId, reportName, groupName, startTime, endTime, granularity);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Groups by Report", StringUtils.defaultIfBlank(reportName, "all"));

            throw new WebApplicationException(
                    "Retrieving Groups... " + errorMessage,
                    status
            );
        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Groups services by Report failed in Argo Web Api. reportName=%s, groupType=%s", reportName);

            throw new WebApplicationException(
                    "Retrieving Groups Services... failed in Argo Web Api",
                    500
            );
        }
    }

    //get endpoint results by group and endpoint
    public TenantWebApiGroupEndpointResultsByReportResponse retrieveResultsEndpointsByReportGroupAndEndpoint(String tenantId, String reportName, String groupName, String endpointName, String startTime, String endTime, String granularity) {

        try {
            return argoWebApiClient.getResultsEndpointsByReportAndGroup(accessToken, tenantId, reportName, groupName, startTime, endTime, granularity);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Groups by Report", StringUtils.defaultIfBlank(reportName, "all"));

            throw new WebApplicationException(
                    "Retrieving Groups... " + errorMessage,
                    status
            );
        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Groups services by Report failed in Argo Web Api. reportName=%s, groupType=%s", reportName);

            throw new WebApplicationException(
                    "Retrieving Groups Services... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiGroupResultsByReportResponse retrieveResultsGroupByNameByReport(String tenantId, String reportName, String groupName, String startTime, String endTime, String granularity) {

        try {
            return argoWebApiClient.getGroupByNameResultsByReport(accessToken, tenantId, reportName, groupName, startTime, endTime, granularity);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Groups by Report", StringUtils.defaultIfBlank(reportName, "all"));

            throw new WebApplicationException(
                    "Retrieving Groups... " + errorMessage,
                    status
            );
        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Groups by Report failed in Argo Web Api. reportName=%s, groupName=%s", reportName, groupName);

            throw new WebApplicationException(
                    "Retrieving Groups... failed in Argo Web Api",
                    500
            );
        }
    }


    public TenantWebApiEndpointResultsByReportResponse retrieveResultsEndpointsByReport(String tenantId, String reportName, String startTime, String endTime, String granularity) {

        try {
            return argoWebApiClient.getEndpointsResultsByReport(accessToken, tenantId, reportName, startTime, endTime, granularity);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Endpoints by Report", StringUtils.defaultIfBlank(reportName, "all"));

            throw new WebApplicationException(
                    "Retrieving Endpoints... " + errorMessage,
                    status
            );
        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Endpoints by Report failed in Argo Web Api. reportName=%s", reportName);

            throw new WebApplicationException(
                    "Retrieving Endpoints... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiEndpointResultsByReportResponse retrieveResultsEndpointByNameByReport(String tenantId, String reportName, String endpointName, String startTime, String endTime, String granularity) {

        try {
            return argoWebApiClient.getEndpointByNameResultsByReport(accessToken, tenantId, reportName, endpointName, startTime, endTime, granularity);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving endpoints by Report", StringUtils.defaultIfBlank(reportName, "all"));

            throw new WebApplicationException(
                    "Retrieving Endpoints... " + errorMessage,
                    status
            );
        } catch (RuntimeException e) {
            LOG.errorf(e, "Retrieving Endpoints by Report failed in Argo Web Api. reportName=%s, endpointName=%s", reportName, endpointName);

            throw new WebApplicationException(
                    "Retrieving Endpoints... failed in Argo Web Api",
                    500
            );
        }
    }

    public WebApiReportResponse retrieveReportByIdWebApi(String reportId, String tenantId) {

        try {

            return argoWebApiClient.fetchReportByIdSuperAdmin(reportId, accessToken, tenantId);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Report", reportId);

            throw new WebApplicationException(
                    "Retrieving Report... failed to retrieve report with id: "
                            + reportId + " from Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Report failed in Argo Web Api. reportId=%s",
                    reportId
            );

            throw new WebApplicationException(
                    "Retrieving Report... failed to retrieve report with id: "
                            + reportId + " from Argo Web Api",
                    500
            );
        }
    }


    public WebApiNodeReportResponse setReportPublicWebApi(String reportId, String tenantId) {

        var report = retrieveReportByIdWebApi(reportId, tenantId);

        if (report.data.get(0).disabled) {
            throw new WebApplicationException(
                    "Report '" + report.data.get(0).info.name  + "' is inactive and cannot be made public.",
                    409
            );
        }

        try {

            return argoWebApiClient.setReportPublicSuperAdmin(reportId, accessToken, tenantId);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Updating Report Visibility", reportId);

            throw new WebApplicationException(
                    "Updating Report Visibility... failed to set report with id: "
                            + reportId + " as public in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Updating Report Visibility failed in Argo Web Api. reportId=%s",
                    reportId
            );

            throw new WebApplicationException(
                    "Updating Report Visibility... failed to set report with id: "
                            + reportId + " as public in Argo Web Api",
                    500
            );
        }
    }

    public WebApiNodeReportResponse setReportPrivateWebApi(String reportId, String tenantId) {

        try {
            return argoWebApiClient.setReportPrivateSuperAdmin(reportId, accessToken, tenantId);

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Updating Report Visibility", reportId);

            throw new WebApplicationException(
                    "Updating Report Visibility... failed to set report with id: "
                            + reportId + " as private in Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Updating Report Visibility failed in Argo Web Api. reportId=%s",
                    reportId
            );

            throw new WebApplicationException(
                    "Updating Report Visibility... failed to set report with id: "
                            + reportId + " as private in Argo Web Api",
                    500
            );
        }
    }

    public WebApiReportResponse retrieveReportsWebApi(String tenantId, Boolean publicReports, Boolean privateReports, Boolean nodeReports) {

        try {

            return argoWebApiClient.fetchReportsSuperAdmin(
                    accessToken,
                    tenantId, Boolean.TRUE.equals(publicReports) ? "" : null,
                    Boolean.TRUE.equals(privateReports) ? "" : null,
                    Boolean.TRUE.equals(nodeReports) ? "" : null
            );

        } catch (WebApplicationException e) {

            int status = e.getResponse().getStatus();

            logArgoError(e, "Retrieving Reports", tenantId);

            throw new WebApplicationException(
                    "Retrieving Reports... failed to retrieve reports for tenant with id: "
                            + tenantId + " from Argo Web Api",
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e,
                    "Retrieving Reports failed in Argo Web Api. tenantId=%s",
                    tenantId
            );

            throw new WebApplicationException(
                    "Retrieving Reports... failed to retrieve reports for tenant with id: "
                            + tenantId + " from Argo Web Api",
                    500
            );
        }
    }


    public TenantWebApiGroupStatusTimelineResponse retrieveStatusTimelineGroupsByReport(String tenantId, String reportName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getGroupsStatusTimelineByReport(accessToken, tenantId, reportName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Groups Status", "all");

            throw new WebApplicationException(
                    "Retrieving Groups Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e, "Retrieving Groups Status failed in Argo Web Api. tenantId=%s", tenantId);

            throw new WebApplicationException(
                    "Retrieving Groups Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiGroupStatusTimelineResponse retrieveStatusTimelineGroupByNameByReport(String tenantId, String reportName, String groupName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getGroupStatusTimelineByGroupNameByReport(accessToken, tenantId, reportName, groupName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Group Status", groupName);

            throw new WebApplicationException(
                    "Retrieving Group Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e, "Retrieving Group Status failed in Argo Web Api. groupName=%s", groupName);

            throw new WebApplicationException(
                    "Retrieving Group Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointsByGroup(String tenantId, String reportName, String groupName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getEndpointsStatusTimelineByGroup(accessToken, tenantId, reportName, groupName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Endpoints Status", groupName);

            throw new WebApplicationException(
                    "Retrieving Endpoints Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Endpoints Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s",
                    tenantId,
                    reportName,
                    groupName
            );

            throw new WebApplicationException(
                    "Retrieving Endpoints Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointByGroupAndName(String tenantId, String reportName, String groupName, String endpointName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getEndpointStatusTimelineByGroupAndName(accessToken, tenantId, reportName, groupName, endpointName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Endpoint Status", endpointName);

            throw new WebApplicationException(
                    "Retrieving Endpoint Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Endpoint Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, endpointName=%s",
                    tenantId,
                    reportName,
                    groupName,
                    endpointName
            );

            throw new WebApplicationException(
                    "Retrieving Endpoint Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointsByServiceType(String tenantId, String reportName, String groupName, String serviceTypeName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getEndpointsStatusTimelineByServiceType(
                    accessToken, tenantId, reportName, groupName, serviceTypeName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Endpoints Status", serviceTypeName);

            throw new WebApplicationException(
                    "Retrieving Endpoints Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Endpoints Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, serviceTypeName=%s",
                    tenantId,
                    reportName,
                    groupName,
                    serviceTypeName
            );

            throw new WebApplicationException(
                    "Retrieving Endpoints Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointByServiceTypeAndName(String tenantId, String reportName, String groupName, String serviceTypeName, String endpointName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getEndpointStatusTimelineByServiceTypeAndName(accessToken, tenantId, reportName, groupName, serviceTypeName, endpointName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Endpoint Status", endpointName);

            throw new WebApplicationException(
                    "Retrieving Endpoint Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Endpoint Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, serviceTypeName=%s, endpointName=%s",
                    tenantId,
                    reportName,
                    groupName,
                    serviceTypeName,
                    endpointName
            );

            throw new WebApplicationException(
                    "Retrieving Endpoint Status... failed in Argo Web Api",
                    500
            );
        }
    }


    private String logArgoError(WebApplicationException e, String operation, String identifier) {

        try {

            var body = e.getResponse().readEntity(String.class);
            var error = new ObjectMapper()
                    .readValue(body, ArgoWebApiErrorResponse.class);

            LOG.errorf(
                    "%s failed in Argo Web Api. identifier=%s, status=%s, argoMessage=%s",
                    operation,
                    identifier,
                    e.getResponse().getStatus(),
                    error.extractMessage()
            );

            return error.extractMessage();

        } catch (Exception ex) {

            LOG.errorf(
                    ex,
                    "Failed parsing Argo Web Api error response. operation=%s, identifier=%s, status=%s",
                    operation,
                    identifier,
                    e.getResponse().getStatus()
            );
        }
        return "Data Inconsistency: Tenant entry is present in status-api but missing from web-api.";
    }

    public TenantWebApiServiceTypeStatusTimelineResponse retrieveStatusTimelineServiceTypesByGroup(String tenantId, String reportName, String groupName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getServiceTypesStatusTimelineByGroup(accessToken, tenantId, reportName, groupName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Service Type Status", groupName);

            throw new WebApplicationException(
                    "Retrieving Service Type Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Service Type Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s",
                    tenantId,
                    reportName,
                    groupName
            );

            throw new WebApplicationException(
                    "Retrieving Service Type Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiServiceTypeStatusTimelineResponse retrieveStatusTimelineServiceTypeByName(String tenantId, String reportName, String groupName, String serviceTypeName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getServiceTypeStatusTimelineByName(accessToken, tenantId, reportName, groupName, serviceTypeName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Service Type Status", serviceTypeName);

            throw new WebApplicationException(
                    "Retrieving Service Type Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Service Type Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, serviceTypeName=%s",
                    tenantId,
                    reportName,
                    groupName,
                    serviceTypeName
            );

            throw new WebApplicationException(
                    "Retrieving Service Type Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiMetricStatusTimelineResponse retrieveStatusTimelineMetricsByEndpoint(String tenantId, String reportName, String groupName, String endpointName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getMetricsStatusTimelineByEndpoint(
                    accessToken, tenantId, reportName, groupName, endpointName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Metrics Status", endpointName);

            throw new WebApplicationException(
                    "Retrieving Metrics Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Metrics Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, endpointName=%s",
                    tenantId, reportName, groupName, endpointName
            );

            throw new WebApplicationException(
                    "Retrieving Metrics Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiMetricStatusTimelineResponse retrieveStatusTimelineMetricByEndpointAndName(String tenantId, String reportName, String groupName, String endpointName, String metricName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getMetricStatusTimelineByEndpointAndName(
                    accessToken, tenantId, reportName, groupName, endpointName, metricName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Metric Status", metricName);

            throw new WebApplicationException(
                    "Retrieving Metric Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Metric Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, endpointName=%s, metricName=%s",
                    tenantId, reportName, groupName, endpointName, metricName
            );

            throw new WebApplicationException(
                    "Retrieving Metric Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiMetricStatusTimelineResponse retrieveStatusTimelineMetricsByServiceTypeAndEndpoint(String tenantId, String reportName, String groupName, String serviceTypeName, String endpointName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getMetricsStatusTimelineByServiceTypeAndEndpoint(
                    accessToken, tenantId, reportName, groupName, serviceTypeName, endpointName, startTime, endTime
            );

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Metrics Status", endpointName);

            throw new WebApplicationException(
                    "Retrieving Metrics Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Metrics Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, serviceTypeName=%s, endpointName=%s",
                    tenantId, reportName, groupName, serviceTypeName, endpointName
            );

            throw new WebApplicationException(
                    "Retrieving Metrics Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiMetricStatusTimelineResponse retrieveStatusTimelineMetricByServiceTypeEndpointAndName(String tenantId, String reportName, String groupName, String serviceTypeName, String endpointName, String metricName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getMetricStatusTimelineByServiceTypeEndpointAndName(
                    accessToken, tenantId, reportName, groupName, serviceTypeName, endpointName, metricName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Metric Status", metricName);

            throw new WebApplicationException(
                    "Retrieving Metric Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Metric Status failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, serviceTypeName=%s, endpointName=%s, metricName=%s",
                    tenantId, reportName, groupName, serviceTypeName, endpointName, metricName
            );

            throw new WebApplicationException(
                    "Retrieving Metric Status... failed in Argo Web Api",
                    500
            );
        }

    }

    public TenantWebApiMetricStatusDetailsResponse retrieveStatusMetricDetailsByServiceTypeEndpointAndName(String tenantId, String reportName, String groupName, String serviceTypeName, String endpointName, String metricName, String timestamp) {

        try {
            return argoWebApiClient.getMetricStatusDetailsByServiceTypeEndpointAndName(accessToken, tenantId, reportName, groupName, serviceTypeName, endpointName, metricName, timestamp);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Metric Status Details", metricName);

            throw new WebApplicationException(
                    "Retrieving Metric Status Details... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(
                    e,
                    "Retrieving Metric Status Details failed in Argo Web Api. tenantId=%s, reportName=%s, groupName=%s, serviceTypeName=%s, endpointName=%s, metricName=%s, timestamp=%s",
                    tenantId, reportName, groupName, serviceTypeName, endpointName, metricName, timestamp
            );

            throw new WebApplicationException(
                    "Retrieving Metric Status Details... failed in Argo Web Api",
                    500
            );
        }
    }


    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointByReport(String tenantId, String reportName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getEndpointsStatusTimelineByReport(accessToken, tenantId, reportName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Endpoint Status", "all");

            throw new WebApplicationException(
                    "Retrieving Endpoint Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e, "Retrieving Endpoint Status failed in Argo Web Api. tenantId=%s", tenantId);

            throw new WebApplicationException(
                    "Retrieving Endpoint Status... failed in Argo Web Api",
                    500
            );
        }
    }

    public TenantWebApiEndpointStatusTimelineResponse retrieveStatusTimelineEndpointByNameByReport(String tenantId, String reportName, String endpointName, String startTime, String endTime) {

        try {
            return argoWebApiClient.getEndpointsStatusTimelineByEndpointNameByReport(accessToken, tenantId, reportName, endpointName, startTime, endTime);

        } catch (WebApplicationException e) {

            var status = e.getResponse().getStatus();
            var errorMessage = logArgoError(e, "Retrieving Endpoint Status", endpointName);

            throw new WebApplicationException(
                    "Retrieving Endpoint Status... " + errorMessage,
                    status
            );

        } catch (RuntimeException e) {

            LOG.errorf(e, "Retrieving Endpoint Status failed in Argo Web Api. endpointName=%s", endpointName);

            throw new WebApplicationException(
                    "Retrieving Endpoint Status... failed in Argo Web Api",
                    500
            );
        }
    }
}



