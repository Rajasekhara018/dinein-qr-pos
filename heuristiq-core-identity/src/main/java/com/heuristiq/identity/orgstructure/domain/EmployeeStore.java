package com.heuristiq.identity.orgstructure.domain;

import java.util.List;
import java.util.Optional;

import com.heuristiq.identity.orgstructure.domain.Models.Employee;

/** Port to employee records (people in the organization, with or without a login). */
public interface EmployeeStore {
    Employee insert(String tenantId, Employee employee);

    boolean update(String tenantId, Employee employee);

    Optional<Employee> find(String tenantId, String id);

    Optional<Employee> findByCode(String tenantId, String employeeCode);

    /** Keyset-paginated by id; {@code search} matches code, name or email (prefix, case-insensitive). */
    List<Employee> list(String tenantId, String search, String afterId, int limit);

    /** As {@link #list(String, String, String, int)}, only people assigned to the given departments. */
    List<Employee> list(String tenantId, String search, String afterId, int limit, java.util.Set<String> onlyUnits);
}
