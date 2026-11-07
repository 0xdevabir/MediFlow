package com.mediflow.identity.mfa;

import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Role;
import jakarta.servlet.http.HttpSession;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Decides per session whether the signed-in user still has to enrol in or pass TOTP. */
@Component
public class MfaRequirementResolver {

  public enum Requirement {
    NONE,
    ENROL,
    VERIFY
  }

  private final MfaService mfa;

  public MfaRequirementResolver(MfaService mfa) {
    this.mfa = mfa;
  }

  public Requirement resolve(HttpSession session, String username, Set<Role> roles) {
    if (session != null
        && username.equals(session.getAttribute(MfaSession.REQUIRED_FOR))
        && session.getAttribute(MfaSession.REQUIRED) instanceof String cached) {
      return Requirement.valueOf(cached);
    }
    Requirement result = compute(username, roles);
    if (session != null && result == Requirement.NONE) {
      session.setAttribute(MfaSession.REQUIRED_FOR, username);
      session.setAttribute(MfaSession.REQUIRED, result.name());
    }
    return result;
  }

  Requirement compute(String username, Set<Role> roles) {
    AppUser user = mfa.user(username).orElse(null);
    if (user == null) {
      // Unknown principal: deny by default (force the verify page, which will not succeed).
      return Requirement.VERIFY;
    }
    boolean optedIn = user.getMfaEnabled();
    if (!MfaService.requiresMfa(roles, optedIn)) {
      return Requirement.NONE;
    }
    return mfa.hasVerifiedSecret(user.getId()) ? Requirement.VERIFY : Requirement.ENROL;
  }
}
