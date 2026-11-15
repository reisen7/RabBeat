package com.rabbeat.app.data.local

import java.text.Normalizer
import kotlin.math.abs

data class DupCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileSize: Long?,
    val mimeType: String?,
    val folder: String?,
    val hidden: Boolean = false,
)

/** Same non-null directory + same song identity means the same physical file. */
fun sameFolder(a: String?, b: String?): Boolean =
    a != null && a.trimEnd('/') == b?.trimEnd('/')

data class DedupeResult(
    val keptIds: Set<String>,
    val replaced: Map<String, String>,
)

data class DedupePlan(
    val keptIds: Set<String>,
    val replaced: Map<String, String>,
)

object TrackDeduper {
    private val LOSSLESS = setOf("flac", "wav", "x-wav", "alac", "ape", "aiff", "x-aiff")

    fun dedupe(candidates: List<DupCandidate>, existingIds: Set<String>, hiddenIds: Set<String> = emptySet()): DedupeResult {
        val groups = mutableListOf<MutableList<IndexedValue<DupCandidate>>>()
        candidates.forEachIndexed { index, candidate ->
            val group = groups.firstOrNull { g -> g.any { isDuplicate(it.value, candidate) } }
            if (group != null) {
                group += IndexedValue(index, candidate)
            } else {
                groups += mutableListOf(IndexedValue(index, candidate))
            }
        }
        val kept = linkedSetOf<String>()
        val replaced = linkedMapOf<String, String>()
        groups.forEach { group ->
            val winner = group.minWithOrNull(
                compareBy(
                    { if (it.value.id in hiddenIds) 1 else 0 },
                    { if (isLossless(it.value)) 0 else 1 },
                    { -(it.value.fileSize ?: 0L) },
                    { if (it.value.id in existingIds) 0 else 1 },
                    { it.value.folder?.length ?: Int.MAX_VALUE },
                    { it.index },
                ),
            )!!.value
            kept += winner.id
            group.forEach { other ->
                if (other.value.id != winner.id) replaced[other.value.id] = winner.id
            }
        }
        return DedupeResult(keptIds = kept, replaced = replaced)
    }

    fun plan(rows: List<DupCandidate>, existingIds: Set<String>, others: List<DupCandidate>, hiddenIds: Set<String> = emptySet()): DedupePlan {
        val dedupe = dedupe(rows, existingIds, hiddenIds)
        val replaced = dedupe.replaced.toMutableMap()
        val keptIds = linkedSetOf<String>()
        rows.forEach { row ->
            if (row.id !in dedupe.keptIds) return@forEach
            // Hidden tracks don't swallow newly imported files from elsewhere:
            // hiding applies to that specific file. A same-directory (i.e. same
            // file) hit on a hidden entry still merges so it stays hidden.
            val match = others.firstOrNull {
                isDuplicate(row, it) && (!it.hidden || sameFolder(row.folder, it.folder))
            }
            if (match != null) {
                replaced[row.id] = match.id
            } else {
                keptIds += row.id
            }
        }
        fun resolve(id: String): String {
            var cur = id
            val seen = mutableSetOf<String>()
            while (true) {
                val next = replaced[cur] ?: return cur
                if (!seen.add(cur)) return cur
                cur = next
            }
        }
        val resolved = replaced.keys.associateWith(::resolve)
        return DedupePlan(keptIds = keptIds, replaced = resolved)
    }

    fun isDuplicate(a: DupCandidate, b: DupCandidate): Boolean {
        if (normalize(a.title) != normalize(b.title)) return false
        if (normalize(a.artist) != normalize(b.artist)) return false
        return if (a.durationMs > 0L && b.durationMs > 0L) {
            abs(a.durationMs - b.durationMs) <= 2000L
        } else {
            normalize(a.album) == normalize(b.album)
        }
    }

    private fun isLossless(candidate: DupCandidate): Boolean {
        val mime = candidate.mimeType?.lowercase().orEmpty()
        return LOSSLESS.any { mime.contains(it) }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }
}
