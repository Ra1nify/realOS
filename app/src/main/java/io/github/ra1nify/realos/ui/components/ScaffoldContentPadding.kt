package io.github.ra1nify.realos.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.compositionLocalOf

/**
 * Content padding published by realOS [Scaffold]; content applies it inside the scrolling container.
 *
 * `Modifier.padding(innerPadding)` on the outer container places content below the coui bar area.
 * The coui bars sample scrolling content into the backdrop. Content scrolled under the bar area
 * produces the ColorOS 17 gradient blur on the top bar. With the padding outside the container,
 * content stays below the bar area; the backdrop samples the solid background and the bar renders opaque.
 *
 * Usage:
 * - `LazyColumn(contentPadding = LocalScaffoldContentPadding.current) { ... }`
 * - `Column(Modifier.verticalScroll(...).padding(LocalScaffoldContentPadding.current)) { ... }`
 */
val LocalScaffoldContentPadding = compositionLocalOf { PaddingValues() }
