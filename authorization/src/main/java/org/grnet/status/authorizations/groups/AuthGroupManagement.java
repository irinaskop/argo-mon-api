package org.grnet.status.authorizations.groups;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.grnet.status.authorizations.clients.KeycloakGroupClient;
import org.grnet.status.authorizations.dtos.*;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
//@IfBuildProfile("prod")
public class AuthGroupManagement implements GroupManagement {

    private static final Logger LOG = Logger.getLogger(AuthGroupManagement.class);

    @Inject
    @RestClient
    KeycloakGroupClient groupClient;


    /**
     * Creates a subgroup under a parent path and assigns roles and configuration.
     *
     * @param parentPath parent group path
     * @param name group name
     * @param roles roles to assign
     * @param attributes group attributes
     */
    @Override
    public void createGroup(String parentPath, String name, List<String> roles, Map<String, List<String>> attributes) {

        if (roles == null) roles = List.of("admin", "viewer");
        if (attributes == null) attributes = Map.of("description", List.of(name));

        // Build request
        var req = new GroupRequest();
        req.name = name;
        req.attributes = attributes;


        // Resolve parent group ID
        var parentId = getGroupIdByPath(parentPath);
        if (parentId == null)
            throw new IllegalStateException("Creating group failed. Parent group not found at path: " + parentPath);

        // Create child group
        groupClient.createSubGroup(parentId, req);

        // Resolve new group ID (path-based)
        var newGroupPath = parentPath + "/" + name;
        var newGroupId = getGroupIdByPath(newGroupPath);
        if (newGroupId == null)
            throw new IllegalStateException("Creating group failed. New group not found after creation: " + newGroupPath);

        LOG.infof("Group created: %s → ID: %s", newGroupPath, newGroupId);

        // Assign roles
        for (String role : roles) {
            groupClient.addRole(newGroupId, role);
        }

        // Update the group configuration with roles
        updateGroupConfigurationRoles(newGroupId, roles);

    }

    /**
     * Deletes a group by its full path if it exists.
     *
     * @param fullGroupPath full group path
     */
    @Override
    public void deleteGroup(String fullGroupPath) {
        try {
            var id = getGroupIdByPath(fullGroupPath);
            if (id != null) {
                groupClient.deleteGroup(id);
            } else {
                LOG.warnf("Group not found for deletion: %s", fullGroupPath);
            }
        } catch (Exception e) {
            LOG.errorf("Failed to delete group %s: %s", fullGroupPath, e.getMessage());
        }
    }

    /**
     * Assigns a role to a group.
     *
     * @param groupId group identifier
     * @param role role to assign
     */
    @Override
    public void addRole(String groupId, String role) {
        groupClient.addRole(groupId, role);
    }

    /**
     * Updates the default configuration roles of a group.
     *
     * @param groupId group identifier
     * @param roles roles to set
     */
    @Override
    public void updateConfiguration(String groupId, List<String> roles) {
        updateGroupConfigurationRoles(groupId, roles);
    }

    /**
     * Updates the group configuration to include the provided roles.
     *
     * @param groupId group identifier
     * @param roles roles to set
     */
    private void updateGroupConfigurationRoles(String groupId, List<String> roles) {

        // Fetch full group structure
        var group = groupClient.getGroup(groupId);

        // Obtain defaultConfiguration ID
        var configId = group.attributes
                .get("defaultConfiguration")
                .get(0);

        // Fetch complete configuration JSON object
        var config = groupClient.getConfiguration(groupId, configId);

        // Remove default "member" role if present
        if (config.groupRoles != null) {
            config.groupRoles.remove("member");
        }

        // Assign new roles
        config.setGroupRoles(roles);

        // Update configuration
        groupClient.updateConfiguration(groupId, config);

        LOG.infof("Updated roles for group %s → %s", groupId, roles);
    }

    /**
     * Resolves a group identifier from a full group path.
     *
     * @param fullPath full group path
     * @return group identifier or null if not found
     */
    @Override
    public String getGroupId(String fullPath) {
        return getGroupIdByPath(fullPath);
    }

