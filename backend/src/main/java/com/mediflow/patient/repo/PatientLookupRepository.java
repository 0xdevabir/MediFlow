package com.mediflow.patient.repo;

import com.mediflow.shared.domain.Patient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Patient-module queries over the shared {@code patient} table. */
public interface PatientLookupRepository extends JpaRepository<Patient, UUID> {

  Optional<Patient> findByUserId(UUID userId);

  Optional<Patient> findByMrn(String mrn);

  @Query(
      "select p from Patient p where lower(p.mrn) = lower(:q)"
          + " or lower(p.givenName) like lower(concat('%', :q, '%'))"
          + " or lower(p.familyName) like lower(concat('%', :q, '%'))"
          + " order by p.familyName, p.givenName")
  List<Patient> search(@Param("q") String q, Pageable page);
}
