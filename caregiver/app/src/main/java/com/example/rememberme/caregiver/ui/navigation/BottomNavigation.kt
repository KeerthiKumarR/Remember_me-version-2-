package com.example.rememberme.caregiver.ui.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rememberme.caregiver.ui.theme.BorderColor
import com.example.rememberme.caregiver.ui.theme.InkColor
import com.example.rememberme.caregiver.ui.theme.MintColor

@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    val items = listOf(
        Screen.SosAlert,
        Screen.VisitorLog,
        Screen.DailySummary,
        Screen.Settings
    )

    NavigationBar(
        containerColor = InkColor,
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderColor)
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MintColor,
                    unselectedIconColor = Color.Gray,
                    selectedTextColor = MintColor,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = Color.White.copy(alpha = 0.05f)
                )
            )
        }
    }
}
