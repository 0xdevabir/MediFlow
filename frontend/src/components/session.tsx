"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { api, UnauthorizedError, type Me } from "@/lib/api";

const SessionContext = createContext<Me | null>(null);

export function useSession(): Me {
  const me = useContext(SessionContext);
  if (!me) throw new Error("useSession outside <SessionProvider>");
  return me;
}

export function SessionProvider({
  children,
  fallback,
}: {
  children: ReactNode;
  fallback: ReactNode;
}) {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .get<Me>("/api/v1/me")
      .then((m) => !cancelled && setMe(m))
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof UnauthorizedError) {
          // MFA is completed on the classic page, which then returns to "/".
          window.location.href = e.mfaRequired ? "/identity/mfa/verify" : "/login";
        } else {
          router.replace("/login?error=unavailable");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [router]);

  if (!me) return <>{fallback}</>;
  return <SessionContext.Provider value={me}>{children}</SessionContext.Provider>;
}
