package tw.chehu.displayfaultsimulator

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

class OnlineUpdateActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var currentVersionView: TextView
    private lateinit var latestVersionView: TextView
    private lateinit var statusView: TextView
    private lateinit var releaseNotesView: TextView
    private lateinit var retryButton: Button
    private lateinit var releaseButton: Button
    private var releaseInfo: ReleaseInfo? = null
    private var receiverRegistered = false
    private var verifyingDownload = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == preferences().getLong(KEY_DOWNLOAD_ID, -1L)) checkDownloadedApk(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        registerDownloadReceiver()
        if (!checkDownloadedApk(false)) checkLatestRelease()
    }

    override fun onResume() {
        super.onResume()
        resumePendingInstall()
        checkDownloadedApk(false)
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(downloadReceiver)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildContent(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(245, 247, 246))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(32))
        }
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(textView(getString(R.string.online_update_title), 25f, Color.rgb(13, 27, 20), Typeface.BOLD),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(button(getString(R.string.action_done), secondary = true) { finish() },
                LinearLayout.LayoutParams(dp(100), dp(52)))
        }, marginBottom())

        content.addView(card().apply {
            addView(body(getString(R.string.online_update_description)))
            currentVersionView = valueText(getString(R.string.update_current_version, readCurrentVersion()))
            addView(currentVersionView)
            latestVersionView = valueText(getString(R.string.update_latest_version, getString(R.string.update_checking)))
            addView(latestVersionView)
            statusView = valueText(getString(R.string.update_connecting)).apply {
                setTextColor(Color.rgb(82, 95, 88)); setPadding(0, dp(16), 0, dp(4))
            }
            addView(statusView)
            retryButton = button(getString(R.string.update_retry), secondary = false) { checkLatestRelease() }.apply {
                isEnabled = false
            }
            addView(retryButton, buttonParams())
            releaseButton = button(getString(R.string.update_open_release), secondary = true) { openReleasePage() }.apply {
                isEnabled = false
            }
            addView(releaseButton, buttonParams())
        }, marginBottom())

        content.addView(card().apply {
            addView(sectionTitle(getString(R.string.update_release_notes)))
            releaseNotesView = body(getString(R.string.update_loading))
            addView(releaseNotesView)
        }, marginBottom())

        content.addView(body(getString(R.string.update_security_notice)))
        scroll.addView(content)
        return scroll
    }

    private fun checkLatestRelease() {
        status(getString(R.string.update_connecting), STATUS_NEUTRAL)
        latestVersionView.text = getString(R.string.update_latest_version, getString(R.string.update_checking))
        releaseNotesView.text = getString(R.string.update_loading)
        retryButton.isEnabled = false
        releaseButton.isEnabled = false
        releaseInfo = null
        executor.execute {
            runCatching { fetchLatestRelease() }
                .onSuccess { runOnUiThread { showRelease(it) } }
                .onFailure { error -> runOnUiThread { showCheckError(error) } }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "Display-Fault-Simulator-Android-Updater")
        try {
            val responseCode = connection.responseCode
            val response = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) error("GitHub HTTP $responseCode")
            val root = JSONObject(response)
            val tag = root.optString("tag_name").trim()
            val pageUrl = root.optString("html_url").trim()
            if (tag.isEmpty() || pageUrl.isEmpty()) error("Incomplete GitHub Release data")
            val assets = root.optJSONArray("assets")
            var selected: JSONObject? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val candidate = assets.optJSONObject(index) ?: continue
                    val name = candidate.optString("name")
                    if (!name.lowercase(Locale.ROOT).endsWith(".apk") || name.contains("unsigned", true)) continue
                    if (selected == null || name.contains("displayfaultsimulator", true)) selected = candidate
                    if (name.contains("displayfaultsimulator", true)) break
                }
            }
            return ReleaseInfo(
                tag = tag,
                pageUrl = pageUrl,
                notes = root.optString("body").trim(),
                publishedAt = root.optString("published_at").trim(),
                apkName = selected?.optString("name").orEmpty(),
                apkUrl = selected?.optString("browser_download_url").orEmpty(),
                apkSize = selected?.optLong("size") ?: 0L,
                apkDigest = selected?.optString("digest").orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun showRelease(info: ReleaseInfo) {
        releaseInfo = info
        latestVersionView.text = getString(R.string.update_latest_version, info.tag)
        releaseButton.isEnabled = true
        releaseNotesView.text = buildString {
            append(info.notes.ifEmpty { getString(R.string.update_no_release_notes) })
            if (info.publishedAt.isNotEmpty()) append("\n\n").append(getString(R.string.update_published_at, info.publishedAt))
            if (info.apkName.isNotEmpty()) append("\n").append(getString(R.string.update_apk_asset, info.apkName, formatSize(info.apkSize)))
        }
        val comparison = compareVersions(normalizeVersion(info.tag), normalizeVersion(readCurrentVersion()))
        when {
            comparison <= 0 -> {
                status(
                    getString(if (comparison == 0) R.string.update_already_latest else R.string.update_local_newer),
                    STATUS_SUCCESS
                )
                retryButton.text = getString(R.string.update_check_again)
                retryButton.isEnabled = true
            }
            info.apkUrl.isEmpty() -> {
                status(getString(R.string.update_apk_missing), STATUS_ERROR)
                retryButton.text = getString(R.string.update_open_release)
                retryButton.setOnClickListener { openReleasePage() }
                retryButton.isEnabled = true
            }
            else -> startDownload(info)
        }
    }

    private fun showCheckError(error: Throwable) {
        status(getString(R.string.update_check_failed), STATUS_ERROR)
        latestVersionView.text = getString(R.string.update_latest_version, getString(R.string.update_unavailable))
        releaseNotesView.text = error.message ?: error.javaClass.simpleName
        retryButton.text = getString(R.string.update_retry)
        retryButton.setOnClickListener { checkLatestRelease() }
        retryButton.isEnabled = true
    }

    private fun startDownload(info: ReleaseInfo) {
        val downloadUri = Uri.parse(info.apkUrl)
        if (!downloadUri.scheme.equals("https", true)) {
            status(getString(R.string.update_https_only), STATUS_ERROR)
            retryButton.isEnabled = true
            return
        }
        val existingId = preferences().getLong(KEY_DOWNLOAD_ID, -1L)
        if (existingId >= 0 && checkDownloadedApk(false)) return
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (directory == null) {
            status(getString(R.string.update_storage_unavailable), STATUS_ERROR)
            retryButton.isEnabled = true
            return
        }
        val fileName = "display-fault-update-${safeFilePart(info.tag)}.apk"
        File(directory, fileName).delete()
        runCatching {
            val request = DownloadManager.Request(downloadUri)
                .setTitle(getString(R.string.update_download_title, info.tag))
                .setDescription(info.apkName)
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
            val id = downloadManager().enqueue(request)
            preferences().edit()
                .putLong(KEY_DOWNLOAD_ID, id)
                .putString(KEY_FILE_NAME, fileName)
                .putString(KEY_EXPECTED_DIGEST, info.apkDigest)
                .putString(KEY_RELEASE_URL, info.pageUrl)
                .apply()
            status(getString(R.string.update_downloading), STATUS_PROGRESS)
            retryButton.text = getString(R.string.update_downloading_short)
            retryButton.isEnabled = false
        }.onFailure {
            status(getString(R.string.update_download_start_failed, safeMessage(it)), STATUS_ERROR)
            retryButton.isEnabled = true
        }
    }

    /** Returns true while a tracked download is pending, running, completed, or being installed. */
    private fun checkDownloadedApk(notifyFailure: Boolean): Boolean {
        val id = preferences().getLong(KEY_DOWNLOAD_ID, -1L)
        if (id < 0) return false
        return runCatching {
            downloadManager().query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) {
                    clearTrackedDownload(); return@use false
                }
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        status(getString(R.string.update_verifying), STATUS_PROGRESS)
                        if (!verifyingDownload) {
                            verifyingDownload = true
                            verifyAndInstall(id)
                        }
                        true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        clearTrackedDownload()
                        status(getString(R.string.update_download_failed, reason), STATUS_ERROR)
                        retryButton.isEnabled = true
                        if (notifyFailure) Toast.makeText(this, getString(R.string.update_download_failed, reason), Toast.LENGTH_LONG).show()
                        false
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        status(getString(R.string.update_download_paused), STATUS_PROGRESS)
                        retryButton.isEnabled = false
                        true
                    }
                    else -> {
                        status(getString(R.string.update_downloading), STATUS_PROGRESS)
                        retryButton.isEnabled = false
                        true
                    }
                }
            }
        }.getOrElse {
            if (notifyFailure) Toast.makeText(this, getString(R.string.update_status_failed), Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun verifyAndInstall(downloadId: Long) {
        val prefs = preferences()
        val fileName = prefs.getString(KEY_FILE_NAME, null) ?: run { clearTrackedDownload(); return }
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        val expectedDigest = prefs.getString(KEY_EXPECTED_DIGEST, "").orEmpty()
        executor.execute {
            val error = runCatching { verifyApk(file, expectedDigest) }
                .getOrElse { getString(R.string.update_invalid_apk) }
            runOnUiThread {
                verifyingDownload = false
                if (error != null) {
                    downloadManager().remove(downloadId)
                    clearTrackedDownload()
                    status(error, STATUS_ERROR)
                    retryButton.isEnabled = true
                    return@runOnUiThread
                }
                val uri = downloadManager().getUriForDownloadedFile(downloadId)
                if (uri == null) {
                    clearTrackedDownload()
                    status(getString(R.string.update_download_missing), STATUS_ERROR)
                } else {
                    clearTrackedDownload(keepFileMetadata = true)
                    requestInstall(uri)
                }
            }
        }
    }

    private fun verifyApk(file: File, expectedDigest: String): String? {
        if (!file.isFile || file.length() <= 0L) return getString(R.string.update_download_missing)
        val normalizedDigest = expectedDigest.substringAfter("sha256:", "").lowercase(Locale.ROOT)
        if (normalizedDigest.isNotEmpty()) {
            val actual = file.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            if (actual != normalizedDigest) return getString(R.string.update_digest_mismatch)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = @Suppress("DEPRECATION") packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return getString(R.string.update_invalid_apk)
        if (archive.packageName != packageName) return getString(R.string.update_wrong_package)
        val installed = currentPackageInfo(flags) ?: return getString(R.string.update_signature_unavailable)
        if (signingDigests(archive) != signingDigests(installed)) return getString(R.string.update_signature_mismatch)
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
        if (archiveVersion <= installedVersion) return getString(R.string.update_version_not_newer)
        return null
    }

    private fun requestInstall(apkUri: Uri) {
        if (!packageManager.canRequestPackageInstalls()) {
            preferences().edit().putString(KEY_PENDING_URI, apkUri.toString()).apply()
            status(getString(R.string.update_allow_install), STATUS_PROGRESS)
            Toast.makeText(this, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        launchInstaller(apkUri)
    }

    private fun resumePendingInstall() {
        val pending = preferences().getString(KEY_PENDING_URI, "").orEmpty()
        if (pending.isNotEmpty() && packageManager.canRequestPackageInstalls()) {
            preferences().edit().remove(KEY_PENDING_URI).apply()
            launchInstaller(Uri.parse(pending))
        }
    }

    private fun launchInstaller(apkUri: Uri) {
        runCatching {
            status(getString(R.string.update_opening_installer), STATUS_SUCCESS)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            status(getString(R.string.update_installer_failed), STATUS_ERROR)
            openReleasePage()
        }
    }

    private fun openReleasePage() {
        val url = releaseInfo?.pageUrl ?: preferences().getString(KEY_RELEASE_URL, "").orEmpty()
        if (url.isEmpty()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, R.string.update_release_open_failed, Toast.LENGTH_LONG).show() }
    }

    private fun currentPackageInfo(flags: Int): PackageInfo? = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, flags)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signingDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else info.signatures.orEmpty()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun status(message: String, color: Int) {
        if (!::statusView.isInitialized) return
        statusView.text = message
        statusView.setTextColor(color)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(downloadReceiver, filter)
        receiverRegistered = true
    }

    private fun clearTrackedDownload(keepFileMetadata: Boolean = false) {
        preferences().edit().remove(KEY_DOWNLOAD_ID).apply {
            if (!keepFileMetadata) remove(KEY_FILE_NAME).remove(KEY_EXPECTED_DIGEST)
        }.apply()
    }

    private fun preferences(): SharedPreferences = getSharedPreferences(PREFS, MODE_PRIVATE)
    private fun downloadManager() = getSystemService(DownloadManager::class.java)
    @Suppress("DEPRECATION")
    private fun readCurrentVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: getString(R.string.update_unknown)
    }.getOrDefault(getString(R.string.update_unknown))

    private fun button(label: String, secondary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 15f; isAllCaps = false
        setTextColor(if (secondary) Color.rgb(31, 41, 36) else Color.WHITE)
        background = rounded(if (secondary) Color.rgb(232, 238, 234) else Color.rgb(8, 122, 54), 14f)
        setOnClickListener { action() }
    }
    private fun buttonParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(10) }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20))
        background = rounded(Color.WHITE, 18f, Color.rgb(224, 230, 226)); elevation = dp(1).toFloat()
    }
    private fun sectionTitle(value: String) = textView(value, 19f, Color.rgb(13, 27, 20), Typeface.BOLD)
    private fun valueText(value: String) = textView(value, 15f, Color.rgb(13, 27, 20), Typeface.BOLD).apply { setPadding(0, dp(10), 0, 0) }
    private fun body(value: String) = textView(value, 14f, Color.rgb(82, 95, 88)).apply { setLineSpacing(0f, 1.25f); setPadding(0, dp(5), 0, 0) }
    private fun textView(value: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setTypeface(typeface, style)
    }
    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }
    private fun marginBottom() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    private data class ReleaseInfo(
        val tag: String,
        val pageUrl: String,
        val notes: String,
        val publishedAt: String,
        val apkName: String,
        val apkUrl: String,
        val apkSize: Long,
        val apkDigest: String
    )

    companion object {
        private const val API_URL = "https://api.github.com/repos/ahui3c/DisplayFaultSimulator_Android/releases/latest"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val PREFS = "online_update"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_EXPECTED_DIGEST = "expected_digest"
        private const val KEY_PENDING_URI = "pending_uri"
        private const val KEY_RELEASE_URL = "release_url"
        private val STATUS_NEUTRAL = Color.rgb(82, 95, 88)
        private val STATUS_SUCCESS = Color.rgb(8, 122, 54)
        private val STATUS_PROGRESS = Color.rgb(30, 100, 145)
        private val STATUS_ERROR = Color.rgb(185, 74, 54)

        private fun normalizeVersion(value: String) = value.trim().removePrefix("v").removePrefix("V")
        internal fun compareVersions(left: String, right: String): Int {
            val a = numericParts(left)
            val b = numericParts(right)
            if (a.isEmpty() || b.isEmpty()) return left.compareTo(right, ignoreCase = true)
            repeat(maxOf(a.size, b.size)) { index ->
                val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
                if (comparison != 0) return comparison
            }
            return 0
        }
        private fun numericParts(value: String) = Regex("\\d+").findAll(value).map { it.value.toLongOrNull() ?: Long.MAX_VALUE }.toList()
        private fun safeFilePart(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        private fun formatSize(bytes: Long) = if (bytes <= 0) "" else String.format(Locale.US, " (%.1f MB)", bytes / 1024.0 / 1024.0)
        private fun safeMessage(error: Throwable) = error.message ?: error.javaClass.simpleName

        fun cleanupAfterUpdate(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_FILE_NAME, null)
            if (name != null) File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name).delete()
            prefs.edit().clear().apply()
        }
    }
}
