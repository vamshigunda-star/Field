package com.vamshi.field.ui.roster

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.vamshi.field.ui.components.RegisterAthleteSheet

@Composable
fun RosterDialogs(
    uiState: RosterUiState,
    onAction: (RosterAction) -> Unit
) {
    // Modern Bottom Sheets
    if (uiState.showAddGroupDialog) {
        AddGroupSheet(
            onDismiss = { onAction(RosterAction.OnDismissAddGroupDialog) },
            onConfirm = { name, location, cycle -> onAction(RosterAction.OnCreateGroup(name, location, cycle)) }
        )
    }

    if (uiState.showRegisterAthleteDialog) {
        RegisterAthleteSheet(
            onDismiss = { onAction(RosterAction.OnDismissRegisterAthleteDialog) },
            onConfirm = { first, last, dob, sex, email, med -> 
                onAction(RosterAction.OnRegisterAthlete(first, last, dob, sex, email, med)) 
            }
        )
    }

    if (uiState.showAddToGroupDialog) {
        AddToGroupSelectionSheet(
            groups = uiState.groups,
            onDismiss = { onAction(RosterAction.OnDismissAddToGroupDialog) },
            onGroupSelected = { onAction(RosterAction.OnAddSelectedToGroup(it)) },
            onCreateGroup = { onAction(RosterAction.OnShowAddGroupDialog) }
        )
    }

    uiState.showManageMembersDialog?.let { groupId ->
        val members = uiState.groupMembers[groupId] ?: emptyList()
        ManageGroupMembersSheet(
            allAthletes = uiState.allAthletes,
            currentAthleteIds = members.map { it.id }.toSet(),
            onDismiss = { onAction(RosterAction.OnDismissManageMembersDialog) },
            onAddAthlete = { id -> onAction(RosterAction.OnAddAthleteToGroup(groupId, id)) },
            onRemoveAthlete = { id -> onAction(RosterAction.OnRemoveAthleteFromGroup(groupId, id)) }
        )
    }

    // Confirmation Dialogs
    uiState.showDeleteAthleteConfirmation?.let { id ->
        val athlete = uiState.allAthletes.find { it.id == id }
        ConfirmationDialog(
            title = "Delete Athlete",
            message = "Are you sure you want to delete ${athlete?.fullName}? This action cannot be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { onAction(RosterAction.OnConfirmDeleteAthlete(id)) },
            onDismiss = { onAction(RosterAction.OnDismissDeleteConfirmation) }
        )
    }

    uiState.showRemoveMemberConfirmation?.let { (groupId, individualId) ->
        val athlete = uiState.allAthletes.find { it.id == individualId }
        val group = uiState.groups.find { it.id == groupId }
        ConfirmationDialog(
            title = "Remove from Group",
            message = "Remove ${athlete?.fullName} from ${group?.name}?",
            confirmText = "Remove",
            isDestructive = true,
            onConfirm = { onAction(RosterAction.OnConfirmRemoveMember(groupId, individualId)) },
            onDismiss = { onAction(RosterAction.OnDismissRemoveMemberConfirmation) }
        )
    }

    uiState.showDeleteGroupConfirmation?.let { group ->
        ConfirmationDialog(
            title = "Delete Group",
            message = "Are you sure you want to delete \"${group.name}\"? Athletes in this group will remain in your roster, and past testing events will become ungrouped.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { onAction(RosterAction.OnConfirmDeleteGroup(group)) },
            onDismiss = { onAction(RosterAction.OnDismissDeleteGroupConfirmation) }
        )
    }
}
