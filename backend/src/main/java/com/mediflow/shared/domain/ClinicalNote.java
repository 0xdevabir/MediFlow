package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "clinical_note")
public class ClinicalNote extends BaseEntity {

  @Column(name = "encounter_id", nullable = false)
  private UUID encounterId;

  @Column(name = "note_version", nullable = false)
  private int noteVersion;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "author_id")
  private UUID authorId;

  @Column(name = "supersedes_id")
  private UUID supersedesId;

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID encounterId) {
    this.encounterId = encounterId;
  }

  public int getNoteVersion() {
    return noteVersion;
  }

  public void setNoteVersion(int noteVersion) {
    this.noteVersion = noteVersion;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public void setAuthorId(UUID authorId) {
    this.authorId = authorId;
  }

  public UUID getSupersedesId() {
    return supersedesId;
  }

  public void setSupersedesId(UUID supersedesId) {
    this.supersedesId = supersedesId;
  }

}
