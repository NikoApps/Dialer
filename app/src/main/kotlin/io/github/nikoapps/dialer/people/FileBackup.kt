package io.github.nikoapps.dialer.people

import java.io.File

/**
 * A copy of a person's file, such as their photo, kept while a new one is put in its place, so
 * the old one can come back if the person can't be saved after all. Plain files, so it's tested
 * on the JVM. Nothing here throws.
 */
class FileBackup private constructor(private val target: File, private val copy: File?) {

    /** Puts the old file back, or deletes the new one when there was no old one. */
    fun restore() {
        runCatching {
            if (copy == null) {
                target.delete()
            } else if (!copy.renameTo(target)) {
                copy.copyTo(target, overwrite = true)
                copy.delete()
            }
        }
    }

    /** The new file stays; the copy of the old one goes. */
    fun drop() {
        copy?.delete()
    }

    companion object {
        /** Copies [target] aside, if it's there. The original stays in place until it's replaced. */
        fun of(target: File): FileBackup {
            val copy = File(target.parentFile, target.name + ".old")
            val kept = runCatching { target.isFile && target.copyTo(copy, overwrite = true).isFile }.getOrDefault(false)
            return FileBackup(target, if (kept) copy else null)
        }
    }
}
