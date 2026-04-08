package org.grnet.status.services;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.grnet.status.authorizations.dtos.GroupUser;
import org.grnet.status.authorizations.dtos.GroupUserResponse;
import org.grnet.status.authorizations.dtos.PartialGroup;
import org.grnet.status.authorizations.groups.AuthGroupManagement;
import org.grnet.status.authorizations.groups.GroupManagement;
import org.grnet.status.authorizations.groups.GroupMembersResponse;
import org.grnet.status.dtos.pagination.PageResource;
import org.grnet.status.entities.Page;
import org.grnet.status.entities.PageQueryImpl;
import org.grnet.status.repositories.TenantRepository;
import org.grnet.status.util.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service responsible for managing groups and their members through the external authorization provider.
 */
@ApplicationScoped
public class GroupManagementService {

    @Inject
    AuthGroupManagement groupManagement;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    Utility utility;

    @Inject
    MailerService mailerService;

    @ConfigProperty(name = "api.auth.entitlements.parent.group")
    String parentGroup;

    @ConfigProperty(name = "api.ui.url")
    String uiBaseUrl;

    @ConfigProperty(name = "api.auth.entitlements.parent.group")
    String namespace;

    /**
     * Returns a paginated list of members for the given group.
     *
     * @param groupName the group identifier
     * @param search optional search filter
     * @param page 0-based page index
     * @param size number of records per page
     * @param uriInfo request context for pagination links
     * @return paginated list of group members
     */
    public PageResource<GroupUserResponse> getAllMembers(String groupName, String search, int page, int size, UriInfo uriInfo) {

        var response = getMembers(groupName, page*size, size, search);

        var members = response
                .results
                .stream()
                .map(g->g.user)
                .map(gu -> {
                    var user = new GroupUserResponse();
                    user.id = gu.id;
                    user.email = gu.email;
                    user.username = gu.username;
                    user.firstName = gu.firstName;
                    user.lastName = gu.lastName;
                    user.uid = gu.getUid();
                    user.tenants = gu.getTenants();
                    return user;
                })
                .toList();

        var pageable = new PageQueryImpl<GroupUserResponse>();

        pageable.list = members;
        pageable.index = page;
        pageable.size = size;
        pageable.count = response.count;
        pageable.page = Page.of(page, size);

        return new PageResource<>(pageable, uriInfo);
    }

    /**
     * Fetches members of a group directly from the authorization provider.
     *
     * @param groupName the group identifier
     * @param first starting index (offset)
     * @param max maximum number of results
     * @param search optional search filter
     * @return provider response containing members and total count
     */
    public GroupMembersResponse getMembers(String groupName, int first, int max, String search) {

        var fullPath = normalizePath(parentGroup) + "/" + groupName;

        return groupManagement.fetchGroupMembers(fullPath, first, max, search);
    }

    /**
     * Returns members of a tenant group filtered by role.
     *
     * @param groupName tenant name
     * @param role role to filter by (admin, viewer, member)
     * @return list of users with the specified role
     */
    public List<GroupUser> getTenantMembersByRole(String groupName, String role) {

        var fullPath = normalizePath(parentGroup) + "/tenants/" + groupName;

        return groupManagement.fetchGroupMembersByRole(fullPath, role);
    }

    /**
     * Adds a user to a group with the default role "member".
     *
     * @param groupName the group identifier
     * @param username user identifier recognized by the auth provider
     */
    public void addMember(String groupName, String username) {

        var fullPath = normalizePath(parentGroup) + "/" + groupName;

        groupManagement.addGroupMember(fullPath, username, "member");
    }

    /**
     * Adds a user to a tenant group with a specific role.
     *
     * @param tenantName tenant name
     * @param username user identifier
     * @param role role to assign
     */
    public void addUserToTenantGroup(String tenantName, String username, String role) {

        var parentPath = "/" + namespace + "/tenants/" + tenantName;

        groupManagement.addGroupMember(parentPath, username, role);
    }

    /**
     * Normalizes a group path.
     *
     * @param p raw path
     * @return normalized path
     */
    private static String normalizePath(String p) {
        if (p == null || p.isBlank()) return "";
        p = p.trim();

        // ensure leading slash
        if (!p.startsWith("/")) p = "/" + p;

        // remove trailing slash
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);

        return p;
    }

    /**
     * Returns a paginated list of groups from the authorization provider.
     *
     * @param page 0-based page index
     * @param size page size
     * @param uriInfo request context for pagination links
     * @return paginated list of groups
     */
    public PageResource<PartialGroup> fetchGroups(int page, int size, UriInfo uriInfo){

        var groups = groupManagement.fetchGroups();

        var partition = utility.partition(new ArrayList<>(groups), size);

        var pageableMembers = partition.get(page) == null ? Collections.EMPTY_LIST : partition.get(page);

        var pageable = new PageQueryImpl<PartialGroup>();

        pageable.list = pageableMembers;
        pageable.index = page;
        pageable.size = size;
        pageable.count = groups.size();
        pageable.page = Page.of(page, size);

        return new PageResource<>(pageable, uriInfo);
    }

    /**
     * Adds a user to a tenant group and sends a notification email.
     *
     * @param tenantId tenant identifier
     * @param username user identifier
     * @param role role to assign
     * @param email email address for notification
     */
    public void addMemberToGroup(String tenantId, String username, String role, String email){

        var tenant = tenantRepository.findById(tenantId);

        var parentPath = "/" + namespace + "/tenants/"+tenant.name;

        groupManagement.addGroupMember(parentPath, username, role);

        try {
            mailerService.sendEmailToMemberAddedGroup(
                    List.of(email),
                    tenant.name,
                    role,
                    uiBaseUrl
            );
        } catch (Exception e) {
            Log.warn("Added to group email failed: " + email, e);
        }
    }

    /**
     * Removes a member from a tenant group.
     *
     * @param tenantId tenant identifier
     * @param memberId provider member identifier
     */
    public void deleteMemberFromGroup(String tenantId, String memberId){

        var tenant = tenantRepository.findById(tenantId);

        var parentPath = "/" + namespace + "/tenants/"+tenant.name;

        groupManagement.removeMemberFromGroup(parentPath, memberId);
    }
}
