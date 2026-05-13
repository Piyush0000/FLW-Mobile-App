package org.piramalswasthya.sakhi.helpers

import timber.log.Timber
import java.io.File

/**
 * Resolves the on-disk cache location for an image owned by a synced entity.
 *
 * The previous implementation derived filenames from [System.currentTimeMillis],
 * so every down-sync wrote a fresh file even when the underlying image had not
 * changed. Devices belonging to field workers running daily syncs filled their
 * scoped storage with orphaned duplicates until writes failed with
 * `IOException: No space left on device` (AMRIT#162).
 *
 * Filenames produced here are deterministic for a given
 * (`prefix`, `itemId`, `index`) tuple, so subsequent syncs overwrite the same
 * file rather than accumulating new copies. Older files that share the same
 * prefix/itemId/index but a different extension are deleted so a mime-type
 * change between syncs does not leave stragglers behind.
 *
 * @param cacheDir application-scoped cache directory, typically
 *                 `Context.cacheDir`; created if it does not yet exist
 * @param prefix domain prefix (e.g. `"saas_bahu_sammelan"`); used to namespace
 *               files belonging to one feature so cleanup never touches
 *               unrelated cache content
 * @param itemId server-assigned identifier of the parent entity
 * @param index zero-based position of the image within the entity's image list
 * @param ext file extension without the leading dot (e.g. `"jpg"`, `"png"`)
 * @return the canonical [File] handle to write the image bytes to
 */
fun resolveSyncImageFile(
    cacheDir: File,
    prefix: String,
    itemId: Long,
    index: Int,
    ext: String
): File {
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }
    val canonical = File(cacheDir, "${prefix}_${itemId}_img${index}.$ext")
    val staleMatcher = "${prefix}_${itemId}_img${index}."
    cacheDir.listFiles { entry ->
        entry.isFile && entry.name.startsWith(staleMatcher) && entry.name != canonical.name
    }?.forEach { stale ->
        if (!stale.delete()) {
            Timber.w("resolveSyncImageFile: failed to remove stale cache file %s", stale.name)
        }
    }
    return canonical
}
