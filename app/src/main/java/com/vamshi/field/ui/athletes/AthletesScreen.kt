package com.vamshi.field.ui.athletes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamshi.field.ui.components.AppTopBar
import com.vamshi.field.ui.roster.AthleteTabContent
import com.vamshi.field.ui.roster.RosterAction
import com.vamshi.field.ui.roster.RosterDialogs
import com.vamshi.field.ui.roster.RosterViewModel
import com.vamshi.field.ui.theme.SportOrange

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll

import androidx.compose.foundation.layout.WindowInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthletesScreen(
    onNavigateToAthleteReport: (String) -> Unit,
    viewModel: RosterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = "Athletes",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { viewModel.onAction(RosterAction.OnShowRegisterAthleteDialog) }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Register Athlete")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onAction(RosterAction.OnShowRegisterAthleteDialog) },
                containerColor = SportOrange,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Athlete")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            AthleteTabContent(
                uiState = uiState,
                onAction = { action ->
                    when (action) {
                        is RosterAction.OnNavigateToAthleteReport -> onNavigateToAthleteReport(action.individualId)
                        else -> viewModel.onAction(action)
                    }
                }
            )
        }
    }

    RosterDialogs(uiState = uiState, onAction = { action ->
        when (action) {
            is RosterAction.OnNavigateToAthleteReport -> onNavigateToAthleteReport(action.individualId)
            else -> viewModel.onAction(action)
        }
    })
}
