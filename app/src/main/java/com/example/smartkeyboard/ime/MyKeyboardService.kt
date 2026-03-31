package com.example.smartkeyboard.ime

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import com.example.smartkeyboard.R
import java.util.Locale
import kotlin.math.roundToInt
//my personal keyboard
class MyKeyboardService : InputMethodService() {

    private enum class KeyboardMode {
        ABC,
        NUM,
        SYMBOL
    }

    private enum class EmojiCategory {
        SMILEYS,
        GESTURES,
        HEARTS,
        FUN,
        OBJECTS
    }

    private val INITIAL_DELAY = 300L
    private val REPEAT_DELAY = 70L
    private val SUGGESTION_DEBOUNCE_MS = 24L
    private val SHIFT_DOUBLE_TAP_WINDOW_MS = 300L

    private val handler = Handler(Looper.getMainLooper())

    private var isShiftOn = false
    private var isCapsLock = false
    private var lastShiftTime = 0L
    private var currentWord = ""
    private var correctionSuggestion: String? = null
    private var lastWordBeforeSpace = ""
    private var isSelectionMode = false
    private var selectionAnchor = -1
    private var selectionHead = -1
    private var currentWordPrefixLength = 0
    private var currentWordSuffixLength = 0
    private var pendingSelectionRefreshSkips = 0

    private lateinit var keyboardView: View
    private lateinit var emojiPanel: View
    private lateinit var topRowIdleContainer: View
    private lateinit var topRowSuggestionContainer: View
    private lateinit var suggestionViews: List<TextView>
    private lateinit var emojiCategoryButtons: List<Button>
    private lateinit var emojiSlotButtons: List<Button>
    private lateinit var keyPreviewTextView: TextView
    private var keyPreviewPopup: PopupWindow? = null
    private var keyboardMode = KeyboardMode.ABC
    private var currentEmojiCategory = EmojiCategory.SMILEYS
    private var isShowingWidgets = true
    private var dictionary: List<String> = emptyList()
    private var dictionarySet: Set<String> = emptySet()
    private var pendingSuggestionRunnable: Runnable? = null
    private var lastSuggestionWord = ""
    private var lastSuggestionValues: List<String> = emptyList()
    private var lastCorrectionSuggestion: String? = null

    private val normalSuggestionColor = 0xFF90A4AE.toInt()
    private val correctionSuggestionColor = 0xFFEF5350.toInt()

    private val numberMap = mapOf(
        R.id.key_q to "1", R.id.key_w to "2", R.id.key_e to "3",
        R.id.key_r to "4", R.id.key_t to "5", R.id.key_y to "6",
        R.id.key_u to "7", R.id.key_i to "8", R.id.key_o to "9", R.id.key_p to "0",

        R.id.key_a to "@", R.id.key_s to "#", R.id.key_d to "$",
        R.id.key_f to "%", R.id.key_g to "&", R.id.key_h to "*",
        R.id.key_j to "(", R.id.key_k to ")", R.id.key_l to "!",

        R.id.key_z to "-", R.id.key_x to "+", R.id.key_c to "=",
        R.id.key_v to "/", R.id.key_b to ":", R.id.key_n to ";", R.id.key_m to "?"
    )

    private val symbolMap = mapOf(
        R.id.key_q to "[", R.id.key_w to "]", R.id.key_e to "{",
        R.id.key_r to "}", R.id.key_t to "<", R.id.key_y to ">",
        R.id.key_u to "^", R.id.key_i to "*", R.id.key_o to "+", R.id.key_p to "=",

        R.id.key_a to "_", R.id.key_s to "\\", R.id.key_d to "|",
        R.id.key_f to "~", R.id.key_g to "$", R.id.key_h to "\"",
        R.id.key_j to "'", R.id.key_k to "`", R.id.key_l to "@",

        R.id.key_z to ".", R.id.key_x to ",", R.id.key_c to "!",
        R.id.key_v to "?", R.id.key_b to "#", R.id.key_n to "%", R.id.key_m to "&"
    )

