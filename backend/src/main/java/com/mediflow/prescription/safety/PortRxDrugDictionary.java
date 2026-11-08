package com.mediflow.prescription.safety;

import com.mediflow.shared.domain.DrugBrand;
import com.mediflow.shared.port.DrugDictionaryPort;
import com.mediflow.shared.port.DrugDictionaryPort.DoseLimitRule;
import com.mediflow.shared.port.DrugDictionaryPort.DrugMatch;
import com.mediflow.shared.port.DrugDictionaryPort.GenericRef;
import com.mediflow.shared.port.DrugDictionaryPort.InteractionRule;
import com.mediflow.shared.repo.DrugBrandRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Thin adapter: prescription safety / item editor talk to {@link RxDrugDictionary}; lookups go
 * through the shared {@link DrugDictionaryPort} (V2-09 when present, else {@link
 * RepositoryDrugDictionaryPort}).
 */
@Component
@Primary
public class PortRxDrugDictionary implements RxDrugDictionary {

  private final DrugDictionaryPort port;
  private final DrugBrandRepository brands;
  private final ObjectProvider<RepositoryDrugDictionaryPort> repositoryPort;

  public PortRxDrugDictionary(
      DrugDictionaryPort port,
      DrugBrandRepository brands,
      ObjectProvider<RepositoryDrugDictionaryPort> repositoryPort) {
    this.port = port;
    this.brands = brands;
    this.repositoryPort = repositoryPort;
  }

  @Override
  public Optional<GenericInfo> generic(UUID genericId) {
    return port.findGeneric(genericId).map(PortRxDrugDictionary::toGeneric);
  }

  @Override
  public Optional<BrandInfo> brand(UUID brandId) {
    if (brandId == null) {
      return Optional.empty();
    }
    RepositoryDrugDictionaryPort local = repositoryPort.getIfAvailable();
    if (local != null) {
      return local.findBrand(brandId).map(PortRxDrugDictionary::toBrand);
    }
    if (brands == null) {
      return Optional.empty();
    }
    return brands.findById(brandId).map(PortRxDrugDictionary::toBrandFromEntity);
  }

  @Override
  public List<BrandInfo> brandsByName(String name) {
    return port.findBrandProducts(name).stream()
        .filter(m -> m.brandId() != null)
        .map(PortRxDrugDictionary::toBrand)
        .toList();
  }

  @Override
  public Optional<GenericInfo> genericByName(String name) {
    return port.findGenericByName(name).map(PortRxDrugDictionary::toGeneric);
  }

  @Override
  public Optional<InteractionInfo> interaction(UUID genericA, UUID genericB) {
    return port.interaction(genericA, genericB).map(PortRxDrugDictionary::toInteraction);
  }

  @Override
  public List<DoseLimitInfo> doseLimits(UUID genericId) {
    return port.doseLimits(genericId).stream().map(PortRxDrugDictionary::toDose).toList();
  }

  @Override
  public List<BrandInfo> searchBrands(String query, int limit) {
    int cap = Math.max(1, limit);
    return port.suggestBrands(query).stream()
        .filter(m -> m.brandId() != null)
        .limit(cap)
        .map(PortRxDrugDictionary::toBrand)
        .toList();
  }

  private static GenericInfo toGeneric(GenericRef g) {
    return new GenericInfo(g.id(), g.name(), g.atcCode());
  }

  private static BrandInfo toBrand(DrugMatch m) {
    return new BrandInfo(
        m.brandId(), m.brandName(), m.genericId(), m.strength(), m.form(), m.dgdaRegNo());
  }

  private static BrandInfo toBrandFromEntity(DrugBrand b) {
    return new BrandInfo(
        b.getId(), b.getName(), b.getGenericId(), b.getStrength(), b.getForm(), b.getDgdaRegNo());
  }

  private static InteractionInfo toInteraction(InteractionRule r) {
    return new InteractionInfo(r.genericAId(), r.genericBId(), r.severity(), r.description());
  }

  private static DoseLimitInfo toDose(DoseLimitRule r) {
    return new DoseLimitInfo(
        r.genericId(), r.population(), r.route(), r.minDose(), r.maxDose(), r.unit());
  }
}
