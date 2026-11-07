package com.mediflow.appointment.support;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ApptUserRepository extends Repository<AppUser, UUID> {

  Optional<AppUser> findById(UUID id);

  @Query("select distinct u from AppUser u join u.roles r where r = :role and u.enabled = true")
  List<AppUser> findEnabledByRole(@Param("role") Role role);
}