    /**
     * Resolves a group identifier from a full group path using the flattened group map.
     *
     * @param fullPath full group path
     * @return group identifier or null if not found
     */
    private String getGroupIdByPath(String fullPath) {
        return flattenGroups().get(fullPath);
    }

    /**
     * Builds a flattened map of all group paths to group identifiers.
     *
     * @return map of group path to group identifier
     */
    private Map<String, String> flattenGroups() {
        var response = groupClient.getGroups("");
        Map<String, String> map = new HashMap<>();

        for (Group group : response.results) {
            collectGroupRecursive(group, map);
        }
        return map;
    }

    /**
     * Retrieves group members for the given group path with paging and optional search.
     *
     * @param fullPath full group path
     * @param first offset
     * @param max page size
     * @param search search filter
     * @return group members response
     */
    @Override
    public GroupMembersResponse fetchGroupMembers(String fullPath, int first, int max, String search) {

        var groupId = getGroupIdByPath(fullPath);

        return groupClient.getGroupMembers(groupId, first, max, search);
    }

    /**
     * Retrieves group members for the given group filtered by role.
     *
     * @param fullPath full group path
     * @param role role filter
     * @return list of group users
     */
    @Override
    public List<GroupUser> fetchGroupMembersByRole(String fullPath, String role) {

        var groupId = getGroupIdByPath(fullPath);

        LOG.infof("AGM getMembersByRole fullPath=%s groupId=%s role=%s", fullPath, groupId, role);


        var response = groupClient.getMembersByRole(groupId, role);

        if (response == null || response.results == null) {
            return List.of();
        }

        return response.results.stream()
                .map(entry -> entry.user)
                .toList();
    }

    /**
     * Adds a user to a group with the specified role if not already a member.
     *
     * @param fullPath full group path
     * @param username user identifier
     * @param role role to assign
     */
    @Override
    public void addGroupMember(String fullPath, String username, String role) {

        var groupId = getGroupIdByPath(fullPath);

        var response = groupClient.getGroupMembers(groupId, 0, 10, username);

        if(response.count>0){
            return;
        }

        groupClient.addUserToGroup(groupId, new AddGroupMemberRequest(username, List.of(role)));
    }

    /**
     * Adds a user to a group by group identifier with the specified role if not already a member.
     *
     * @param id group identifier
     * @param username user identifier
     * @param role role to assign
     */
    @Override
    public void addMemberToGroupByGroupId(String id, String username, String role) {

        var response = groupClient.getGroupMembers(id, 0, 10, username);

        if (response.count>0) {
            return;
        }

        groupClient.addUserToGroup(id, new AddGroupMemberRequest(username, List.of(role)));
    }

    /**
     * Retrieves a flattened list of groups.
     *
     * @return list of partial groups
     */
    @Override
    public List<PartialGroup> fetchGroups() {

        var groups = new ArrayList<PartialGroup>();

        var response = groupClient.getGroups("");

        for (Group group : response.results) {
            collectGroupRecursive(group, groups);
        }
        return groups;
    }

    /**
     * Removes a member from a group.
     *
     * @param fullPath full group path
     * @param memberId member identifier
     */
    @Override
    public void removeMemberFromGroup(String fullPath, String memberId) {

        var groupId = getGroupIdByPath(fullPath);

        groupClient.removeMemberFromGroup(groupId, memberId);
    }

    /**
     * Collects groups and identifiers recursively into the provided map.
     *
     * @param group root group
     * @param map group path to identifier map
     */
    private void collectGroupRecursive(Group group, Map<String, String> map) {
        // Path → ID
        map.put(group.path, group.id);

        // ID → defaultConfiguration
        if (group.attributes != null && group.attributes.containsKey("defaultConfiguration")) {
            map.put(group.id, group.attributes.get("defaultConfiguration").get(0));
        }

        if (group.extraSubGroups != null) {
            for (Group child : group.extraSubGroups) {
                collectGroupRecursive(child, map);
            }
        }
    }
}
