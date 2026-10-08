# Play Console answers for Field

What to enter in each Play Console form, so whoever publishes the next version doesn't have to
work it out again. The listing text and images are in `metadata/android/en-US/`. Reviewed against
Field 1.0.x (October 2026). Check the answers again if the app gains features that send data anywhere.

## Main store listing (Grow users → Store presence → Main store listing)

| Field | Value |
|---|---|
| App name | `metadata/android/en-US/title.txt` |
| Short description | `short_description.txt` |
| Full description | `full_description.txt` |
| App icon (512×512) | `images/icon.png` |
| Feature graphic (1024×500) | `images/featureGraphic.png` |
| Phone screenshots | `images/phoneScreenshots/1.png` … `6.png`, in that order |

**Store settings:** Category **Health & Fitness**. Contact email `fieldapp.support@gmail.com`.
Website `https://vamshigunda-star.github.io/Field/`.

## App content (Policy and programs → App content)

**Privacy policy:** `https://vamshigunda-star.github.io/Field/privacy`

**App access:** *All functionality in my app is available without any access restrictions.*
First launch asks only for a coach name. Google Drive backup uses the reviewer's own Google
account; no credentials are needed.

**Ads:** *No, my app does not contain ads.*

**Content rating** (questionnaire):
- Category: *All Other App Types*
- Violence, sexuality, language, controlled substances, crude humour, gambling: **No** to all
- Users can interact or exchange content with each other: **No**
- Shares the user's current location: **No**
- Digital purchases: **No**
- Expected rating: Everyone / PEGI 3

**Target audience:** **18 and over** only. The app is for coaches and teachers. They may record
data about children, but children are not the users. *Appeals to children: No.*

**Government app:** No. **Financial features:** none. **News app:** No.

**Health apps declaration:** select **Activity and fitness** (fitness tracking and coaching).
Field is not a medical device and makes no diagnosis.

**Advertising ID:** *No.* The app doesn't declare the `AD_ID` permission. Run
`aapt2 dump permissions` on a release build to confirm after dependency upgrades.

## Data safety

Field has no server. Data leaves the device only when the coach chooses Google Drive backup, and
then it goes to the coach's own Drive app folder. Under Google's definitions that still counts as
**collected**, so it is declared as collected, not shared, and optional.

1. **Does your app collect or share any of the required user data types?** Yes
2. **Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS to Google APIs)
3. **Which account creation methods does your app support?** *My app does not allow users to
   create an account.* The coach profile is local only, and Google sign-in is used only to reach Drive.
4. **Data types.** For every type below, set: Collected **Yes**, Shared **No**, Processed
   ephemerally **No**, Collection **Optional** (users can choose), Purpose **App functionality**.
   - Personal info → **Name** (coach and athletes)
   - Personal info → **Email address** (optional coach and athlete email)
   - Personal info → **Other info** (athletes' date of birth and sex)
   - Health and fitness → **Health info** (optional medical-alert notes)
   - Health and fitness → **Fitness info** (test results)
5. **Can users request that their data is deleted?** Yes. Uninstalling removes local data, and
   Drive backups can be deleted from Drive → Settings → Manage apps (described in the privacy policy).

## Testing (Test and release → Testing → Closed testing)

New personal developer accounts need **12 or more testers opted in for 14 consecutive days** before
Production unlocks. Add the testers' Google Group email under *Testers*, upload the `.aab`, and
share the opt-in link. Invite 15–20 people so a few dropping out doesn't restart the 14 days.

## App signing

On the first upload, **don't** let Google generate a new app signing key. Choose to use the
existing key (`D:\Keys\field-upload.jks`, alias `upload`) via the PEPK export option. That keeps
Play builds and GitHub APKs on the same signature (they can update each other) and keeps the
existing Drive OAuth client valid.
