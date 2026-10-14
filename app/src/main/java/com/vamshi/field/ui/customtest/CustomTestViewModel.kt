package com.vamshi.field.ui.customtest

import com.vamshi.field.domain.model.standards.parseScore
import com.vamshi.field.domain.model.standards.StageScore
import com.vamshi.field.domain.model.standards.InputParadigm
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.CustomTestDraft
import com.vamshi.field.domain.model.standards.CustomTestField
import com.vamshi.field.domain.model.standards.MeasurementMethod
import com.vamshi.field.domain.model.standards.ScoringBands
import com.vamshi.field.domain.model.standards.TestCategory
import com.vamshi.field.domain.usecase.standards.GetCustomTestDraftUseCase
import com.vamshi.field.domain.usecase.standards.GetTestLibraryUseCase
import com.vamshi.field.domain.usecase.standards.SaveCustomTestResult
import com.vamshi.field.domain.usecase.standards.SaveCustomTestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Identifies one cut-point input. [sex] is null for the shared ladder. */
data class CutPointKey(val sex: BiologicalSex?, val index: Int)

data class CustomTestUiState(
    val draft: CustomTestDraft = CustomTestDraft(),
    val categories: List<TestCategory> = emptyList(),
    /** Field-level messages from validation, rendered inline under the offending input. */
    val errors: Map<CustomTestField, String> = emptyMap(),
    /**
     * Kept as raw text rather than parsed doubles so a half-typed "1." survives
     * recomposition. Parsed into the draft on every change.
     */
    val validMinText: String = "",
    val validMaxText: String = "",
    /**
     * Raw cut-point text keyed by (sex, index) — same reason as the range fields, a
     * half-typed "6." must survive recomposition. Null sex is the shared ladder.
     */
    val cutPointText: Map<CutPointKey, String> = emptyMap(),
    val isAdvancedExpanded: Boolean = false,
    val isSaving: Boolean = false,
    /** Set once after a successful save; the screen consumes it to navigate away. */
    val savedTestId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val isEditMode: Boolean get() = draft.isEdit

    /**
     * Cheap enable/disable for the save button. Full validation runs on save — this only
     * stops the coach tapping a button that obviously can't succeed, without red text
     * appearing while they are still typing the first character.
     */
    val canSave: Boolean
        get() = draft.name.isNotBlank() &&
            draft.unit.isNotBlank() &&
            draft.categoryId.isNotBlank() &&
            !isSaving
}

sealed interface CustomTestAction {
    data class OnNameChange(val value: String) : CustomTestAction
    data class OnCategorySelect(val categoryId: String) : CustomTestAction
    data class OnUnitChange(val value: String) : CustomTestAction
    data class OnDirectionChange(val isHigherBetter: Boolean) : CustomTestAction
    data class OnDescriptionChange(val value: String) : CustomTestAction
    data class OnMeasurementMethodChange(val method: MeasurementMethod) : CustomTestAction
    data class OnTrialsChange(val trials: Int) : CustomTestAction
    data class OnValidMinChange(val value: String) : CustomTestAction
    data class OnValidMaxChange(val value: String) : CustomTestAction
    data object OnToggleAdvanced : CustomTestAction
    data class OnScoringEnabledChange(val enabled: Boolean) : CustomTestAction
    data class OnSameForAllSexesChange(val same: Boolean) : CustomTestAction
    /** [sex] is null for the shared ladder; [index] is 0..1, worst→best. */
    data class OnCutPointChange(
        val sex: BiologicalSex?,
        val index: Int,
        val value: String
    ) : CustomTestAction
    data object OnSave : CustomTestAction
    data object OnNavigateBack : CustomTestAction
    data object NavigationConsumed : CustomTestAction
    data object OnDismissError : CustomTestAction
}

