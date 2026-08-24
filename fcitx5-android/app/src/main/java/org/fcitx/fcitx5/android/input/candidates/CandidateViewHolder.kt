/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates

import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.core.CandidateWord

class CandidateViewHolder(val ui: CandidateItemUi) : RecyclerView.ViewHolder(ui.root) {
    var idx = -1
        private set

    var candidate: CandidateWord = CandidateWord.Empty
        private set

    private var directHit = false
    private var candidateTextColorOverride: Int? = null
    private var candidateCommentColorOverride: Int? = null

    fun update(
        newIndex: Int,
        newCandidate: CandidateWord,
        newDirectHit: Boolean = false,
        newCandidateTextColorOverride: Int? = null,
        newCandidateCommentColorOverride: Int? = null
    ) {
        idx = newIndex
        if (candidate != newCandidate ||
            directHit != newDirectHit ||
            candidateTextColorOverride != newCandidateTextColorOverride ||
            candidateCommentColorOverride != newCandidateCommentColorOverride
        ) {
            candidate = newCandidate
            directHit = newDirectHit
            candidateTextColorOverride = newCandidateTextColorOverride
            candidateCommentColorOverride = newCandidateCommentColorOverride
            ui.updateCandidate(
                newCandidate,
                newDirectHit,
                newCandidateTextColorOverride,
                newCandidateCommentColorOverride
            )
        }
    }

    fun clear() {
        update(-1, CandidateWord.Empty)
    }
}
