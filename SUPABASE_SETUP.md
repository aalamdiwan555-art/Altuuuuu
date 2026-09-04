# Supabase setup

1. Create or open the Supabase project connected to this repository.
2. Run `supabase/schema.sql` in the Supabase SQL editor.
3. Create the administrator account in Supabase Auth using the admin email and a password you choose. The password is never stored in this repository.
4. Run the final `update public.profiles ...` statement from the SQL file to mark that account as an administrator.
5. In the GitHub repository settings, add these Actions secrets:
   - `SUPABASE_URL`: the project URL, such as `https://your-project.supabase.co`
   - `SUPABASE_ANON_KEY`: the public anon key from Supabase project API settings
6. Push to GitHub. The `Build APK` workflow will build and publish the debug APK as a downloadable workflow artifact.

The app always checks the current approval state and subscription expiry before starting a scenario. The database functions enforce that only an administrator can approve or decline users.