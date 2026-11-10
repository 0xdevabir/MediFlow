import {

  Building2,
  CalendarDays,
  ClipboardList,
  FlaskConical,
  LayoutDashboard,
  ListOrdered,
  Pill,
  PhoneCall,
  ScrollText,
  ShieldCheck,
  Siren,
  Stethoscope,
  UserCog,
  Users,
  type LucideIcon,
} from "lucide-react";
import type { Role } from "./api";

export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  /** Roles that see the item; omitted = every signed-in user. */
  roles?: Role[];
  /** Rendered by this Next app. Others are still served by the classic UI through the proxy. */
  native?: boolean;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

// Mirrors the role rules of the classic navbar (templates/layout/base.html).
export const NAV: NavGroup[] = [
  {
    label: "Overview",
    items: [
      { label: "Dashboard", href: "/", icon: LayoutDashboard, native: true },
      {
        label: "Live queue",
        href: "/queue",
        icon: ListOrdered,
        roles: ["FRONT_DESK", "NURSE", "DOCTOR", "ADMIN"],
        native: true,
      },
    ],
  },
  {
    label: "Care",
    items: [
      { label: "Patients", href: "/patients", icon: Users, roles: ["PATIENT", "FRONT_DESK", "ADMIN"] },
      {
        label: "Appointments",
        href: "/appointments",
        icon: CalendarDays,
        roles: ["PATIENT", "FRONT_DESK", "DOCTOR", "NURSE", "ADMIN"],
      },
      { label: "Consultations", href: "/clinical/consultations", icon: Stethoscope, roles: ["DOCTOR", "NURSE", "ADMIN"] },
      { label: "Emergency", href: "/er", icon: Siren, roles: ["ER", "ADMIN"] },
      {
        label: "Staff call list",
        href: "/notifications/call-list",
        icon: PhoneCall,
        roles: ["FRONT_DESK", "NURSE", "ER", "ADMIN"],
      },
    ],
  },
  {
    label: "Orders",
    items: [
      { label: "Prescriptions", href: "/prescriptions", icon: ClipboardList, roles: ["DOCTOR", "PHARMACIST", "ADMIN"] },
      {
        label: "Lab",
        href: "/lab",
        icon: FlaskConical,
        roles: ["LAB_TECH", "DOCTOR", "PATIENT", "FRONT_DESK", "ADMIN"],
      },
      { label: "Pharmacy", href: "/pharmacy", icon: Pill, roles: ["PHARMACIST", "ADMIN"] },
    ],
  },
  {
    label: "Organisation",
    items: [
      { label: "Directory", href: "/directory", icon: Building2 },
      { label: "Staff users", href: "/identity/staff", icon: UserCog, roles: ["ADMIN"] },
      { label: "Admin & audit", href: "/admin", icon: ShieldCheck, roles: ["ADMIN", "CLINICAL_APPROVER"] },
      { label: "My access log", href: "/audit/me", icon: ScrollText },
    ],
  },
];

export function navFor(roles: Role[]): NavGroup[] {
  return NAV.map((g) => ({
    ...g,
    items: g.items.filter((i) => !i.roles || i.roles.some((r) => roles.includes(r))),
  })).filter((g) => g.items.length > 0);
}

export const ROLE_LABEL: Record<Role, string> = {
  PATIENT: "Patient",
  FRONT_DESK: "Front desk",
  NURSE: "Nurse",
  DOCTOR: "Doctor",
  ER: "Emergency",
  LAB_TECH: "Lab technician",
  PHARMACIST: "Pharmacist",
  ADMIN: "Administrator",
  CLINICAL_APPROVER: "Clinical approver",
};

