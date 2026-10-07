package com.vamshi.field.ui.help

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.vamshi.field.R
import com.vamshi.field.domain.model.reports.PerformanceThresholds

/**
 * Content for the "How to use Field" page. Edit the copy here, not in [HowToUseScreen].
 *
 * Every step names a real control in the app (tab, button or field label). If you rename one
 * of those controls, update the matching step so the guide doesn't send a coach looking for a
 * button that no longer exists.
 *
 * ## Adding a screenshot
 * Each guide shows one screenshot. Until a real image exists, the page draws a labelled
 * placeholder naming the file it expects. To replace it:
 *  1. Capture the screen named in [GuideScreenshot.whatToCapture] on a phone, light theme.
 *  2. Crop away the status and navigation bars, resize to about 720 px wide and save it as
 *     WebP or PNG named exactly [GuideScreenshot.fileName].
 *  3. Put it in `app/src/main/res/drawable-nodpi/` (nodpi stops Android rescaling it).
 *  4. Set `drawableRes = R.drawable.<fileName without extension>` on that guide below.
 */
data class Guide(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val steps: List<GuideStep>,
    val screenshot: GuideScreenshot,
)

data class GuideStep(
    val title: String,
    val body: String,
    /** Shows the three performance-zone chips under this step. */
    val showZoneLegend: Boolean = false,
)

data class GuideScreenshot(
    /** Expected file in `res/drawable-nodpi/`. Shown on the placeholder until [drawableRes] is set. */
    val fileName: String,
    /** Which screen to capture. Shown on the placeholder so whoever adds it knows what to take. */
    val whatToCapture: String,
    /** Short caption shown under the image. */
    val caption: String,
    /** Read aloud by TalkBack. Describe what the screenshot shows, not that it is a screenshot. */
    val contentDescription: String,
    @param:DrawableRes val drawableRes: Int? = null,
)

object HowToUseGuides {

    const val INTRO = "Get started with these three simple guides."

    val all: List<Guide> = listOf(
        Guide(
            id = "add_athletes",
            title = "Add athletes",
            summary = "Create a group, register athletes and build your roster.",
            icon = Icons.Default.Groups,
            steps = listOf(
                GuideStep(
                    title = "Create a group",
                    body = "Open the Roster tab and switch to Groups. Tap + , enter a group name " +
                        "(for example \"Grade 8A\") and tap Create Group.",
                ),
                GuideStep(
                    title = "Register athletes",
                    body = "Switch to the Athletes tab and tap + . Fill in the athlete's details, " +
                        "add any medical alert, then tap Register Athlete.",
                ),
                GuideStep(
                    title = "Add them to the group",
                    body = "Back on Groups, tap the arrow on the group's card to open it, then tap " +
                        "Add Athlete, tick the athletes who belong to it and tap Done.",
                ),
                GuideStep(
                    title = "View your roster",
                    body = "The Athletes tab lists everyone you've registered. Open a group card to " +
                        "see its members. Athletes with a medical alert show a red warning sign.",
                ),
            ),
            screenshot = GuideScreenshot(
                fileName = "guide_add_athletes.webp",
                whatToCapture = "Roster › Groups tab with one group card opened to show its members",
                caption = "Groups live on the Roster tab. Open a card and tap Add Athlete to fill it.",
                contentDescription = "The Roster screen on the Groups tab. The Varsity Football " +
                    "card is open, listing four athletes with an Add Athlete button below them.",
                drawableRes = R.drawable.guide_add_athletes,
            ),
        ),
        Guide(
            id = "record_test",
            title = "Record a test",
            summary = "Start a testing event, pick tests and save results.",
            icon = Icons.Default.Timer,
            steps = listOf(
                GuideStep(
                    title = "Start a testing event",
                    body = "On Home, tap Start Group Testing Event. Choose the athlete group and " +
                        "give the event a name.",
                ),
                GuideStep(
                    title = "Choose tests",
                    body = "Under Select Tests, open a category and tick the tests you'll run. " +
                        "Tap Start Group Testing.",
                ),
                GuideStep(
                    title = "Enter results",
                    body = "Pick a test from the tabs along the top, then tap Enter Result next to " +
                        "an athlete. Choose a timer or Manual Keypad Entry and record the score.",
                ),
                GuideStep(
                    title = "Save",
                    body = "Tap Save Results when you're done. To finish an event another day, open " +
                        "Reports › Event Report, choose the event and tap Resume testing.",
                ),
            ),
            screenshot = GuideScreenshot(
                fileName = "guide_record_test.webp",
                whatToCapture = "Testing screen for a group event, one test tab selected",
                caption = "One tab per test. Tap Enter Result beside each athlete, then Save Results.",
                contentDescription = "The testing screen for an event. Test tabs run along the " +
                    "top, each athlete has an Enter Result button, and a Save Results button " +
                    "sits at the bottom.",
                drawableRes = R.drawable.guide_record_test,
            ),
        ),
        Guide(
            id = "understand_results",
            title = "Understand results",
            summary = "Open an athlete's report, read the zones and compare.",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            steps = listOf(
                GuideStep(
                    title = "Open an athlete's report",
                    body = "Open the Reports tab and stay on Athlete Profile. Use the name at the " +
                        "top to switch between athletes.",
                ),
                GuideStep(
                    title = "Read the performance zones",
                    body = "Each result is compared with published norms for athletes like them. " +
                        "Green is Superior (${PerformanceThresholds.SUPERIOR_MIN}th percentile " +
                        "and above), yellow is Healthy " +
                        "(${PerformanceThresholds.HEALTHY_MIN}th–${PerformanceThresholds.SUPERIOR_MIN - 1}th) " +
                        "and red is Needs Improvement (below ${PerformanceThresholds.HEALTHY_MIN}th). " +
                        "Grey means no norm exists for that athlete's age.",
                    showZoneLegend = true,
                ),
                GuideStep(
                    title = "Compare results",
                    body = "Tap a test to see the latest result, its history across events and how " +
                        "it compares with peers. For a whole group, use the Event Report tab.",
                ),
            ),
            screenshot = GuideScreenshot(
                fileName = "guide_understand_results.webp",
                whatToCapture = "Reports › Athlete Profile for an athlete with coloured results",
                caption = "The Skill Matrix colours each area by zone. Scroll down for every test.",
                contentDescription = "An athlete's profile on the Reports screen. A radar chart " +
                    "shows each fitness area, with percentiles coloured green, yellow or red.",
                drawableRes = R.drawable.guide_understand_results,
            ),
        ),
    )
}
