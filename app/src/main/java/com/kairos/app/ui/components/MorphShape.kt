package com.kairos.app.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

/**
 * `Shape` Compose qui rend un [Morph] (interpolation entre deux `RoundedPolygon`) à un [progress]
 * donné. Les `MaterialShapes` sont **normalisées dans [0, 1]** : on les met simplement à l'échelle
 * de la taille du composant (un `scale(size/2) + translate(1,1)` — prévu pour [-1, 1] — décalerait
 * la forme dans le quart bas-droit).
 *
 * Animer [progress] (0 → 1) via `animateFloatAsState` produit le **shape morphing** M3 Expressive.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        matrix.reset()
        matrix.scale(size.width, size.height)
        val path = morph.toPath(progress).asComposePath().apply { transform(matrix) }
        return Outline.Generic(path)
    }
}
