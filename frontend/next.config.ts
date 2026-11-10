import type { NextConfig } from "next";

/**
 * MediFlow frontend proxies to the Spring backend so the session + CSRF cookies stay same-origin.
 *  - /api/**           JSON APIs
 *  - /auth/login|logout → Spring's form-login endpoints (POST)
 *  - fallback          any route this app doesn't implement yet is served by the classic
 *                      Thymeleaf UI, so every module keeps working while it's migrated.
 */
const BACKEND = process.env.MEDIFLOW_BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return {
      beforeFiles: [
        { source: "/api/:path*", destination: `${BACKEND}/api/:path*` },
        { source: "/auth/login", destination: `${BACKEND}/login` },
        { source: "/auth/logout", destination: `${BACKEND}/logout` },
      ],
      afterFiles: [],
      fallback: [{ source: "/:path*", destination: `${BACKEND}/:path*` }],
    };
  },
};

export default nextConfig;