@HiltViewModel
class CustomTestViewModel @Inject constructor(
    private val getTestLibrary: GetTestLibraryUseCase,
    private val getCustomTestDraft: GetCustomTestDraftUseCase,
    private val saveCustomTest: SaveCustomTestUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editingTestId: String? = savedStateHandle["testId"]

    private val _uiState = MutableStateFlow(CustomTestUiState())
    val uiState: StateFlow<CustomTestUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        if (editingTestId != null) hydrateDraft(editingTestId) else _uiState.update { it.copy(isLoading = false) }
    }

    fun onAction(action: CustomTestAction) {
        when (action) {
            is CustomTestAction.OnNameChange ->
                updateDraft(CustomTestField.NAME) { it.copy(name = action.value) }

            is CustomTestAction.OnCategorySelect ->
                updateDraft(CustomTestField.CATEGORY) { it.copy(categoryId = action.categoryId) }

            is CustomTestAction.OnUnitChange ->
                updateDraft(CustomTestField.UNIT) { it.copy(unit = action.value) }

            is CustomTestAction.OnDirectionChange ->
                updateDraft(null) { it.copy(isHigherBetter = action.isHigherBetter) }

            is CustomTestAction.OnDescriptionChange ->
                updateDraft(null) { it.copy(description = action.value) }

            is CustomTestAction.OnMeasurementMethodChange ->
                _uiState.update { it.withMeasurementMethod(action.method) }

            is CustomTestAction.OnTrialsChange ->
                updateDraft(CustomTestField.TRIALS) { it.copy(trialsPerAthlete = action.trials) }

            is CustomTestAction.OnValidMinChange -> {
                val text = action.value.filterNumeric()
                _uiState.update {
                    it.copy(
                        validMinText = text,
                        draft = it.draft.copy(validMin = parseScore(text, it.draft.paradigm)),
                        errors = it.errors - CustomTestField.VALID_RANGE
                    )
                }
            }

            is CustomTestAction.OnValidMaxChange -> {
                val text = action.value.filterNumeric()
                _uiState.update {
                    it.copy(
                        validMaxText = text,
                        draft = it.draft.copy(validMax = parseScore(text, it.draft.paradigm)),
                        errors = it.errors - CustomTestField.VALID_RANGE
                    )
                }
            }

            CustomTestAction.OnToggleAdvanced ->
                _uiState.update { it.copy(isAdvancedExpanded = !it.isAdvancedExpanded) }

            is CustomTestAction.OnScoringEnabledChange -> {
                _uiState.update { state ->
                    state.copy(
                        draft = state.draft.copy(
                            scoring = if (action.enabled) state.draft.scoring ?: ScoringBands() else null
                        ),
                        // Keep the typed text when toggling off and back on — a coach who
                        // taps the wrong radio shouldn't lose three numbers.
                        cutPointText = if (action.enabled) state.cutPointText else state.cutPointText,
                        errors = state.errors - CustomTestField.BANDS
                    )
                }
            }

            is CustomTestAction.OnSameForAllSexesChange -> {
                _uiState.update { state ->
                    val scoring = state.draft.scoring ?: ScoringBands()
                    state.copy(
                        draft = state.draft.copy(
                            scoring = scoring.copy(sameForAllSexes = action.same)
                        ),
                        errors = state.errors - CustomTestField.BANDS
                    )
                }
            }

            is CustomTestAction.OnCutPointChange -> updateCutPoint(action)

            CustomTestAction.OnSave -> save()

            CustomTestAction.NavigationConsumed ->
                _uiState.update { it.copy(savedTestId = null) }

            CustomTestAction.OnDismissError ->
                _uiState.update { it.copy(errorMessage = null) }

            CustomTestAction.OnNavigateBack -> Unit // navigation is the screen's job
        }
    }

    /**
     * Applies an edit and clears that field's error.
     *
     * Errors surface on save and disappear as soon as the coach touches the field again —
     * re-validating on every keystroke would flash "give the test a name" at someone who
     * has simply not finished typing.
     */
    private fun updateDraft(clears: CustomTestField?, transform: (CustomTestDraft) -> CustomTestDraft) {
        _uiState.update {
            it.copy(
                draft = transform(it.draft),
                errors = if (clears != null) it.errors - clears else it.errors
            )
        }
    }

    private fun updateCutPoint(action: CustomTestAction.OnCutPointChange) {
        val text = action.value.filterNumeric()
        _uiState.update { state ->
            val scoring = state.draft.scoring ?: ScoringBands()
            val parsed = parseScore(text, state.draft.paradigm)

            fun List<Double?>.withValue() =
                toMutableList().also { it[action.index] = parsed }

            val updated = when (action.sex) {
                null -> scoring.copy(shared = scoring.shared.withValue())
                BiologicalSex.FEMALE -> scoring.copy(female = scoring.female.withValue())
                else -> scoring.copy(male = scoring.male.withValue())
            }

            state.copy(
                draft = state.draft.copy(scoring = updated),
                cutPointText = state.cutPointText + (CutPointKey(action.sex, action.index) to text),
                errors = state.errors - CustomTestField.BANDS
            )
        }
    }

    private fun save() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errors = emptyMap()) }

        viewModelScope.launch {
            try {
                when (val result = saveCustomTest(_uiState.value.draft)) {
                    is SaveCustomTestResult.Success ->
                        _uiState.update { it.copy(isSaving = false, savedTestId = result.testId) }

                    is SaveCustomTestResult.Invalid ->
                        _uiState.update { state ->
                            state.copy(
                                isSaving = false,
                                errors = result.errors.associate { it.field to it.message }
                            )
                        }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = e.message ?: "Couldn't save the test")
                }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            getTestLibrary.getCategories()
                .catch { e -> _uiState.update { it.copy(errorMessage = e.message, isLoading = false) } }
                .collect { categories ->
                    _uiState.update { state ->
                        state.copy(
                            categories = categories,
                            // Preselect the first category so the commonest path is one tap
                            // shorter, without overriding a choice already made.
                            draft = if (state.draft.categoryId.isBlank() && !state.isEditMode) {
                                state.draft.copy(categoryId = categories.firstOrNull()?.id.orEmpty())
                            } else {
                                state.draft
                            }
                        )
                    }
                }
        }
    }

    private fun hydrateDraft(testId: String) {
        viewModelScope.launch {
            val draft = getCustomTestDraft(testId)
            if (draft == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "That test can't be edited")
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    draft = draft,
                    validMinText = draft.validMin?.toEditText(draft.paradigm).orEmpty(),
                    validMaxText = draft.validMax?.toEditText(draft.paradigm).orEmpty(),
                    cutPointText = draft.scoring.toCutPointText(draft.paradigm),
                    // Open the section if the coach previously set something in it, so the
                    // saved values aren't hidden behind a collapsed header on reopen.
                    isAdvancedExpanded = draft.validMin != null ||
                        draft.validMax != null ||
                        draft.trialsPerAthlete > 1,
                    isLoading = false
                )
            }
        }
    }
}

