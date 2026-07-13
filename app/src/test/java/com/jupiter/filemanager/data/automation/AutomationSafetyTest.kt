package com.jupiter.filemanager.data.automation

import com.jupiter.filemanager.domain.model.AutomationDefaults
import com.jupiter.filemanager.domain.model.AutomationRule
import com.jupiter.filemanager.domain.model.AutomationSafety
import com.jupiter.filemanager.feature.automation.RuleBuilderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSafetyTest {

    @Test
    fun builtInPresetsAreFiveUniqueSuspendedSafeRules() {
        val presets = AutomationDefaults.rules

        assertEquals(5, presets.size)
        assertEquals(presets.size, presets.map { it.id }.distinct().size)
        assertTrue(presets.all { !it.enabled })
        assertTrue(presets.all { AutomationSafety.isSupportedAction(it.thenText) })
        assertTrue(presets.none { AutomationSafety.isDestructiveAction(it.thenText) })
    }

    @Test
    fun destructiveActionsAreNeverSupported() {
        listOf(
            "delete matching files",
            "remove it",
            "move to Trash",
            "ERASE old downloads",
            "wipe these files",
        ).forEach { action ->
            assertTrue(action, AutomationSafety.isDestructiveAction(action))
            assertFalse(action, AutomationSafety.isSupportedAction(action))
        }
    }

    @Test
    fun onlySafeOrganizationActionsAreSupported() {
        assertTrue(AutomationSafety.isSupportedAction("move to Documents"))
        assertTrue(AutomationSafety.isSupportedAction("move it to /Screenshots"))
        assertEquals("/Screenshots", AutomationSafety.moveDestination("move it to /Screenshots"))
        assertEquals("Documents", AutomationSafety.moveDestination("Move To Documents"))
        assertTrue(AutomationSafety.isSupportedAction("favorite"))
        assertFalse(AutomationSafety.isSupportedAction("move to"))
        assertFalse(AutomationSafety.isSupportedAction("upload to server"))
    }

    @Test
    fun firstRunSeedsPresetsButUpgradeKeepsExistingRulesAndDeletedAllStaysEmpty() {
        val existing = listOf(sampleRule())

        assertEquals(AutomationDefaults.rules, resolveAutomationRules(false, null))
        val upgraded = resolveAutomationRules(false, existing)
        assertEquals(existing.first(), upgraded.first())
        assertEquals(6, upgraded.size)
        assertTrue(upgraded.containsAll(AutomationDefaults.rules))
        assertEquals(emptyList<AutomationRule>(), resolveAutomationRules(true, emptyList()))
    }

    @Test
    fun upgradeDoesNotDuplicateOrOverwriteAnExistingStablePresetId() {
        val customizedPreset = AutomationDefaults.rules.first().copy(
            name = "My renamed PDF rule",
            enabled = true,
        )

        val upgraded = resolveAutomationRules(false, listOf(customizedPreset))

        assertEquals(5, upgraded.size)
        assertEquals(customizedPreset, upgraded.first())
        assertEquals(upgraded.size, upgraded.map { it.id }.distinct().size)
    }

    @Test
    fun builderCannotSaveDestructiveOrUnknownAction() {
        val base = RuleBuilderUiState(
            name = "Example",
            whenText = "PDF files in Downloads",
        )

        assertFalse(base.copy(thenText = "delete files").canSave)
        assertFalse(base.copy(thenText = "send somewhere").canSave)
        assertTrue(base.copy(thenText = "move to Documents").canSave)
    }

    private fun sampleRule() = AutomationRule(
        id = "existing",
        name = "Existing user rule",
        enabled = true,
        whenText = "PDF files in Downloads",
        thenText = "move to Documents",
    )
}