    private val keyMap = mapOf(
        R.id.key_q to "q", R.id.key_w to "w", R.id.key_e to "e",
        R.id.key_r to "r", R.id.key_t to "t", R.id.key_y to "y",
        R.id.key_u to "u", R.id.key_i to "i", R.id.key_o to "o", R.id.key_p to "p",

        R.id.key_a to "a", R.id.key_s to "s", R.id.key_d to "d",
        R.id.key_f to "f", R.id.key_g to "g", R.id.key_h to "h",
        R.id.key_j to "j", R.id.key_k to "k", R.id.key_l to "l",

        R.id.key_z to "z", R.id.key_x to "x", R.id.key_c to "c",
        R.id.key_v to "v", R.id.key_b to "b", R.id.key_n to "n", R.id.key_m to "m"
    )

    private val emojiCategoryOrder = listOf(
        EmojiCategory.SMILEYS,
        EmojiCategory.GESTURES,
        EmojiCategory.HEARTS,
        EmojiCategory.FUN,
        EmojiCategory.OBJECTS
    )

    private val emojiCategoryIcons = mapOf(
        EmojiCategory.SMILEYS to "🙂",
        EmojiCategory.GESTURES to "👍",
        EmojiCategory.HEARTS to "❤️",
        EmojiCategory.FUN to "🎉",
        EmojiCategory.OBJECTS to "🐶"
    )

