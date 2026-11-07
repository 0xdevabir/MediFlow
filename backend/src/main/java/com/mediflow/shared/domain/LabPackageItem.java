package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "lab_package_item")
@IdClass(LabPackageItem.Pk.class)
public class LabPackageItem {

  @Id
  @Column(name = "package_id", nullable = false)
  private UUID packageId;

  @Id
  @Column(name = "test_id", nullable = false)
  private UUID testId;

  public UUID getPackageId() {
    return packageId;
  }

  public void setPackageId(UUID packageId) {
    this.packageId = packageId;
  }

  public UUID getTestId() {
    return testId;
  }

  public void setTestId(UUID testId) {
    this.testId = testId;
  }

  public static class Pk implements Serializable {
    private UUID packageId;
    private UUID testId;

    public Pk() {
    }

    public Pk(UUID packageId, UUID testId) {
      this.packageId = packageId;
      this.testId = testId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(packageId, pk.packageId) && Objects.equals(testId, pk.testId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(packageId, testId);
    }
  }
}
