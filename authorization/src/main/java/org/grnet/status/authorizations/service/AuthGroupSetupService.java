package org.grnet.status.authorizations.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.grnet.status.authorizations.groups.AuthGroupManagement;
import org.grnet.status.authorizations.groups.GroupManagement;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for asynchronously creating and deleting authorization groups.
 */
@ApplicationScoped
public class AuthGroupSetupService {

    private static final Logger LOG = Logger.getLogger(AuthGroupSetupService.class);

    @Inject
    AuthGroupManagement groupManagement;

    @Inject
    ManagedExecutor executor;

    /**
     * Asynchronously creates a group under the specified parent path.
     *
     * @param parentPath parent group path
     * @param name group name
     * @param roles roles to assign
     * @param attributes group attributes
     */
    public void createGroup(String parentPath, String name, List<String> roles, Map<String, List<String>> attributes) {

        executor.runAsync(() -> {
            try {
                groupManagement.createGroup(parentPath, name, roles, attributes);
                LOG.infof("Async group created: %s/%s", parentPath, name);
            } catch (Exception e) {
                LOG.errorf("Async group creation failed for %s/%s: %s",
                        parentPath, name, e.getMessage());
            }
        });
    }

    /**
     * Asynchronously deletes a group by its full path.
     *
     * @param fullPath full group path
     */
    public void deleteGroup(String fullPath) {

        executor.runAsync(() -> {
            try {
                groupManagement.deleteGroup(fullPath);
                LOG.infof("Async group deleted: %s", fullPath);
            } catch (Exception e) {
                LOG.errorf(
                        "Async group deletion failed for %s: %s",
                        fullPath, e.getMessage()
                );
            }
        });
    }
}