    private val emojiCategoryValues = mapOf(
        EmojiCategory.SMILEYS to listOf("😀", "😂", "😅", "😊", "😍", "😎", "😢", "😡", "😭", "😁", "😆", "😉"),
        EmojiCategory.GESTURES to listOf("👍", "👎", "👏", "🙌", "🙏", "✌️", "🤞", "👋", "🤝", "👌"),
        EmojiCategory.HEARTS to listOf("❤️", "💔", "💯", "✔️", "✖️", "⭐", "🔥", "🎉", "⚡", "✨"),
        EmojiCategory.FUN to listOf("🐶", "🐱", "🍕", "🍔", "☕", "🌍", "🌙", "☀️", "🎵", "🎁"),
        EmojiCategory.OBJECTS to listOf("📱", "💻", "🎮", "📷", "🎧", "🚗", "✈️", "🏠", "📚", "🕒")
    )

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)
        emojiPanel = keyboardView.findViewById(R.id.emojiPanel)
        topRowIdleContainer = keyboardView.findViewById(R.id.top_row_idle_container)
        topRowSuggestionContainer = keyboardView.findViewById(R.id.top_row_suggestion_container)
        suggestionViews = listOf(
            keyboardView.findViewById(R.id.suggestion_one),
            keyboardView.findViewById(R.id.suggestion_two),
            keyboardView.findViewById(R.id.suggestion_three)
        )
        setupSuggestionClicks()
        if (dictionary.isEmpty()) {
            loadDictionary()
        }
        initializeKeyPreview()
        setupEmojiPanel()
        updateSuggestionViews(emptyList())
        setupLetterKeys()
        setupDeleteKey()
        setupSpaceKey()
        setupEnterKey()
        setupShiftKey()
        setupNumbersKey()
        setupCommaKey()
        setupPeriodKey()
        setupArrowKeys()
        setupShortcutKeys()
        refreshKeyboardUi()
        isShowingWidgets = true
        switchTopRowMode(isTyping = false)

        return keyboardView
    }
    private fun setupDeleteKey() {
        setupRepeatKey(R.id.key_delete) {
            val inputConnection = currentInputConnection ?: return@setupRepeatKey
            val selectedText = inputConnection.getSelectedText(0)

            if (!selectedText.isNullOrEmpty()) {
                pendingSelectionRefreshSkips += 1
                inputConnection.commitText("", 1)
            } else {
                pendingSelectionRefreshSkips += 1
                inputConnection.deleteSurroundingText(1, 0)
            }

            lastWordBeforeSpace = ""
            clearSuggestionPresentation()
            refreshSuggestionsFromCursor()
        }
    }

    // 🔥 SPACE KEY
    private fun setupSpaceKey() {
        setupRepeatKey(R.id.key_space) {
            commitText(" ")
        }
    }

    // 🔥 ENTER KEY
    private fun setupEnterKey() {
        setupTapKey(R.id.key_enter) {
            val inputConnection = currentInputConnection ?: return@setupTapKey
            val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val shouldInsertNewLine = action == EditorInfo.IME_ACTION_NONE ||
                action == EditorInfo.IME_ACTION_UNSPECIFIED ||
                imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0

            if (shouldInsertNewLine) {
                commitText("\n")
            } else {
                inputConnection.performEditorAction(action)
            }
        }
    }

    private fun setupShiftKey() {
        setupTapKey(R.id.key_shift, clearsSelectionMode = false) {
            if (isSelectionMode) {
                clearSelectionMode()
                return@setupTapKey
            }

            when (keyboardMode) {
                KeyboardMode.NUM -> {
                    keyboardMode = KeyboardMode.SYMBOL
                    refreshKeyboardUi()
                }

                KeyboardMode.SYMBOL -> {
                    keyboardMode = KeyboardMode.NUM
                    refreshKeyboardUi()
                }

                KeyboardMode.ABC -> {
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastShiftTime < SHIFT_DOUBLE_TAP_WINDOW_MS) {
                        isCapsLock = !isCapsLock
                        isShiftOn = isCapsLock
                    } else {
                        isShiftOn = !isShiftOn
                        isCapsLock = false
                    }

                    lastShiftTime = currentTime
                    refreshKeyboardUi()
                }
            }
        }

        keyboardView.findViewById<Button>(R.id.key_shift).setOnLongClickListener { view ->
            performKeyHaptic(view)
            toggleSelectionMode()
            true
        }
    }

    private fun setupNumbersKey() {
        setupTapKey(R.id.key_numbers) {
            keyboardMode = if (keyboardMode == KeyboardMode.ABC) {
                KeyboardMode.NUM
            } else {
                KeyboardMode.ABC
            }

            isShiftOn = false
            isCapsLock = false
            lastShiftTime = 0L
            refreshKeyboardUi()
        }
    }

    private fun setupCommaKey() {
        setupTapKey(
            R.id.key_comma,
            previewTextProvider = { "," }
        ) {
            commitText(",")
        }
    }

    private fun setupPeriodKey() {
        setupTapKey(
            R.id.key_period,
            previewTextProvider = { "." }
        ) {
            commitText(".")
        }
    }

    private fun setupArrowKeys() {
        setupRepeatKey(R.id.key_arrow_left, clearsSelectionMode = false) {
            if (isSelectionMode) {
                pendingSelectionRefreshSkips += 1
                expandSelection(-1)
                refreshSuggestionsFromCursor()
            } else {
                pendingSelectionRefreshSkips += 1
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
                handler.post { refreshSuggestionsFromCursor() }
            }
        }

        setupRepeatKey(R.id.key_arrow_right, clearsSelectionMode = false) {
            if (isSelectionMode) {
                pendingSelectionRefreshSkips += 1
                expandSelection(1)
                refreshSuggestionsFromCursor()
            } else {
                pendingSelectionRefreshSkips += 1
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                handler.post { refreshSuggestionsFromCursor() }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isShiftOn = false
        isCapsLock = false
        lastShiftTime = 0L
        keyboardMode = if (shouldStartInNumbersMode(info)) {
            KeyboardMode.NUM
        } else {
            KeyboardMode.ABC
        }
        currentWord = ""
        lastWordBeforeSpace = ""
        isSelectionMode = false
        selectionAnchor = -1
        selectionHead = -1
        clearComposingState(showWidgets = false)
        isShowingWidgets = true
        if (::emojiPanel.isInitialized) {
            emojiPanel.visibility = View.GONE
        }
        refreshKeyboardUi()
        switchTopRowMode(isTyping = false)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )

        if (!::keyboardView.isInitialized) {
            return
        }

        if (pendingSelectionRefreshSkips > 0) {
            pendingSelectionRefreshSkips -= 1
            return
        }

        if (newSelStart != oldSelStart || newSelEnd != oldSelEnd) {
            refreshSuggestionsFromCursor()
        }
    }

    fun switchTopRowMode(isTyping: Boolean) {
        if (!::topRowIdleContainer.isInitialized || !::topRowSuggestionContainer.isInitialized) {
            return
        }

        topRowSuggestionContainer.visibility = if (isTyping) View.VISIBLE else View.GONE
        topRowIdleContainer.visibility = if (isTyping) View.GONE else View.VISIBLE
    }

    fun onUserTyped() {
        switchTopRowMode(isTyping = true)
        isShowingWidgets = false
    }

    private fun clearPendingSuggestionUpdate() {
        pendingSuggestionRunnable?.let(handler::removeCallbacks)
        pendingSuggestionRunnable = null
    }

    private fun clearSuggestionPresentation() {
        clearPendingSuggestionUpdate()
        correctionSuggestion = null
        lastSuggestionWord = ""
        lastSuggestionValues = emptyList()
        lastCorrectionSuggestion = null
        updateSuggestionViews(emptyList())
    }

    private fun clearComposingState(showWidgets: Boolean = true) {
        currentWord = ""
        currentWordPrefixLength = 0
        currentWordSuffixLength = 0
        clearSuggestionPresentation()
        if (showWidgets) {
            switchTopRowMode(isTyping = false)
            isShowingWidgets = true
        }
    }

    private fun scheduleSuggestionUpdate(word: String) {
        val normalizedWord = word.trim().lowercase(Locale.US)

        if (normalizedWord.isEmpty()) {
            clearSuggestionPresentation()
            return
        }

        if (normalizedWord == lastSuggestionWord) {
            correctionSuggestion = lastCorrectionSuggestion
            updateSuggestionViews(lastSuggestionValues)
            return
        }

        clearPendingSuggestionUpdate()
        pendingSuggestionRunnable = Runnable {
            pendingSuggestionRunnable = null

            if (currentWord != normalizedWord) {
                return@Runnable
            }

            val suggestions = generateSuggestions(normalizedWord)
            lastSuggestionWord = normalizedWord
            lastSuggestionValues = suggestions
            lastCorrectionSuggestion = correctionSuggestion
            updateSuggestionViews(suggestions)
        }

        handler.postDelayed(pendingSuggestionRunnable!!, SUGGESTION_DEBOUNCE_MS)
    }

    private fun toggleSelectionMode() {
        if (isSelectionMode) {
            clearSelectionMode()
            return
        }

        val inputConnection = currentInputConnection ?: return
        val beforeLength = inputConnection.getTextBeforeCursor(1000, 0)?.length ?: 0
        val selectedLength = inputConnection.getSelectedText(0)?.length ?: 0

        isSelectionMode = true
        selectionAnchor = beforeLength
        selectionHead = beforeLength + selectedLength
        updateShiftUI()
    }

    private fun clearSelectionMode() {
        if (!isSelectionMode) {
            return
        }

        isSelectionMode = false
        selectionAnchor = -1
        selectionHead = -1
        updateShiftUI()
    }

    private fun expandSelection(direction: Int) {
        val inputConnection = currentInputConnection ?: return
        val beforeLength = inputConnection.getTextBeforeCursor(1000, 0)?.length ?: 0
        val selectedLength = inputConnection.getSelectedText(0)?.length ?: 0
        val afterLength = inputConnection.getTextAfterCursor(1000, 0)?.length ?: 0

        if (selectionAnchor < 0 || selectionHead < 0) {
            selectionAnchor = beforeLength
            selectionHead = beforeLength + selectedLength
        }

        val maxPosition = beforeLength + selectedLength + afterLength
        selectionHead = (selectionHead + direction).coerceIn(0, maxPosition)
        inputConnection.setSelection(
            minOf(selectionAnchor, selectionHead),
            maxOf(selectionAnchor, selectionHead)
        )
    }

    private fun refreshSuggestionsFromCursor() {
        val inputConnection = currentInputConnection ?: return
        val beforeText = inputConnection.getTextBeforeCursor(50, 0)?.toString().orEmpty()
        val afterText = inputConnection.getTextAfterCursor(50, 0)?.toString().orEmpty()

        val lastPart = beforeText.takeLastWhile { it.isLetter() }.lowercase(Locale.US)
        val firstPart = afterText.takeWhile { it.isLetter() }.lowercase(Locale.US)
        val wordAtCursor = lastPart + firstPart

        currentWordPrefixLength = lastPart.length
        currentWordSuffixLength = firstPart.length

        if (wordAtCursor.isEmpty()) {
            clearComposingState(showWidgets = true)
            return
        }

        currentWord = wordAtCursor
        scheduleSuggestionUpdate(currentWord)
        switchTopRowMode(isTyping = true)
        isShowingWidgets = false
    }

    private fun loadDictionary() {
        try {
            dictionary = resources.openRawResource(R.raw.dictionary)
                .bufferedReader()
                .useLines { lines ->
                    lines.map { it.trim().lowercase(Locale.US) }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .toList()
                }
            dictionarySet = dictionary.toSet()
        } catch (_: Exception) {
            dictionary = emptyList()
            dictionarySet = emptySet()
        }
    }

    private fun updateCurrentWord(committedText: String) {
        when {
            committedText.any { it == ' ' || it == '\n' || it == '\t' } -> {
                lastWordBeforeSpace = currentWord
                clearComposingState(showWidgets = true)
            }

            committedText.all { it.isLetter() } -> {
                lastWordBeforeSpace = ""
                currentWord += committedText.lowercase(Locale.US)
                currentWordPrefixLength = currentWord.length
                currentWordSuffixLength = 0
                scheduleSuggestionUpdate(currentWord)
                onUserTyped()
            }

            else -> {
                lastWordBeforeSpace = ""
                clearComposingState(showWidgets = true)
            }
        }
    }

    private fun generateSuggestions(word: String): List<String> {
        val normalizedWord = word.trim().lowercase(Locale.US)
        correctionSuggestion = null

        if (normalizedWord.isEmpty() || dictionary.isEmpty()) {
            return emptyList()
        }

        val suggestions = dictionary.asSequence()
            .mapNotNull { candidate ->
                val baseScore = when {
                    candidate == normalizedWord -> 100
                    candidate.startsWith(normalizedWord) -> 80
                    candidate.contains(normalizedWord) -> 50
                    else -> return@mapNotNull null
                }

                val score = baseScore - kotlin.math.abs(candidate.length - normalizedWord.length)
                candidate to score
            }
            .sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy { kotlin.math.abs(it.first.length - normalizedWord.length) }
                    .thenBy { it.first }
            )
            .map { it.first }
            .filter { it != normalizedWord }
            .take(3)
            .toList()

        val exactMatch = dictionarySet.contains(normalizedWord)
        if (!exactMatch && suggestions.isEmpty() && normalizedWord.length > 1) {
            val correction = findCorrectionSuggestion(normalizedWord)
            if (correction != null) {
                correctionSuggestion = correction
                return listOf(correction)
            }
        }

        return suggestions
    }

    private fun findCorrectionSuggestion(word: String): String? {
        if (word.isEmpty() || dictionary.isEmpty()) {
            return null
        }

        return dictionary.minByOrNull { candidate ->
            correctionScore(word, candidate)
        }
    }

    private fun correctionScore(input: String, candidate: String): Int {
        val distance = levenshteinDistance(input, candidate)
        val prefixBonus = commonPrefixLength(input, candidate)
        return (distance * 10) + kotlin.math.abs(input.length - candidate.length) - (prefixBonus * 2)
    }

    private fun commonPrefixLength(first: String, second: String): Int {
        val sharedLength = minOf(first.length, second.length)

        for (index in 0 until sharedLength) {
            if (first[index] != second[index]) {
                return index
            }
        }

        return sharedLength
    }

    private fun levenshteinDistance(first: String, second: String): Int {
        if (first == second) {
            return 0
        }

        if (first.isEmpty()) {
            return second.length
        }

        if (second.isEmpty()) {
            return first.length
        }

        val previousRow = IntArray(second.length + 1) { it }
        val currentRow = IntArray(second.length + 1)

        for (firstIndex in first.indices) {
            currentRow[0] = firstIndex + 1

            for (secondIndex in second.indices) {
                val substitutionCost = if (first[firstIndex] == second[secondIndex]) 0 else 1

                currentRow[secondIndex + 1] = minOf(
                    currentRow[secondIndex] + 1,
                    previousRow[secondIndex + 1] + 1,
                    previousRow[secondIndex] + substitutionCost
                )
            }

            for (secondIndex in previousRow.indices) {
                previousRow[secondIndex] = currentRow[secondIndex]
            }
        }

        return previousRow[second.length]
    }

    private fun updateSuggestionViews(suggestions: List<String>) {
        if (!::suggestionViews.isInitialized) {
            return
        }

        val displayedSuggestions = listOf(
            currentWord,
            suggestions.getOrNull(0).orEmpty(),
            suggestions.getOrNull(1).orEmpty()
        )
        val hasSuggestions = suggestions.isNotEmpty()

        suggestionViews.forEachIndexed { index, textView ->
            val suggestion = if (hasSuggestions) {
                displayedSuggestions[index]
            } else {
                ""
            }

            textView.text = suggestion
            textView.visibility = if (suggestion.isEmpty()) View.GONE else View.VISIBLE
            textView.setTextColor(
                if (index > 0 && suggestion.isNotEmpty() && suggestion == correctionSuggestion) {
                    correctionSuggestionColor
                } else {
                    normalSuggestionColor
                }
            )
        }
    }

    private fun applySuggestion(selectedSuggestion: String) {
        if (currentWord.isEmpty() || selectedSuggestion.isEmpty()) {
            return
        }

        val inputConnection = currentInputConnection ?: return
        val selectedText = inputConnection.getSelectedText(0)
        clearSelectionMode()
        clearPendingSuggestionUpdate()

        if (!selectedText.isNullOrEmpty()) {
            pendingSelectionRefreshSkips += 1
        } else {
            pendingSelectionRefreshSkips += 2
            inputConnection.deleteSurroundingText(currentWordPrefixLength, currentWordSuffixLength)
        }

        inputConnection.commitText("$selectedSuggestion ", 1)

        lastWordBeforeSpace = selectedSuggestion.lowercase(Locale.US)
        clearComposingState(showWidgets = true)
    }

    private fun updateShiftUI() {
        val keyShift = keyboardView.findViewById<Button>(R.id.key_shift)

        if (isSelectionMode) {
            keyShift.setBackgroundColor(0xFF546E7A.toInt())
        } else if (keyboardMode != KeyboardMode.ABC) {
            keyShift.setBackgroundResource(R.drawable.key_background)
        } else if (isCapsLock) {
            keyShift.setBackgroundColor(0xFFEF6C00.toInt())
        } else if (isShiftOn) {
            keyShift.setBackgroundColor(0xFF6D7B8D.toInt())
        } else {
            keyShift.setBackgroundResource(R.drawable.key_background)
        }
    }

    private fun updateKeyLabels() {
        val activeMap = when (keyboardMode) {
            KeyboardMode.ABC -> keyMap
            KeyboardMode.NUM -> numberMap
            KeyboardMode.SYMBOL -> symbolMap
        }

        for ((id, value) in activeMap) {
            val button = keyboardView.findViewById<Button>(id)

            val text = if (keyboardMode == KeyboardMode.ABC && (isShiftOn || isCapsLock)) {
                value.uppercase()
            } else {
                value
            }

            button.text = text
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacksAndMessages(null)
        pendingSuggestionRunnable = null
        hideKeyPreview()
        if (::emojiPanel.isInitialized) {
            emojiPanel.visibility = View.GONE
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pendingSuggestionRunnable = null
        hideKeyPreview()
        super.onDestroy()
    }

    private fun setupLetterKeys() {
        for ((id, value) in keyMap) {
            val button = keyboardView.findViewById<Button>(id)
            button.setOnTouchListener(
                createKeyPreviewTouchListener { resolveLetterValue(id, value) }
            )
            button.setOnClickListener { view ->
                clearSelectionMode()
                performKeyHaptic(view)
                commitText(resolveLetterValue(id, value))

                if (keyboardMode == KeyboardMode.ABC && isShiftOn && !isCapsLock) {
                    isShiftOn = false
                    refreshKeyboardUi()
                }
            }
        }
    }

    private fun setupShortcutKeys() {
        setupTapKey(R.id.key_tools) {
            if (isShowingWidgets) {
                switchTopRowMode(isTyping = true)
                isShowingWidgets = false
            } else {
                switchTopRowMode(isTyping = false)
                isShowingWidgets = true
            }
        }

        setupTapKey(R.id.key_emoji) {
            renderEmojiCategory()
            emojiPanel.visibility = if (emojiPanel.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        setupTapKey(R.id.key_select_all) {
            currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
        }

        setupTapKey(R.id.key_copy) {
            currentInputConnection?.performContextMenuAction(android.R.id.copy)
        }

        setupTapKey(R.id.key_paste) {
            currentInputConnection?.performContextMenuAction(android.R.id.paste)
        }
    }

    private fun setupSuggestionClicks() {
        suggestionViews.forEach { textView ->
            textView.setOnClickListener {
                applySuggestion(textView.text.toString())
            }
        }
    }

    private fun setupEmojiPanel() {
        emojiCategoryButtons = listOf(
            keyboardView.findViewById(R.id.emoji_category_smileys),
            keyboardView.findViewById(R.id.emoji_category_gestures),
            keyboardView.findViewById(R.id.emoji_category_hearts),
            keyboardView.findViewById(R.id.emoji_category_fun),
            keyboardView.findViewById(R.id.emoji_category_objects)
        )
        emojiSlotButtons = listOf(
            keyboardView.findViewById(R.id.emoji_slot_1),
            keyboardView.findViewById(R.id.emoji_slot_2),
            keyboardView.findViewById(R.id.emoji_slot_3),
            keyboardView.findViewById(R.id.emoji_slot_4),
            keyboardView.findViewById(R.id.emoji_slot_5),
            keyboardView.findViewById(R.id.emoji_slot_6),
            keyboardView.findViewById(R.id.emoji_slot_7),
            keyboardView.findViewById(R.id.emoji_slot_8),
            keyboardView.findViewById(R.id.emoji_slot_9),
            keyboardView.findViewById(R.id.emoji_slot_10),
            keyboardView.findViewById(R.id.emoji_slot_11),
            keyboardView.findViewById(R.id.emoji_slot_12)
        )

        emojiCategoryButtons.forEachIndexed { index, button ->
            val category = emojiCategoryOrder[index]
            button.setOnClickListener { view ->
                performKeyHaptic(view)
                if (currentEmojiCategory != category) {
                    currentEmojiCategory = category
                    renderEmojiCategory()
                }
            }
        }

        emojiSlotButtons.forEach { button ->
            button.setOnClickListener { view ->
                val emoji = button.text?.toString().orEmpty()
                if (emoji.isBlank()) {
                    return@setOnClickListener
                }

                clearSelectionMode()
                performKeyHaptic(view)
                currentInputConnection?.commitText(emoji, 1)
                lastWordBeforeSpace = ""
                clearComposingState(showWidgets = true)
            }
        }

        renderEmojiCategory()
    }

    private fun renderEmojiCategory() {
        if (!::emojiCategoryButtons.isInitialized || !::emojiSlotButtons.isInitialized) {
            return
        }

        emojiCategoryButtons.forEachIndexed { index, button ->
            val category = emojiCategoryOrder[index]
            button.text = emojiCategoryIcons.getValue(category)
            button.alpha = if (category == currentEmojiCategory) 1f else 0.72f
        }

        val emojiValues = emojiCategoryValues[currentEmojiCategory].orEmpty()
        emojiSlotButtons.forEachIndexed { index, button ->
            val emoji = emojiValues.getOrNull(index).orEmpty()
            val isVisible = emoji.isNotEmpty()
            button.text = emoji
            button.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
            button.isEnabled = isVisible
        }
    }

    private fun setupTapKey(
        buttonId: Int,
        clearsSelectionMode: Boolean = true,
        previewTextProvider: (() -> String)? = null,
        action: () -> Unit
    ) {
        val button = keyboardView.findViewById<Button>(buttonId)
        button.setOnTouchListener(
            previewTextProvider?.let { createKeyPreviewTouchListener(it) }
        )
        button.setOnClickListener { view ->
            if (clearsSelectionMode) {
                clearSelectionMode()
            }
            performKeyHaptic(view)
            action()
        }
    }

    private fun setupRepeatKey(
        buttonId: Int,
        clearsSelectionMode: Boolean = true,
        previewTextProvider: (() -> String)? = null,
        action: () -> Unit
    ) {
        val button = keyboardView.findViewById<Button>(buttonId)
        var isPressed = false
        var repeatRunnable: Runnable? = null

        button.setOnClickListener(null)
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (clearsSelectionMode) {
                        clearSelectionMode()
                    }
                    previewTextProvider?.invoke()?.let { showKeyPreview(view, it) }
                    setPressedState(view, true)
                    isPressed = true
                    action()

                    repeatRunnable = object : Runnable {
                        override fun run() {
                            if (!isPressed) {
                                return
                            }

                            action()
                            handler.postDelayed(this, REPEAT_DELAY)
                        }
                    }

                    handler.postDelayed(repeatRunnable!!, INITIAL_DELAY)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    hideKeyPreview()
                    setPressedState(view, false)
                    repeatRunnable?.let(handler::removeCallbacks)
                    isPressed = false
                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    hideKeyPreview()
                    setPressedState(view, false)
                    repeatRunnable?.let(handler::removeCallbacks)
                    isPressed = false
                    true
                }

                else -> false
            }
        }
    }

    private fun refreshKeyboardUi() {
        updateKeyLabels()
        updateShiftUI()
        updateNumbersUI()
        updateStaticKeyLabels()
    }

    private fun updateNumbersUI() {
        keyboardView.findViewById<Button>(R.id.key_numbers).text =
            if (keyboardMode == KeyboardMode.ABC) "123" else "ABC"
        keyboardView.findViewById<Button>(R.id.key_comma).text = ","
        keyboardView.findViewById<Button>(R.id.key_period).text = "."
    }

    private fun updateStaticKeyLabels() {
        keyboardView.findViewById<Button>(R.id.key_tools).text = "\u22EF"
        keyboardView.findViewById<Button>(R.id.key_emoji).text = "\uD83D\uDE42"
        keyboardView.findViewById<Button>(R.id.key_shift).text = when (keyboardMode) {
            KeyboardMode.ABC -> "\u2191"
            KeyboardMode.NUM -> "=\\<"
            KeyboardMode.SYMBOL -> "123"
        }
        keyboardView.findViewById<Button>(R.id.key_delete).text = "\u232B"
        keyboardView.findViewById<Button>(R.id.key_arrow_left).text = "\u2190"
        keyboardView.findViewById<Button>(R.id.key_arrow_right).text = "\u2192"
        keyboardView.findViewById<Button>(R.id.key_enter).text = "\u23CE"
        keyboardView.findViewById<Button>(R.id.key_select_all).text = "\u29C9"
    }

    private fun resolveLetterValue(id: Int, fallbackValue: String): String {
        return when (keyboardMode) {
            KeyboardMode.ABC -> {
                if (isShiftOn || isCapsLock) {
                    fallbackValue.uppercase()
                } else {
                    fallbackValue
                }
            }

            KeyboardMode.NUM -> numberMap[id] ?: ""
            KeyboardMode.SYMBOL -> symbolMap[id] ?: ""
        }
    }

    private fun setPressedState(view: View, pressed: Boolean) {
        if (pressed) {
            performKeyHaptic(view)
        }

        view.isPressed = pressed
    }

    private fun initializeKeyPreview() {
        val horizontalPadding = dpToPx(14)
        val verticalPadding = dpToPx(10)

        keyPreviewTextView = TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = dpToPx(48)
            minHeight = dpToPx(56)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(0xFF5C6773.toInt())
                setStroke(dpToPx(1), 0xFF8A97A8.toInt())
            }
            elevation = dpToPx(8).toFloat()
        }

        keyPreviewPopup = PopupWindow(
            keyPreviewTextView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isTouchable = false
            isFocusable = false
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = dpToPx(8).toFloat()
        }
    }

    private fun createKeyPreviewTouchListener(
        previewTextProvider: () -> String
    ): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> showKeyPreview(view, previewTextProvider())
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hideKeyPreview()
            }
            false
        }
    }

    private fun showKeyPreview(anchorView: View, previewText: String) {
        if (previewText.isBlank() || !::keyPreviewTextView.isInitialized) {
            return
        }

        val popup = keyPreviewPopup ?: return
        keyPreviewTextView.text = previewText
        keyPreviewTextView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popupWidth = keyPreviewTextView.measuredWidth
        val popupHeight = keyPreviewTextView.measuredHeight
        val xOffset = (anchorView.width - popupWidth) / 2
        val yOffset = -(anchorView.height + popupHeight + dpToPx(8))

        hideKeyPreview()
        popup.showAsDropDown(anchorView, xOffset, yOffset, Gravity.START)
    }

    private fun hideKeyPreview() {
        keyPreviewPopup?.dismiss()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    private fun commitText(text: String) {
        if (text.isEmpty()) {
            return
        }

        val inputConnection = currentInputConnection ?: return
        val shouldRefreshFromCursor = currentWordSuffixLength > 0
        pendingSelectionRefreshSkips += 1
        inputConnection.commitText(text, 1)
        if (shouldRefreshFromCursor) {
            refreshSuggestionsFromCursor()
        } else {
            updateCurrentWord(text)
        }
    }

    private fun performKeyHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun shouldStartInNumbersMode(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false

        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> true

            else -> false
        }
    }
}
