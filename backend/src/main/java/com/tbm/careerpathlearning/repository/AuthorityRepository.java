package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.model.Authority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AuthorityRepository extends JpaRepository<Authority, Long> {

    Optional<Authority> findByName(AuthorityName Name);

    List<Authority> findAllByNameIn(Set<AuthorityName> names);
}