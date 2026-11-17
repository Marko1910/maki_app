// Staging is the default. A production build requires its own values (see vite.config.ts).
// The anon key is meant to ship; RLS protects the data (same as data/SupabaseConfig.kt).
const STAGING_URL = "https://mbbdrmicjobydprvbpsj.supabase.co";
const STAGING_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1iYmRybWljam9ieWRwcnZicHNqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODI0MzI0NzAsImV4cCI6MjA5ODAwODQ3MH0.i77TIvFugdf3o3fF1NT-Z429sI7t-vW_1appE3YFZmU";

export const SUPABASE_URL: string = import.meta.env.VITE_SUPABASE_URL ?? STAGING_URL;
export const SUPABASE_ANON_KEY: string = import.meta.env.VITE_SUPABASE_ANON_KEY ?? STAGING_ANON_KEY;
export const IS_STAGING = SUPABASE_URL === STAGING_URL;

// Web Push public key (VAPID) of the project this build talks to; its private half is a secret of the
// send-push function. scripts/setup-push-secrets.sh writes it to .env.staging.local and .env.development.local.
// Empty means this build offers no push (components/Push.tsx).
export const VAPID_PUBLIC_KEY: string = import.meta.env.VITE_VAPID_PUBLIC_KEY ?? "";
