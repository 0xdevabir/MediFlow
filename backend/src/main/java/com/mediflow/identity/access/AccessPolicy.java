AccessPolicy.java 249L cognitive
// /Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/V2-02-identity-staff-access/backend/src/main/java/com/mediflow/identity/access/AccessPolicy.java
§ function function (L52-L67)
  public boolean canReadLabOrder(UUID labOrderId) {
    if (labOrderId == null) {
      return false;
    }
    Optional<AccessFacts.LabOrderRef> order = facts.labOrder(labOrderId);
    if (order.isEmpty()) {
      return false;
    }
    AccessFacts.LabOrderRef ref = order.get();
    Instant now = clock.now();
    return evaluate(
        AccessResource.LAB_ORDERS,
        AccessAction.R,
        ref.patientId(),
        scope -> scope == AccessScope.LINKED_LAB_ORDER && AccessRules.labOrderHandled(ref, now));
  }
// ... 5 lines omitted
§ function function (L73-L93)
  public boolean canReadPrescription(UUID prescriptionId) {
    if (prescriptionId == null) {
      return false;
    }
    Optional<AccessFacts.PrescriptionRef> rx = facts.prescription(prescriptionId);
    if (rx.isEmpty()) {
      return false;
    }
    AccessFacts.PrescriptionRef ref = rx.get();
    AccessResource resource =
        ref.status() == PrescriptionStatus.DRAFT
            ? AccessResource.DRAFT_PRESCRIPTION
            : AccessResource.SIGNED_PRESCRIPTION;
    return evaluate(
        resource,
        AccessAction.R,
        ref.patientId(),
        scope ->
            scope == AccessScope.LINKED_PRESCRIPTION
                && AccessRules.SIGNED_STATUSES.contains(ref.status()));
  }
// ... 20 lines omitted
§ function function (L114-L133)
  public boolean minimumNecessary(String resource) {
    AccessResource res;
    try {
      res = AccessResource.valueOf(resource);
    } catch (IllegalArgumentException | NullPointerException e) {
      return true;
    }
    Set<Role> roles = currentRoles();
    boolean any = false;
    for (Role role : roles) {
      Optional<AccessMatrix.Cell> cell = AccessMatrix.cell(res, role);
      if (cell.isPresent()) {
        any = true;
        if (!cell.get().minimumNecessary()) {
          return false;
        }
      }
    }
    return any;
  }
// ... 9 lines omitted
§ function function (L143-L170)
  private boolean evaluate(
      AccessResource resource,
      AccessAction action,
      UUID patientId,
      Predicate<AccessScope> objectLink) {
    if (resource == null || action == null) {
      return false;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return false;
    }
    Set<Role> roles = roles(auth);
    if (roles.isEmpty()) {
      return false;
    }
    Context ctx = new Context(auth.getName());
    for (Role role : roles) {
      Optional<AccessMatrix.Cell> cell = AccessMatrix.cell(resource, role);
      if (cell.isEmpty() || !cell.get().actions().contains(action)) {
        continue;
      }
      if (inScope(cell.get().scope(), resource, patientId, objectLink, ctx)) {
        return true;
      }
    }
    return false;
  }
// ... 1 lines omitted
§ function function (L172-L211)
  private boolean inScope(
      AccessScope scope,
      AccessResource resource,
      UUID patientId,
      Predicate<AccessScope> objectLink,
      Context ctx) {
    if (scope == AccessScope.GLOBAL || !resource.patientScoped()) {
      return true;
    }
    if (patientId == null) {
      // List-level question ("may I open the worklist?"): the role holds the cell.
      return true;
    }
    Instant now = clock.now();
    return switch (scope) {
      case GLOBAL -> true;
      case AGGREGATE -> false;
      case STAFF -> true;
      case OWN -> ctx.userId().flatMap(facts::patientIdOfUser).map(patientId::equals).orElse(false);
      case ASSIGNED ->
          ctx.userId()
              .map(
                  uid ->
                      facts.seenByDoctorToday(uid, patientId, now)
                          || facts.activeReferral(uid, patientId, now)
                          || facts.activeBreakGlass(uid, patientId, now))
              .orElse(false);
      case ER_ASSIGNED ->
          ctx.userId()
              .map(
                  uid ->
                      facts.erAssignedOnActiveAlert(uid, patientId)
                          || facts.activeBreakGlass(uid, patientId, now))
              .orElse(false);
      // Object-level checks (one order / one prescription) use only that object's link, so a lab
      // tech cannot read a cancelled order just because the patient has another open one.
      case LINKED_LAB_ORDER ->
          objectLink != null ? objectLink.test(scope) : facts.openLabOrderForPatient(patientId, now);
      case LINKED_PRESCRIPTION ->
          objectLink != null
              ? objectLink.test(scope)
              : facts.signedPrescriptionForPatient(patientId, now);
    };
  }
// ... 1 lines omitted
§ function function (L213-L219)
  private Set<Role> currentRoles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
      return Set.of();
    }
    return roles(auth);
  }
// ... 1 lines omitted
§ function function (L221-L231)
  public static Set<Role> roles(Authentication auth) {
    EnumSet<Role> roles = EnumSet.noneOf(Role.class);
    for (GrantedAuthority authority : auth.getAuthorities()) {
      try {
        roles.add(Role.valueOf(authority.getAuthority()));
      } catch (IllegalArgumentException ignored) {
        // not a MediFlow role (e.g. FACTOR_* authorities)
      }
    }
    return roles;
  }
7/28 chunks shown (1117 tokens)
[lean-ctx] full source: read "/Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/V2-02-identity-staff-access/backend/src/main/java/com/mediflow/identity/access/AccessPolicy.java" directly (no MCP)  ·  or ctx_read("/Users/mdabirhossain/Documents/WebDevelopment/Shared-Startups/MediFlow-wt/V2-02-identity-staff-access/backend/src/main/java/com/mediflow/identity/access/AccessPolicy.java", mode="full")
