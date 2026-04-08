package org.grnet.status.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.grnet.status.authorizations.dtos.GroupUserResponse;
import org.grnet.status.authorizations.groups.AuthGroupManagement;
import org.grnet.status.authorizations.groups.GroupManagement;
import org.grnet.status.authorizations.service.AccessControlService;
import org.grnet.status.authorizations.service.AuthGroupSetupService;
import org.grnet.status.dtos.ams.PublishRequest;
import org.grnet.status.dtos.pagination.PageResource;
import org.grnet.status.dtos.readiness.WebApiTenantReadiness;
import org.grnet.status.dtos.tenant.ContactDto;
import org.grnet.status.dtos.tenant.TenantRequestDto;
import org.grnet.status.dtos.tenant.TenantResponseDto;
import org.grnet.status.dtos.tenant.alerts.AlertDefinitionRequest;
import org.grnet.status.dtos.tenant.node.WebApiNodeAvailabilityResponse;
import org.grnet.status.dtos.tenant.node.WebApiNodeResponse;
import org.grnet.status.dtos.tenant.metadata.InstanceDto;
import org.grnet.status.dtos.tenant.metadata.TenantMetadata;
import org.grnet.status.dtos.tenant.node.WebApiNodeStatusResponse;
import org.grnet.status.dtos.tenant.status.EventStatusDto;
import org.grnet.status.dtos.tenant.status.TenantStatusDto;
import org.grnet.status.dtos.tenant.status.TenantStatusFullResponse;
import org.grnet.status.dtos.tenant.webapi.TenantWebApiNodeRequest;
import org.grnet.status.dtos.tenant.webapi.TenantWebApiGetResponse;
import org.grnet.status.entities.Contact;
import org.grnet.status.entities.Page;
import org.grnet.status.entities.PageQueryImpl;
import org.grnet.status.entities.Tenant;
import org.grnet.status.enums.*;
import org.grnet.status.exceptions.CustomRuntimeException;
import org.grnet.status.mappers.TenantMapper;
import org.grnet.status.repositories.ContactRepository;
import org.grnet.status.repositories.TenantRepository;
import org.grnet.status.services.clients.AmsService;
import org.grnet.status.services.clients.WebApiService;
import org.grnet.status.services.utils.ImageUploadUtil;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service responsible for managing tenants and tenant status workflows.
 */
@ApplicationScoped
public class TenantService {

    @Inject
    TenantRepository tenantRepository;
    @Inject
    ContactRepository contactRepository;
    @Inject
    AuthGroupSetupService authGroupSetupService;

    @Inject
    AccessControlService accessControlService;

    @Inject
    WebApiService webApiService;

    @Inject
    AuthGroupManagement groupManagement;

    @Inject
    GroupManagementService groupManagementService;

    @ConfigProperty(name = "api.auth.entitlements.parent.group")
    String namespace;

    @Inject
    ImageUploadUtil imageUploadUtil;

    @ConfigProperty(name = "base.upload.logo.dir")
    String baseUploadTenantsImagesDir;

    @ConfigProperty(name = "api.server.url")
    String apiServerUrl;

