package com.heuristiq.uam.orgstructure.adapter.out;

import com.heuristiq.uam.common.UamCommonConstants;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.heuristiq.uam.orgstructure.application.OrgStructureChanged;
import com.heuristiq.uam.orgstructure.domain.EmployeeStore;
import com.heuristiq.uam.orgstructure.domain.Models.Employee;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.uam.search.UserSearchIndexer;
import com.heuristiq.uam.users.repository.UamUserRepository;

/**
 * After the structure is published, refreshes the search entries of the organization's people so the department in each
 * entry is current. Reads the organization's employees page by page; a failure is logged and never reaches the publish.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class UserDepartmentRefresher {
    private static final Logger log = LoggerFactory.getLogger(UserDepartmentRefresher.class);

    private final EmployeeStore employees;
    private final UamUserRepository users;
    private final UserSearchIndexer indexer;

    public UserDepartmentRefresher(EmployeeStore employees, UamUserRepository users, UserSearchIndexer indexer) {
        this.employees = employees;
        this.users = users;
        this.indexer = indexer;
    }

    @Async(UamCommonConstants.NOTIFICATION_EXECUTOR)
    @EventListener
    public void onStructureChanged(OrgStructureChanged event) {
        try {
            String after = null;
            int refreshed = 0;
            List<Employee> page;
            do {
                page = employees.list(event.tenantId(), null, after, OrgStructureConstants.MAX_PAGE_SIZE);
                List<String> userIds = new ArrayList<>();
                page.forEach(e -> {
                    if (e.userId() != null) {
                        userIds.add(e.userId());
                    }
                });
                for (var user : users.findAllByIds(userIds)) {
                    indexer.republish(user);
                    refreshed++;
                }
                after = page.isEmpty() ? null : page.get(page.size() - 1).id();
            } while (page.size() >= OrgStructureConstants.MAX_PAGE_SIZE);
            log.info("Refreshed the department of {} users of organization {}", refreshed, event.tenantId());
        } catch (RuntimeException e) {
            log.warn("Could not refresh users' departments in the search index for organization {}: {}", event.tenantId(), e.getMessage());
        }
    }
}