/** Seeds the editable text for every cut point so an edited test shows its saved numbers. */
private fun ScoringBands?.toCutPointText(paradigm: InputParadigm): Map<CutPointKey, String> {
    if (this == null) return emptyMap()
    val out = mutableMapOf<CutPointKey, String>()
    shared.forEachIndexed { i, v -> v?.let { out[CutPointKey(null, i)] = it.toEditText(paradigm) } }
    male.forEachIndexed { i, v -> v?.let { out[CutPointKey(BiologicalSex.MALE, i)] = it.toEditText(paradigm) } }
    female.forEachIndexed { i, v -> v?.let { out[CutPointKey(BiologicalSex.FEMALE, i)] = it.toEditText(paradigm) } }
    return out
}

private val CustomTestDraft.paradigm: InputParadigm
    get() = measurementMethod.inputParadigm

/**
 * Switching to or from Levels changes what the typed text means ("8.10" is 8.1 as a number
 * but level 8 shuttle 10 as a stage), so every numeric field is re-read under the new method
 * rather than keeping values parsed under the old one.
 */
private fun CustomTestUiState.withMeasurementMethod(method: MeasurementMethod): CustomTestUiState {
    val paradigm = method.inputParadigm
    fun List<Double?>.reparse(sex: BiologicalSex?) =
        mapIndexed { i, old -> cutPointText[CutPointKey(sex, i)]?.let { parseScore(it, paradigm) } ?: old }
    val scoring = draft.scoring?.let {
        it.copy(shared = it.shared.reparse(null), male = it.male.reparse(BiologicalSex.MALE), female = it.female.reparse(BiologicalSex.FEMALE))
    }
    return copy(
        draft = draft.copy(
            measurementMethod = method,
            validMin = parseScore(validMinText, paradigm),
            validMax = parseScore(validMaxText, paradigm),
            scoring = scoring
        )
    )
}

/**
 * Keeps only characters that can form a number, so the parse is predictable. A leading minus
 * survives: reach tests (sit-and-reach, back scratch) record a gap as a negative score, and
 * stripping it made those standards impossible to author.
 */
private fun String.filterNumeric(): String =
    filterIndexed { i, c -> c.isDigit() || c == '.' || (c == '-' && i == 0) }

/** 8.0 -> "8", 8.5 -> "8.5", and a stage as "8.10" — no trailing ".0" in the edit form. */
private fun Double.toEditText(paradigm: InputParadigm): String = when {
    paradigm == InputParadigm.MULTI_STAGE -> StageScore.format(this)
    this % 1.0 == 0.0 -> toLong().toString()
    else -> toString()
}
