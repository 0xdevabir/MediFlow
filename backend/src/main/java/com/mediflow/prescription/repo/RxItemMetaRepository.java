package com.mediflow.prescription.repo;

import com.mediflow.prescription.domain.RxItemMeta;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RxItemMetaRepository extends JpaRepository<RxItemMeta, UUID> {
  Optional<RxItemMeta> findByItemId(UUID itemId);

  List<RxItemMeta> findByItemIdIn(Collection<UUID> itemIds);

  /** Rows of [outcome, count] for AI items decided at signing. */
  @Query(
      "select m.aiOutcome, count(m) from RxItemMeta m where m.source ="
          + " com.mediflow.prescription.domain.ItemSource.AI and m.aiOutcome is not null"
          + " group by m.aiOutcome")
  List<Object[]> countAiOutcomes();
}
