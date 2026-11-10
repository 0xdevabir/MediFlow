import type { Metadata } from "next";
import { IBM_Plex_Sans, IBM_Plex_Mono, Noto_Sans_Bengali } from "next/font/google";
import "./globals.css";

const plexSans = IBM_Plex_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-plex-sans",
});
const plexMono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-plex-mono",
});
const bengali = Noto_Sans_Bengali({
  subsets: ["bengali"],
  weight: ["400", "500", "600"],
  variable: "--font-bengali",
});

export const metadata: Metadata = {
  title: { default: "MediFlow", template: "%s · MediFlow" },
  description: "Hospital workflow: patients, queue, consultations, prescriptions, lab and pharmacy.",
};

// Applies the saved/system theme before paint so there's no light→dark flash.
const themeScript = `(()=>{try{var t=localStorage.getItem('mf-theme');if(!t)t=matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';document.documentElement.dataset.theme=t}catch(e){}})()`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body className={`${plexSans.variable} ${plexMono.variable} ${bengali.variable} font-sans`}>
        {children}
      </body>
    </html>
  );
}
