package com.mediflow.lab.repo;

import com.mediflow.shared.domain.Hospital;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Hospitals the lab booking UI can offer. */
public interface LabHospitalQueries extends Repository<Hospital, UUID> {

  Optional<Hospital> findById(UUID id);

  List<Hospital> findByActiveTrueOrderByNameAsc();
}
