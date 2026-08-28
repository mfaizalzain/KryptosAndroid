package com.kryptos.vault.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
//  Kryptos Design Tokens — one cohesive, premium brand palette.
//
//  Identity: "A private vault of gold." Deep matte slate + a refined amber/gold
//  accent. Previously the app used three competing identities (neon cyan on the
//  scan screens, hot gold on the lock screen, and 10 unrelated card gradients).
//  Every screen now resolves through these tokens so the app reads as one brand.
// =============================================================================

// --- Brand accent (gold / amber) ---------------------------------------------
val BrandGold = Color(0xFFC9A227)          // Refined gold — buttons, highlights
val BrandGoldDeep = Color(0xFF9F7E3B)      // Deep antique gold — on light surfaces
val BrandGoldSoft = Color(0xFFF0DFB2)      // Soft champagne — containers on light
val BrandGoldFaint = Color(0xFFFBF4E3)     // Pale wash — on light primary containers
val BrandGoldOn = Color(0xFF3F3214)        // Text on gold (never pure black)
val BrandGoldOnDeep = Color(0xFF14203A)    // Text on deep-gold filled buttons
val BrandGoldChip = Color(0xFF3B2F14)      // Gold on dark containers

// --- Slate neutrals (Light) ---------------------------------------------------
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50  = Color(0xFFF8FAFC)

// --- Light scheme -------------------------------------------------------------
val PrimaryLight = BrandGoldDeep
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = BrandGoldFaint
val OnPrimaryContainerLight = Color(0xFF4A3816)

val SecondaryLight = Slate600
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Slate200
val OnSecondaryContainerLight = Slate800

val TertiaryLight = Color(0xFF8A6A2B)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = BrandGoldSoft
val OnTertiaryContainerLight = Color(0xFF4E3700)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Slate50
val OnBackgroundLight = Slate900
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Slate800
val SurfaceVariantLight = Slate100
val OnSurfaceVariantLight = Slate600
val OutlineLight = Slate400
val OutlineVariantLight = Slate300

// --- Dark scheme --------------------------------------------------------------
val PrimaryDark = Color(0xFFE2C175)
val OnPrimaryDark = Color(0xFF142900)
val PrimaryContainerDark = Color(0xFF4A3C22)
val OnPrimaryContainerDark = Color(0xFFFEEFC8)

val SecondaryDark = Color(0xFF8E9BAE)
val OnSecondaryDark = Color(0xFF10161F)
val SecondaryContainerDark = Color(0xFF2C3545)
val OnSecondaryContainerDark = Slate200

val TertiaryDark = Color(0xFFD4AF37)
val OnTertiaryDark = Color(0xFF142900)
val TertiaryContainerDark = Color(0xFF2E2718)
val OnTertiaryContainerDark = Color(0xFFFFF6E0)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF0E121B)
val OnBackgroundDark = Slate200
val SurfaceDark = Color(0xFF171D29)
val OnSurfaceDark = Slate100
val SurfaceVariantDark = Color(0xFF232C3B)
val OnSurfaceVariantDark = Slate400
val OutlineDark = Slate600
val OutlineVariantDark = Slate700

// --- Shared (light + dark) ----------------------------------------------------
val InversePrimaryLight = PrimaryDark
val InversePrimaryDark = PrimaryLight
val ScrimBlack = Color(0xFF000000)
