import { NextResponse, type NextRequest } from "next/server";

/** Pages owned by this app that need a signed-in user. Everything else is proxied or public. */
const PROTECTED = ["/", "/queue"];

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  const isProtected = PROTECTED.some((p) =>
    p === "/" ? pathname === "/" : pathname === p || pathname.startsWith(`${p}/`),
  );
  if (isProtected && !req.cookies.has("SESSION")) {
    const url = req.nextUrl.clone();
    url.pathname = "/login";
    url.search = pathname === "/" ? "" : `?next=${encodeURIComponent(pathname)}`;
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = { matcher: ["/", "/queue/:path*"] };
