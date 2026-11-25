package com.heuristiq.identity.users.repository;

import com.heuristiq.identity.users.UamUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UamUserRepository {
    UamUser save(UamUser user);

    Optional<UamUser> findById(String id);

    /** The users with these ids in one query; unknown ids are simply absent. */
    List<UamUser> findAllByIds(Collection<String> ids);

    List<UamUser> findAll(Sort sort);

    Page<UamUser> findAll(Pageable pageable);

    void delete(UamUser user);

    Optional<UamUser> findByUserNameIgnoreCase(String userName);

    Optional<UamUser> findByEmailIgnoreCase(String email);

    /**
     * Finds the account owning the phone number {@code rawPhone} (E.164 or national form in the default region), matching
     * both accounts stored in the international form and legacy bare-number rows; see
     * {@link com.heuristiq.identity.helper.PhoneIdentity}. Empty when blank or nothing matches.
     */
    Optional<UamUser> findByPhone(String rawPhone);

    boolean existsByUserNameIgnoreCase(String userName);

    /** Filtered + paged query executed in the database (see {@link UamUserSearchCriteria}). */
    Page<UamUser> search(UamUserSearchCriteria criteria, Pageable pageable);

    /** Database-side count of users matching {@code criteria}. */
    long count(UamUserSearchCriteria criteria);
}
