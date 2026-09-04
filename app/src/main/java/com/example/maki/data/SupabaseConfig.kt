package com.example.maki.data

// STAGING project. The anon/publishable key is safe to ship in the client —
// Row Level Security on the database is what actually protects the data.
// Swap these for the PRODUCTION project only after testing in staging.
object SupabaseConfig {
    const val URL = "https://mbbdrmicjobydprvbpsj.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1iYmRybWljam9ieWRwcnZicHNqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODI0MzI0NzAsImV4cCI6MjA5ODAwODQ3MH0.i77TIvFugdf3o3fF1NT-Z429sI7t-vW_1appE3YFZmU"

    const val REST = "$URL/rest/v1"
    const val AUTH = "$URL/auth/v1"
}
