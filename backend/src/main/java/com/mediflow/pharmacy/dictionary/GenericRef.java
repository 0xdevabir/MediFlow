package com.mediflow.pharmacy.dictionary;

import java.io.Serializable;
import java.util.UUID;

/** A generic (INN) with its codes. Safety checks run at this level. */
public record GenericRef(
    UUID id, String name, String atcCode, String rxnormCode, boolean illustrative)
    implements Serializable {}
