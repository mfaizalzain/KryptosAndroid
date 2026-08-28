package com.kryptos.vault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Kryptos shape system.
 *
 * A single, consistent rounded-corner language. Large surfaces (cards, dialogs,
 * sheets) round generously; interactive elements round moderately; chips and
 * small controls round fully. This replaces the ad-hoc per-screen corner radii
 * that ranged from 8dp to 32dp with no pattern.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
);

// Convenience aliases used widely by cards and buttons.
val AppShapeCard = RoundedCornerShape(20.dp)       // list tiles / hero cards
val AppShapeSheet = RoundedCornerShape(28.dp)      // modals & bottom sheets
val AppShapeChip = RoundedCornerShape(12.dp)       // chips / small controls
val AppShapeButton = RoundedCornerShape(16.dp)     // primary buttons
