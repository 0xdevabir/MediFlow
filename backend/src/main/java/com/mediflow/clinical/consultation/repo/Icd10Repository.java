package com.mediflow.clinical.consultation.repo;

import com.mediflow.clinical.consultation.domain.Icd10Entity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Icd10Repository extends JpaRepository<Icd10Entity, String> {

  @Query(
      """
      select i from Icd10Entity i
      where i.active = true
        and (lower(i.code) like lower(concat('%', :q, '%'))
          or lower(i.displayEn) like lower(concat('%', :q, '%'))
          or lower(coalesce(i.displayBn, '')) like lower(concat('%', :q, '%')))
      order by i.code
      """)
  List<Icd10Entity> search(@Param("q") String q);
}