    @Inject
    AmsService amsService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2); // Adjust as needed

    /**
     * Creates a tenant and initializes its group.
     *
     * @param request tenant request
     * @param userId  user identifier
     * @return created tenant response
     * @throws IOException when image handling fails
     */
    public TenantResponseDto create(TenantRequestDto request, String userId) throws IOException {

        var response = createTenant(request, userId);

        try {
            Map<String, List<String>> attributes = new HashMap<>();
            attributes.put("tenantId", List.of(response.id));
            attributes.put("description", List.of(request.info.description));

            var parentPath = "/" + namespace + "/tenants";

            authGroupSetupService.createGroup(parentPath, response.info.name, List.of("admin", "viewer"), attributes);

        } catch (Exception ex) {
            Log.error("Failed to create AGM group for tenant " + response.id + ": " + ex.getMessage());
        }

        return response;
    }

    /**
     * Creates a tenant in Argo Web API and stores it locally.
     *
     * @param request tenant request
     * @param userId  user identifier
     * @return created tenant response
     * @throws IOException when image handling fails
     */
    @Transactional
    public TenantResponseDto createTenant(TenantRequestDto request, String userId) throws IOException {

        var existTenantOpt = tenantRepository.fetchTenantByName(request.info.name);
        if (existTenantOpt.isPresent()) {
            var message = "Creating Tenant... " + "Tenant: " + existTenantOpt.get().name + " already exists in ARGO Status Pages with id: " + existTenantOpt.get().id;
            throw new CustomRuntimeException(409, message, new HashSet<>());
        }
        handleImage(request);

        var tenant = TenantMapper.INSTANCE.dtoToTenant(request.info);
        boolean tenantCreatedRemotely = false;
        String remoteTenantId = null;

        var webApiRequest = TenantMapper.INSTANCE.toWebApiRequest(request);
        var webApiCreateResponse = webApiService.createTenantInWebApi(webApiRequest);
        remoteTenantId = webApiCreateResponse.getData().getId();
        tenantCreatedRemotely = true;
        var status = TenantMapper.INSTANCE.mapStatusToString(setDefaultStatus());
        tenant.setStatus(status);
        tenant.setNode(Boolean.TRUE.equals(request.node) ? true : null);

        try {
            TenantMapper.INSTANCE.mapMetadata(request, tenant);
            writeInDB(request, tenant, remoteTenantId, userId);
            sendNotifications(tenant);
            //   var  tenantWithStatus=tenantRepository.findById(tenant.id);
            return TenantMapper.INSTANCE.tenantToDto(tenant);
        } catch (Exception e) {
            // If tenant was created remotely, but something failed locally, rollback remote creation
            if (tenantCreatedRemotely && remoteTenantId != null) {
                webApiService.deleteTenant(remoteTenantId);
            }

            Log.error(e.getMessage(), e);
            if (e instanceof WebApplicationException) {
                throw new WebApplicationException("Creating Tenant... Failed to create tenant in Argo Web Api", ((WebApplicationException) e).getResponse().getStatus());
            }

            throw new RuntimeException("Creating Tenant... Failed to create tenant");
        }
    }

    /**
     * Retrieves a tenant by its identifier.
     *
     * @param id tenant identifier
     * @return tenant response
     */
    public TenantResponseDto getTenantById(String id) {

        var tenant = tenantRepository.findById(id);

        if (tenant == null) {
            throw new WebApplicationException("Retrieving Tenant... Tenant with id: " + id + " not found", 404);
        }

        try {
            var webapiGetResponse = webApiService.retrieveTenantWebApi(tenant.id);

            var webtenant = TenantMapper.INSTANCE
                    .webApiTenantToDto(tenant, webapiGetResponse);

            webtenant.contacts =
                    TenantMapper.INSTANCE.contactsToDtos(tenant.getContacts());

            webtenant.groupStatus = getGroupStatus(tenant);

            return webtenant;

        } catch (JsonProcessingException e) {

            Log.error("JSON error while retrieving tenant {}", id, e);

            throw new WebApplicationException(
                    "Retrieving Tenant... Failed to retrieve tenant with id: " + id + " due to invalid response from Argo Web Api",
                    502   // Bad Gateway (external system issue)
            );
        }
    }

    /**
     * Deletes a tenant by its identifier and queues group deletion.
     *
     * @param id tenant identifier
     */
    public void deleteTenantById(String id) {

        var tenant = tenantRepository.findById(id);
        if (tenant == null) {
            throw new WebApplicationException("Deleting Tenant.. Tenant not found: " + id, 404);
        }

        var tenantName = tenant.name;
        deleteTenant(tenant.getId());

        try {
            var parentPath = "/" + namespace + "/tenants/";
            var groupPath = parentPath + tenantName;

            authGroupSetupService.deleteGroup(groupPath);

        } catch (Exception ex) {
            Log.error("Deleting Tenant... Failed to queue async AGM group deletion for tenant " + id + ": " + ex.getMessage());
        }
    }

    /**
     * Deletes a tenant by its identifier.
     *
     * @param id tenant identifier
     */
    @Transactional
    public void deleteTenant(String id) {

        var tenant = tenantRepository.findById(id);

        if (tenant == null) {
            throw new WebApplicationException("Deleting Tenant... Tenant not found", 404);
        }

        try {
            Set<Contact> oldContacts = new HashSet<>(tenant.getContacts());

            // 1. Remove relation from both sides
            for (Contact c : oldContacts) {
                c.getTenants().remove(tenant);
            }
            tenant.getContacts().clear();
            tenantRepository.persist(tenant);

            // 2. Delete tenant
            tenantRepository.delete(tenant);

            // 3. External cleanups
            imageUploadUtil.deleteImageIfExists(baseUploadTenantsImagesDir, tenant.name);
            webApiService.deleteTenant(id);

            // 4. Delete orphan contacts
            deleteOrphanContacts(oldContacts);

        } catch (WebApplicationException e) {

            Log.error("Argo Web Api error while deleting tenant {}", id, e);

            throw new WebApplicationException(
                    "Deleting Tenant... Failed to delete tenant from Argo Web Api",
                    e.getResponse().getStatus()
            );
        } catch (JsonProcessingException e) {

            Log.error("JSON processing error while deleting tenant {}", id, e);

            throw new WebApplicationException(
                    "Deleting Tenant.. Internal error while processing tenant data.",
                    500
            );
        } catch (Exception e) {

            Log.error("Unexpected error while deleting tenant {}", id, e);

            throw new WebApplicationException(
                    "Deleting Tenant... Unexpected error occurred while deleting tenant.",
                    500
            );
        }
    }

    /**
     * Deletes all tenants.
     */
    @Transactional
    public void deleteAll() {

        var tenants = tenantRepository.fetchTenants();

        tenants.forEach(t -> {
            try {

                var id = t.id;// 1. Delete from DB first (inside transaction)
                Set<Contact> oldContacts = new HashSet<>(t.getContacts());

                tenantRepository.delete(t);
                oldContacts.stream().forEach(c -> {
                    contactRepository.delete(c);
                });

                imageUploadUtil.deleteImageIfExists(baseUploadTenantsImagesDir, t.name);
                try {
                    webApiService.deleteTenant(id);
                } catch (JsonProcessingException e) {
                    Log.error(e.getMessage(), e);
                    throw new RuntimeException("Deleting Tenant... Failed to delete tenant with id: " + t.id + " from Argo Web Api");
                }

            } catch (RuntimeException e) {

                // If DB delete fails -> API delete is NOT executed, as desired

                int status = 500;
                if (e instanceof WebApplicationException) {
                    status = ((WebApplicationException) e).getResponse().getStatus();
                }

                Log.error(e.getMessage(), e);
                Log.error("ERROR deleting tenant with id: " + t.id + " Received status is: " + status);
            }
        });
    }

    /**
     * Updates an existing tenant by its identifier.
     *
     * @param id      tenant identifier
     * @param request tenant update request
     * @return updated tenant response
     * @throws IOException when image handling fails
     */
    @Transactional
    public TenantResponseDto updateTenant(String id, @Valid TenantRequestDto request) throws IOException {
        handleImage(request);

        // ------------------------------
        // 1. Get previous remote state (for rollback)
        // ------------------------------

        var previousWebApiTenant = webApiService.retrieveTenantWebApi(id);

        var previousData = previousWebApiTenant.getData().get(0);

        if (request.info != null && request.info.name != null &&
                !Objects.equals(previousData.getInfo().getName(), request.info.name)) {
            throw new WebApplicationException("Updating Tenant.. Tenant name cannot be changed", 409);
        }

        var previousRemoteState = TenantMapper.INSTANCE.webApiTenantToTenantRequestDto(
                previousWebApiTenant.getData().get(0).getInfo()
        );
        // ------------------------------
        // 2. Update remote API first
        // ------------------------------

        // create an initial webApiRequest with the existing web api data for the tenant
        var webApiRequest = TenantMapper.INSTANCE.dataToTenantWebApiRequest(previousWebApiTenant.getData().get(0));
        //update the initial webApiRequest with the new data for info and topology while keeping users and dbConf as it is
        TenantMapper.INSTANCE.updateExistingWebApiRequest(request, webApiRequest);

        //updates the tenant in the webApi
        webApiService.updateTenantWebApi(webApiRequest, id);
        // ------------------------------
        // 3. Local DB update
        // ------------------------------
        var tenant = tenantRepository.findById(id);

        // Keep copy of old contacts for orphan check
        Set<Contact> oldContacts = new HashSet<>(tenant.getContacts());

        try {
            updateTenantInDB(request, tenant);
            // ------------------------------
            // 5. Delete orphan contacts (contact with 0 tenants)
            // ------------------------------
            deleteOrphanContacts(oldContacts);

        } catch (Exception dbException) {

            try {
                var webApiRequestPreviousState = TenantMapper.INSTANCE.toWebApiRequest(previousRemoteState);
                webApiService.updateTenantWebApi(webApiRequestPreviousState, id);
            } catch (Exception rollbackEx) {
                throw new WebApplicationException(
                        "Updating Tenant... DB update failed AND remote rollback failed: " + rollbackEx.getMessage(),
                        500
                );
            }
            throw new RuntimeException("Updating Tenant... DB update failed: " + dbException.getMessage());
        }

        return TenantMapper.INSTANCE.tenantToDto(tenant);
    }

    /**
     * Retrieves a paginated list of tenants with optional search and sorting.
     *
     * @param page    0-based page index
     * @param size    page size
     * @param uriInfo request context for pagination links
     * @param search  search filter
     * @param sort    sort field
     * @param order   sort order
     * @return paginated list of tenants
     */
    public PageResource<TenantResponseDto> getTenantsByPageAndSize(int page, int size, UriInfo uriInfo, String search, String sort, String order) {

        ArrayList<TenantResponseDto> tenantList = new ArrayList<>();
        var tenants = tenantRepository.fetchTenantsByPageAndSize(page, size, search, sort, order);
        tenants.list().stream().forEach(t -> {
            TenantResponseDto webtenant = null;
            try {
                var webTenantGetResponse = webApiService.retrieveTenantWebApi(t.id);
                webtenant = TenantMapper.INSTANCE.webApiTenantToDto(t, webTenantGetResponse);
                webtenant.groupStatus = getGroupStatus(t);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            tenantList.add(webtenant);
        });
        return new PageResource<>(tenants, tenantList, uriInfo);
    }


    /**
     * Retrieves a paginated list of tenants accessible to the authenticated user.
     *
     * @param page    0-based page index
     * @param size    page size
     * @param uriInfo request context for pagination links
     * @param search  search filter
     * @param sort    sort field
     * @param order   sort order
     * @return paginated list of tenants
     */
    public PageResource<TenantResponseDto> listAuthorizedTenants(int page, int size, UriInfo uriInfo, String search, String sort, String order) {

        if (accessControlService.isSuperAdmin()) {
            return getTenantsByPageAndSize(page, size, uriInfo, search, sort, order);
        }

        var allowedTenantIds = accessControlService.resolveAccessibleGroupsByName("tenants");

        var tenants = tenantRepository.fetchTenantsByIdsAndPageAndSize(allowedTenantIds, page, size, search, sort, order);

        var tenantList = new ArrayList<TenantResponseDto>();

        tenants.list().forEach(t -> {
            TenantResponseDto webtenant = null;
            try {
                var webTenantGetResponse = webApiService.retrieveTenantWebApi(t.id);
                webtenant = TenantMapper.INSTANCE.webApiTenantToDto(t, webTenantGetResponse);
                webtenant.groupStatus = getGroupStatus(t);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            tenantList.add(webtenant);
        });
        return new PageResource<>(tenants, tenantList, uriInfo);
    }

    /**
     * Validates and stores the tenant logo image and updates the request with the resolved URL.
     *
     * @param request tenant request
     */
    private void handleImage(TenantRequestDto request) {

        var image = request.info.image;
        if (image != null && image.startsWith("data:image/")) {

            imageUploadUtil.validateBase64Image(image);
            var savedPath = imageUploadUtil.saveBase64Image(baseUploadTenantsImagesDir, image, request.info.name, "/logos/");
            request.info.image = apiServerUrl + savedPath;
        }
    }

    /**
     * Resolves contacts from the request by reusing existing records or creating new ones.
     *
     * @param request tenant request
     * @return merged contact set
     */
    private Set<Contact> resolveAndMergeContacts(TenantRequestDto request) {

        Set<Contact> result = new HashSet<>();

        if (request.contacts != null) {
            for (ContactDto dto : request.contacts) {
                // Try to find existing contact in DB by unique fields
                try {
                    Optional<Contact> existing = contactRepository.fetchContactByInfo(dto.name, dto.email, dto.type);
                    if (existing.isPresent()) {
                        result.add(existing.get());
                    } else {
                        // Create new contact (MapStruct mapping)
                        Contact newContact = new Contact();
                        newContact.setContactName(dto.name);
                        newContact.setContactEmail(dto.email);
                        newContact.setContactType(ContactType.valueOf(dto.type));
                        contactRepository.persist(newContact);
                        result.add(newContact);
                    }
                } catch (RuntimeException e) {
                    throw e; // simply rethrow original exception with full stack trace
                }
            }
        }
        return result;
    }

    /**
     * Deletes contacts that are no longer linked to any tenant.
     *
     * @param oldContacts previous tenant contacts
     */
    private void deleteOrphanContacts(Set<Contact> oldContacts) {
        for (Contact contact : oldContacts) {
            if (contact.getTenants() == null || contact.getTenants().isEmpty()) {
                contactRepository.delete(contact);
            }
        }
    }

    //construct and stores a tenant in the database

    /**
     * Persists the tenant and its contacts in the database using the remote tenant identifier.
     *
     * @param request        tenant request
     * @param tenant         tenant entity
     * @param remoteTenantId remote tenant identifier
     * @param userId         user identifier
     * @return persisted tenant entity
     */
    private Tenant writeInDB(TenantRequestDto request,
                             Tenant tenant,
                             String remoteTenantId,
                             String userId) {

        tenant.id = remoteTenantId;
        tenant.updatedBy = userId;

        Set<Contact> contacts = resolveAndMergeContacts(request);
        tenant.setContacts(new HashSet<>(contacts));

        try {
            tenantRepository.persist(tenant);
            return tenant;

        } catch (PersistenceException e) {
            Log.error("Database error while saving tenant {}", remoteTenantId, e);
            throw new WebApplicationException(
                    "Saving in database... " + "Failed to save tenant due to database error.",
                    500
            );
        } catch (Exception e) {
            Log.error("Unexpected error while saving tenant {}", remoteTenantId, e);
            throw new WebApplicationException(
                    "Saving in database... " + "Unexpected error occurred while saving tenant.",
                    500
            );
        }
    }

    //updates the tenant in the database

    /**
     * Updates an existing tenant and its contacts in the database.
     *
     * @param request tenant request
     * @param tenant  tenant entity
     */
    private void updateTenantInDB(TenantRequestDto request, Tenant tenant) {
        // Update simple fields:
        TenantMapper.INSTANCE.updateToTenant(request, tenant);
        TenantMapper.INSTANCE.mapMetadata(request, tenant);

        // ------------------------------
        // 4. Update tenant.contacts
        // ------------------------------
        var updatedContacts = resolveAndMergeContacts(request);
        tenant.setContacts(updatedContacts);

        // Replace tenant.contacts with new set
        tenant.setContacts(updatedContacts);

        TenantMapper.INSTANCE.mapMetadata(request, tenant);
        tenant.setNode(Boolean.TRUE.equals(request.node) ? Boolean.TRUE : null);
        tenantRepository.persist(tenant);
        tenantRepository.flush(); // force errors
    }

    /**
     * Updates the stored tenant status JSON in the database.
     *
     * @param tenant            tenant entity
     * @param updatedStatusJson updated status JSON
     */
    private void updateTenantStatusInDb(Tenant tenant, String updatedStatusJson) {
        // Update simple fields:

        tenant.setStatus(updatedStatusJson);
        tenantRepository.persist(tenant);
        tenantRepository.flush(); // force errors
    }

    /**
     * Updates manual tenant jobs for the specified tenant.
     *
     * @param id      tenant identifier
     * @param request tenant status request
     * @return tenant status response
     */
    @Transactional
    public TenantStatusFullResponse updateTenantManualJobs(String id, @Valid TenantStatusDto request) {
        validateJobsMode(request.jobs, EventMode.MANUAL);

        if (request.jobs != null) {
            request.jobs.forEach(this::applyJobDefinition);
        }

        return updateTenantJobsInternal(id, request);
    }

    /**
     * Updates automatic tenant jobs for the specified tenant.
     *
     * @param id      tenant identifier
     * @param request tenant status request
     * @return tenant status response
     */
    @Transactional
    public TenantStatusFullResponse updateTenantAutoJobs(String id, @Valid TenantStatusDto request) {
        validateJobsMode(request.jobs, EventMode.AUTO);
        if (request.jobs != null) {
            request.jobs.forEach(this::applyJobDefinition);
        }

        return updateTenantJobsInternal(id, request);
    }

    /**
     * Updates tenant jobs for the specified tenant.
     *
     * @param id      tenant identifier
     * @param request tenant status request
     * @return tenant status response
     */
    @Transactional
    public TenantStatusFullResponse updateTenantJobsInternal(String id, @Valid TenantStatusDto request) {

        var tenant = tenantRepository.findById(id);

        var existingStatus = TenantMapper.INSTANCE.mapStatusObject(tenant.getStatus());
        var isComputeEngineCompleted = isComputeEngineCompleted(request);
        request.jobs = mergeJobs(existingStatus.jobs, request.jobs);

        var updatedStatusJson = TenantMapper.INSTANCE.mergeJobsIntoStatus(tenant.getStatus(), request);

        try {
            // ✅ DB update
            updateTenantStatusInDb(tenant, updatedStatusJson);

            var statusDto = TenantMapper.INSTANCE.mapStatusObject(tenant.getStatus());

            var response = new TenantStatusFullResponse();
            response.name = tenant.name;
            response.status = statusDto;

            // Async notification
            if (isComputeEngineCompleted) {
                var alert = buildAlert(
                        EventName.INIT_TOPOLOGY_CONNECTOR,
                        tenant,
                        String.valueOf(Instant.now())
                );

                CompletableFuture.runAsync(() -> {
                    try {
                        notifyAmsInitConnector(id, alert);
                    } catch (Exception ex) {
                        Log.error("AMS notification failed for tenant {}", id, ex);
                    }
                });

            }

            return response;

        } catch (Exception dbException) {
            throw new RuntimeException("Updating Tenant's Status.. DB update failed: " + dbException.getMessage());
        }
    }

    private boolean isComputeEngineCompleted(TenantStatusDto request) {
        return request.jobs.stream()
                .anyMatch(j -> EventName.INIT_COMPUTE_ENGINE.name().equals(j.name) &&
                        EventStatus.COMPLETED.name().equals(j.getStatus()));
    }

    /**
     * Updates tenant alert jobs for the specified tenant.
     *
     * @param id      tenant identifier
     * @param request tenant status request
     * @return tenant status response
     * @throws IOException when status serialization fails
     */
    @Transactional
    public TenantStatusDto updateTenantAlerts(String id, @Valid TenantStatusDto request) throws IOException {

        var tenant = tenantRepository.findById(id);
        if (tenant == null) {
            return null;
        }
        var existingStatus = TenantMapper.INSTANCE.mapStatusObject(tenant.getStatus());
        request.jobs = mergeJobs(existingStatus.jobs, request.jobs);

        if (request.jobs != null) {
            request.jobs.forEach(this::applyJobDefinition);
        }

        request.jobs = mergeJobs(existingStatus.jobs, request.jobs);
        try {
            var updatedAlertJson = TenantMapper.INSTANCE.mergeJobsIntoStatus(tenant.getStatus(), request);

            updateTenantStatusInDb(tenant, updatedAlertJson);
            return TenantMapper.INSTANCE.mapStatusObject(tenant.getStatus());
        } catch (Exception dbException) {

            throw new RuntimeException("Updating Tenant's Status... DB update failed: " + dbException.getMessage());
        }
    }

    /**
     * Merges existing jobs with new jobs by replacing or adding job entries by name.
     *
     * @param existingJobs existing jobs
     * @param newJobs      new jobs
     * @return merged job list
     */
    private List<EventStatusDto> mergeJobs(List<EventStatusDto> existingJobs,
                                           List<EventStatusDto> newJobs) {

        if (existingJobs == null && newJobs == null) {
            return Collections.emptyList();
        }
        if (existingJobs == null) {
            return new ArrayList<>(newJobs);
        }
        if (newJobs == null) {
            return new ArrayList<>(existingJobs);
        }
        Map<String, EventStatusDto> map = existingJobs.stream()
                .collect(Collectors.toMap(
                        e -> e.name.toUpperCase(),   // normalize key
                        e -> e
                ));

        // Replace or add
        for (EventStatusDto newJob : newJobs) {
            var oldJob = map.get(newJob.name);
            if (oldJob != null) {

                if (newJob.getStart() == null && oldJob.getStart() != null) {
                    newJob.setStart(oldJob.getStart());
                }
                if (newJob.getEnd() == null && oldJob.getEnd() != null) {
                    newJob.setEnd(oldJob.getEnd());
                }
                if (newJob.properties == null || newJob.properties.isEmpty()) {
                    newJob.properties = oldJob.properties;
                }
            }
            map.put(newJob.name, newJob);


        }
        return new ArrayList<>(map.values());
    }


    /**
     * Creates the tenant group in the authorization provider if it does not exist.
     *
     * @param tenantId tenant identifier
     * @return tenant group status
     */
    public TenantGroupStatus createTenantGroup(String tenantId) {

        var tenant = tenantRepository.findById(tenantId);
        var groupPath = "/" + namespace + "/tenants/" + tenant.name;

        try {
            String groupId = groupManagement.getGroupId(groupPath);

            if (groupId != null) {
                return TenantGroupStatus.EXISTS;
            }

            Map<String, List<String>> attributes = Map.of(
                    "tenantId", List.of(tenant.id),
                    "description", List.of(tenant.description)
            );

            groupManagement.createGroup(
                    "/" + namespace + "/tenants",
                    tenant.name,
                    List.of("admin", "viewer"),
                    attributes
            );

            return TenantGroupStatus.EXISTS;

        } catch (Exception e) {
            throw new ServiceUnavailableException("Creating Tenant's group management... Group management service is unavailable.");
        }
    }


    /**
     * Retrieves the current authorization group status for the given tenant.
     *
     * @param tenant tenant entity
     * @return tenant group status
     */
    private TenantGroupStatus getGroupStatus(Tenant tenant) {
        var groupPath = "/" + namespace + "/tenants/" + tenant.name;
        try {
            return groupManagement.getGroupId(groupPath) != null
                    ? TenantGroupStatus.EXISTS
                    : TenantGroupStatus.NOT_FOUND;
        } catch (Exception e) {
            return TenantGroupStatus.UNKNOWN;
        }
    }

    /**
     * Notify ams that tenant is created and should initialize the corresponding event process
     *
     * @param id,    the tenant's id
     * @param alert, the alert to be sent to AMS
     * @return TenantStatusDto
     */
    @Transactional
    public TenantStatusDto notifyAms(String id, AlertDefinitionRequest alert) {
        var now = Instant.now();
        var tenant = tenantRepository.findById(id);

        if (alert.properties.containsKey("tenant_name") && !alert.properties.get("tenant_name").equals(tenant.name)) {
            throw new BadRequestException("Notifying Messaging Service... Value of property 'name' differs from tenant's name: " + tenant.name);
        }

        validateAlertProperties(alert.name, alert.properties);

        alert.getProperties().put("tenant_id", id);
        alert.setCreatedAt(String.valueOf(now));
        send(id, alert, "");


        var statusOpt = tenantRepository.fetchTenantStatus(id);
        if (!statusOpt.isEmpty()) {
            return TenantMapper.INSTANCE.mapStatusObject(statusOpt.get());
        }
        return null;
    }

    /**
     * Retrieves a paginated list of tenant members from the authorization provider.
     *
     * @param tenantId tenant identifier
     * @param page     0-based page index
     * @param size     page size
     * @param uriInfo  request context for pagination links
     * @return paginated list of tenant members
     */
    public PageResource<GroupUserResponse> getMembersByTenant(String tenantId, int page, int size, UriInfo uriInfo) {

        var tenant = tenantRepository.findById(tenantId);

        var response = groupManagementService.getMembers("tenants/" + tenant.name, page * size, size, "");

        var members = response
                .results
                .stream()
                .map(g -> g.user)
                .map(gu -> {
                    var user = new GroupUserResponse();
                    user.id = gu.id;
                    user.email = gu.email;
                    user.username = gu.username;
                    user.firstName = gu.firstName;
                    user.lastName = gu.lastName;
                    user.tenants = gu.getTenants();
                    return user;
                })
                .collect(Collectors.toList());

        var pageable = new PageQueryImpl<GroupUserResponse>();

        pageable.list = members;
        pageable.index = page;
        pageable.size = size;
        pageable.count = response.count;
        pageable.page = Page.of(page, size);

        return new PageResource<>(pageable, uriInfo);
    }


    /**
     * Sends a readiness validation notification to the AMS for the specified tenant.
     *
     * @param id    tenant identifier
     * @param alert alert request
     * @return tenant status response
     */
    public TenantStatusDto notifyAmsCheckReadiness(String id, AlertDefinitionRequest alert) {
        var now = Instant.now();
        var tenant = tenantRepository.findById(id);

        if (alert.properties.containsKey("tenant_name") && !alert.properties.get("tenant_name").equals(tenant.name)) {
            throw new BadRequestException("Notifying Messaging Service... Value of property 'name' differs from tenant's name: " + tenant.name);
        }

        validateAlertProperties(alert.name, alert.properties);

        alert.getProperties().put("tenant_id", id);
        alert.setCreatedAt(String.valueOf(now));
        send(id, alert, "Notifying Messaging Service.. A request is sent to the Messaging Service to validate that the necessary data and configuration are in place prior to starting the monitoring process");


        var statusOpt = tenantRepository.fetchTenantStatus(id);
        if (!statusOpt.isEmpty()) {
            return TenantMapper.INSTANCE.mapStatusObject(statusOpt.get());
        }
        return null;
    }

    /**
     * Sends a readiness validation notification to the AMS for the specified tenant.
     *
     * @param id    tenant identifier
     * @param alert alert request
     * @return tenant status response
     */
    @Transactional
    public TenantStatusDto notifyAmsInitConnector(String id, AlertDefinitionRequest alert) {
        var now = Instant.now();
        var tenant = tenantRepository.findById(id);

        if (alert.properties.containsKey("tenant_name") && !alert.properties.get("tenant_name").equals(tenant.name)) {
            throw new BadRequestException("Notifying Messaging Service... Value of property 'name' differs from tenant's name: " + tenant.name);
        }

        validateAlertProperties(alert.name, alert.properties);

        alert.getProperties().put("tenant_id", id);
        alert.setCreatedAt(String.valueOf(now));
        send(id, alert, "Notifying Messaging Service.. A request is sent to the Messaging Service to validate that the necessary data and configuration are in place prior to starting the monitoring process");


        var statusOpt = tenantRepository.fetchTenantStatus(id);
        if (!statusOpt.isEmpty()) {
            return TenantMapper.INSTANCE.mapStatusObject(statusOpt.get());
        }
        return null;
    }


    /**
     * Sends initialization event notifications for the given tenant.
     *
     * @param tenant tenant entity
     */
    private void sendNotifications(Tenant tenant) {

        String createdAt = String.valueOf(Instant.now());

        send(tenant.id, buildAlert(EventName.INIT_AMS, tenant, createdAt), "");
        send(tenant.id, buildAlert(EventName.INIT_MONGO, tenant, createdAt), "");
        send(tenant.id, buildAlert(EventName.INIT_COMPUTE_ENGINE, tenant, createdAt), "");
    }

    /**
     * Builds an alert definition request for the specified event and tenant.
     *
     * @param eventName event name
     * @param tenant    tenant entity
     * @param createdAt created timestamp
     * @return alert definition request
     */
    private AlertDefinitionRequest buildAlert(EventName eventName, Tenant tenant, String createdAt) {
        AlertDefinitionRequest alert = new AlertDefinitionRequest();
        alert.name = eventName.name();
        alert.setCreatedAt(createdAt);


        alert.setProperties(new HashMap<>(Map.of(
                "tenant_id", tenant.id,
                "tenant_name", tenant.name
        )));
        return alert;
    }

    /**
     * Publishes an alert event to the messaging service and updates tenant status accordingly.
     *
     * @param id       tenant identifier
     * @param alert    alert request
     * @param eventMsg custom publish message
     */
    private void send(String id, AlertDefinitionRequest alert, String eventMsg) {

        final boolean hasCustomMsg =
                eventMsg != null && !eventMsg.isEmpty();


        // INITIALISING message
        final String publishingMsg =
                hasCustomMsg
                        ? eventMsg
                        : "Event notification: " + alert.name +
                          " is sent to Messaging Service for publishing";


        // Your special INITIALISED message (only when eventMsg exists)
        final String customInitialisedMsg =
                "A request is initialised to the Messaging Service " +
                        "to validate that the necessary data and configuration " +
                        "are in place prior to starting the monitoring process";


        // FINAL INITIALISED message
        final String initialisedMsg =
                hasCustomMsg
                        ? customInitialisedMsg
                        : "Event notification: " + alert.name +
                          " is initialised to Messaging Service for publishing";

// ✅ CUSTOM FAILED
        final String customFailedMsg =
                "A request to validate that the necessary data and configuration " +
                        "are in place prior to starting the monitoring process, failed to be published the Messaging Service.";


// FAILED
        final String failedMsg =
                hasCustomMsg
                        ? customFailedMsg
                        : "Event notification: " + alert.name +
                          " failed to be initialised to Messaging Service";
        try {
            final Instant now = Instant.now();

            final ObjectMapper objectMapper = new ObjectMapper();
            final String json = objectMapper.writeValueAsString(alert);

            Log.infof(
                    "Sending to Messaging Service | project=%s | topic=%s | tenantId=%s | event=%s",
                    amsService.getProject(),
                    amsService.getTopic(),
                    id,
                    alert.name.toUpperCase()
            );


            final String encodedData =
                    Base64.getEncoder().encodeToString(json.getBytes());

            final PublishRequest.Message message =
                    new PublishRequest.Message();

            message.setData(encodedData);

            final PublishRequest publishData = new PublishRequest();
            publishData.setMessages(List.of(message));


            // 1. INITIALISING
            updateTenantAlerts(
                    id,
                    setAlert(
                            alert.name,
                            EventStatus.INITIALISING,
                            publishingMsg,
                            now,
                            alert.properties
                    )
            );


            // 2. Async publish
            CompletableFuture
                    .runAsync(
                            () -> amsService.publishMessage(publishData),
                            executorService
                    )


                    // 3. INITIALISED
                    .thenRun(() -> {
                        try {
                            updateTenantAlerts(
                                    id,
                                    setAlert(
                                            alert.name,
                                            EventStatus.INITIALISED,
                                            initialisedMsg,
                                            now,
                                            alert.properties
                                    )
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })


                    // 4. FINAL RESULT
                    .whenComplete((ignored, throwable) -> {

                        try {
                            if (throwable == null) {

                                updateTenantAlerts(
                                        id,
                                        setAlert(
                                                alert.name,
                                                EventStatus.INITIALISED,
                                                initialisedMsg,
                                                now,
                                                alert.properties
                                        )
                                );

                                Log.debugf(
                                        "Messaging Service publish succeeded for tenantId=%s, alert=%s",
                                        id,
                                        alert.name
                                );

                            } else {

                                Log.errorf(
                                        throwable,
                                        "Messaging Service publish failed for tenantId=%s, alert=%s",
                                        id,
                                        alert.name
                                );

                                updateTenantAlerts(
                                        id,
                                        setAlert(
                                                alert.name,
                                                EventStatus.FAILED_INITIALISATION,
                                                failedMsg,
                                                now,
                                                alert.properties
                                        )
                                );
                            }

                        } catch (Exception e) {
                            Log.error("Failed to update tenant status", e);
                        }
                    });

        } catch (Exception e) {

            Log.error("Failed to send alert to Messaging Service", e);

            throw new RuntimeException(
                    "Sending notification... Failed to send event notification: " + alert.name,
                    e
            );
        }
    }

    /**
     * Creates a tenant status request containing a single alert job update.
     *
     * @param eventName  event name
     * @param status     event status
     * @param message    status message
     * @param start      start timestamp
     * @param properties alert properties
     * @return tenant status request
     */
    private TenantStatusDto setAlert(String eventName, EventStatus status, String message, Instant start, Map<String, String> properties) {
        var tenantStatus = new TenantStatusDto();
        tenantStatus.jobs = new ArrayList<>();
        var alert = new EventStatusDto();
        alert.start = start;
        alert.setName(eventName);
        alert.setStatus(status.name());
        alert.setMessage(message);

        if (properties != null && !properties.isEmpty()) {
            alert.properties = new HashMap<>(properties); // copy
        }
        tenantStatus.jobs.add(alert);
//        if (status.equals(EventStatus.FAILED_INITIALISATION) || status.equals(EventStatus.INITIALISED)) {
//            alert.end = Instant.now();
//        }
        return tenantStatus;
    }

    /**
     * Builds the default tenant status with all jobs set to unknown.
     *
     * @return default tenant status
     */
    private TenantStatusDto setDefaultStatus() {
        var dto = new TenantStatusDto();
        dto.jobs = new ArrayList<>();

        for (var def : TenantJobEvent.values()) {
            EventStatusDto job = new EventStatusDto();
            job.setName(def.key());
            job.setMode(def.modeValue());
            job.setStatus(EventStatus.UNKNOWN.name());
            if (def.isManual()) {
                job.setMessage("Waiting for manual administrator action");
            }
            dto.jobs.add(job);
        }

        return dto;
    }

    /**
     * Retrieves the tenant status by tenant identifier.
     *
     * @param id tenant identifier
     * @return tenant status response
     */
    public TenantStatusFullResponse getTenantStatus(String id) {
        var resultOpt = tenantRepository.fetchTenantNameAndStatus(id);

        if (resultOpt.isPresent()) {
            Object[] result = resultOpt.get();
            String name = (String) result[0];
            String statusString = (String) result[1];

            TenantStatusDto statusDto = TenantMapper.INSTANCE.mapStatusObject(statusString);

            TenantStatusFullResponse response = new TenantStatusFullResponse();
            response.name = name;
            response.status = statusDto;

            return response;
        }

        return null; // or Optional<TenantStatusFullResponse> if you prefer
    }

    /**
     * Normalizes a job entry by applying the corresponding job definition.
     *
     * @param job job entry
     */
    private void applyJobDefinition(EventStatusDto job) {
        if (job == null || job.name == null) return;

        var def = org.grnet.status.enums.TenantJobEvent
                .fromKey(job.name)
                .orElseThrow(() -> new BadRequestException("Updating Tenant's Status... Unknown job name: " + job.name));

        job.setName(def.key());
        job.setMode(def.modeValue());
    }

    /**
     * Validates that all provided jobs match the expected mode.
     *
     * @param jobs         job list
     * @param expectedMode expected job mode
     */
    private void validateJobsMode(List<EventStatusDto> jobs, EventMode expectedMode) {

        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        for (EventStatusDto job : jobs) {
            if (job == null || job.getName() == null) {
                continue;
            }

            var def = TenantJobEvent
                    .fromKey(job.getName())
                    .orElseThrow(() -> new BadRequestException("Updating Tenant's Status... Unknown job name: " + job.getName()));

            if (def.mode() != expectedMode) {
                throw new BadRequestException("Updating Tenant's Status... " + "Job '" + def.key() + "' is " + def.mode().name().toLowerCase()
                        + " and cannot be updated");
            }
        }
    }

    /**
     * Validates that the provided alert properties are allowed for the specified event.
     *
     * @param eventName event name
     * @param props     alert properties
     */
    private void validateAlertProperties(String eventName, Map<String, String> props) {
        if (props == null || props.isEmpty()) return;

        var def = TenantJobEvent.fromKey(eventName)
                .orElseThrow(() -> new jakarta.ws.rs.BadRequestException("Validating Event Notification... Unknown job name: " + eventName));

        for (var k : props.keySet()) {
            var keyEnum = TenantJobProperty.fromKey(k)
                    .orElseThrow(() -> new jakarta.ws.rs.BadRequestException(
                            "Validating Event Notification... Unknown property key '" + k + "' for job '" + def.key() + "'"
                    ));

            if (!def.allowedProperties().contains(keyEnum)) {
                throw new jakarta.ws.rs.BadRequestException(
                        "Validating Event Notification... " + "Property '" + keyEnum.key() + "' is not allowed for job '" + def.key() + "'"
                );
            }
        }
    }

    /**
     * Checks tenant readiness by tenant identifier.
     *
     * @param id tenant identifier
     * @return tenant readiness response
     */
    @Transactional
    public WebApiTenantReadiness checkReadiness(String id) {

        var tenant = tenantRepository.findById(id);

        if (tenant == null) {
            throw new WebApplicationException("Checking Readiness.. Tenant not found", 404);
        }

        try {
            return webApiService.retrieveTenantReadinessWebApi(tenant.id);

        } catch (JsonProcessingException e) {

            Log.error("Invalid JSON received while checking readiness for tenant {}", id, e);

            throw new WebApplicationException(
                    "Checking Readiness... " + "Failed to check tenant readiness due to invalid response from Argo Web Api",
                    502  // Bad Gateway (external system problem)
            );

        } catch (WebApplicationException e) {
            Log.error("Argo Web Api error while checking readiness for tenant {}", id, e);
            throw new WebApplicationException(
                    "Checking readiness... " + "Argo Web Api error while checking tenant readiness",
                    e.getResponse().getStatus()
            );

        } catch (Exception e) {
            Log.error("Unexpected error while checking readiness for tenant {}", id, e);

            throw new WebApplicationException(
                    "Checking readiness... " + "Unexpected error occurred while checking tenant readiness.",
                    500
            );
        }
    }

    @Transactional
    public WebApiNodeResponse updateTenantNode(String tenantId, TenantWebApiNodeRequest request) {

        var tenant = tenantRepository.findById(tenantId);

        try {
            var response = webApiService.updateTenantNodeWebApi(tenantId, request);

            tenant.setNode(Boolean.TRUE.equals(request.node) ? Boolean.TRUE : null);
            tenantRepository.persist(tenant);
            tenantRepository.flush();

            return response;

        } catch (Exception e) {
            throw new WebApplicationException(
                    "Updating Tenant Node... Failed to update tenant node for tenant with id: " + tenantId + " in Argo Web Api",
                    502
            );
        }
    }

    /**
     * Retrieves availability metrics for a node's services.
     *
     * @param id          the tenants identifier
     * @param date        optional specific date (YYYY-MM-DD)
     * @param startTime   optional start time (W3C format)
     * @param endTime     optional end time (W3C format)
     * @param startDate   optional start date (YYYY-MM-DD)
     * @param endDate     optional end date (YYYY-MM-DD)
     * @param granularity optional aggregation level (daily or monthly)
     * @return availability results for the node's services
     */
    public WebApiNodeAvailabilityResponse getAvailability(String id, String date, String startTime, String endTime, String startDate, String endDate, String granularity) {

        var tenant = tenantRepository.findById(id);

        return webApiService.retrieveNodeAvailability(tenant.name, date, startTime, endTime, startDate, endDate, granularity);
    }


    /**
     * Retrieves status information for a node's services.
     *
     * @param id        the tenants identifier
     * @param startTime optional start time (W3C format)
     * @param endTime   optional end time (W3C format)
     * @param history   optional flag to include full status history
     * @return status results for the node's services
     */
    public WebApiNodeStatusResponse getStatus(String id, String startTime, String endTime, Boolean history) {

        var tenant = tenantRepository.findById(id);

        return webApiService.retrieveNodeStatus(tenant.name, startTime, endTime, history);
    }


    @Transactional
    public void syncWebApiTenantsToLocalDb() {

        Log.info("Starting tenant sync from Argo Web API to local DB...");

        try {
            var response = webApiService.retrieveTenantsWebApi();

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                Log.info("No tenants returned from Argo Web API.");
                return;
            }

            response.getData().forEach(remoteTenant -> {
                try {
                    syncSingleTenantToLocalDb(remoteTenant);
                } catch (Exception e) {
                    Log.errorf(e, "Failed to sync tenant with id: %s", remoteTenant.getId());
                }
            });

            Log.info("Tenant sync completed.");

        } catch (Exception e) {
            Log.error("Failed to fetch tenants from Argo Web API during startup sync.", e);
        }
    }

    private void syncSingleTenantToLocalDb(TenantWebApiGetResponse.Data remoteTenant) {

        if (remoteTenant == null || remoteTenant.getId() == null || remoteTenant.getInfo() == null) {
            Log.warn("Skipping invalid tenant from Web API response.");
            return;
        }

        var info = remoteTenant.getInfo();
        var now = Timestamp.from(Instant.now());

        var existingTenant = tenantRepository.findById(remoteTenant.getId());

        if (existingTenant == null) {

            var tenant = new Tenant();
            tenant.setId(remoteTenant.getId());
            tenant.setName(info.getName());
            tenant.setEmail(info.getEmail());
            tenant.setDescription(info.getDescription());
            tenant.setWebsite(info.getWebsite());
            tenant.setImage(info.getImage());
            tenant.setUpdatedBy("dev-sync");
            tenant.setCreatedAt(now);
            tenant.setUpdatedAt(now);

            var metadata = new TenantMetadata();
            if (metadata.instance == null) {
                metadata.instance = new InstanceDto();
            }
            metadata.instance.topology = TenantMapper.INSTANCE.topologyToDto(remoteTenant.getTopology());

            tenant.setMetadata(TenantMapper.INSTANCE.mapMetadataToString(metadata));
            tenant.setStatus(TenantMapper.INSTANCE.mapStatusToString(setDefaultStatus()));
            tenant.setNode(Boolean.TRUE.equals(remoteTenant.getNode()) ? Boolean.TRUE : null);

            tenantRepository.persist(tenant);

            Log.infof("Tenant inserted locally: %s (%s)", tenant.getName(), tenant.getId());
            return;
        }

        existingTenant.setName(info.getName());
        existingTenant.setEmail(info.getEmail());
        existingTenant.setDescription(info.getDescription());
        existingTenant.setWebsite(info.getWebsite());
        existingTenant.setImage(info.getImage());
        existingTenant.setNode(Boolean.TRUE.equals(remoteTenant.getNode()) ? Boolean.TRUE : null);
        existingTenant.setUpdatedBy("dev-sync");
        existingTenant.setUpdatedAt(now);

        var metadata = TenantMapper.INSTANCE.mapMetadataObject(existingTenant.getMetadata());
        if (metadata == null) {
            metadata = new TenantMetadata();
        }
        if (metadata.instance == null) {
            metadata.instance = new InstanceDto();
        }
        metadata.instance.topology = TenantMapper.INSTANCE.topologyToDto(remoteTenant.getTopology());

        existingTenant.setMetadata(TenantMapper.INSTANCE.mapMetadataToString(metadata));

        tenantRepository.persist(existingTenant);

        Log.infof("Tenant updated locally: %s (%s)", existingTenant.getName(), existingTenant.getId());
    }
}