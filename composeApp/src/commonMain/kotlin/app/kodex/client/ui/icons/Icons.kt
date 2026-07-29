package app.kodex.client.ui.icons

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

/** Material "file_download" glyph — not in material-icons-core, so defined here. */
val DownloadIcon: ImageVector = materialIcon(name = "Filled.Download") {
    materialPath {
        moveTo(19f, 9f)
        horizontalLineToRelative(-4f)
        verticalLineTo(3f)
        horizontalLineTo(9f)
        verticalLineToRelative(6f)
        horizontalLineTo(5f)
        lineToRelative(7f, 7f)
        lineToRelative(7f, -7f)
        close()
        moveTo(5f, 18f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        close()
    }
}

/** Material "filter_list" (funnel) glyph for the filter button. */
val FilterIcon: ImageVector = materialIcon(name = "Filled.FilterList") {
    materialPath {
        moveTo(10f, 18f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(2f)
        close()
        moveTo(3f, 6f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(18f)
        verticalLineTo(6f)
        horizontalLineTo(3f)
        close()
        moveTo(6f, 13f)
        horizontalLineToRelative(12f)
        verticalLineToRelative(-2f)
        horizontalLineTo(6f)
        verticalLineToRelative(2f)
        close()
    }
}

/** Material "check_box" glyph — filled checkbox, used for the "Select all" selection action. */
val SelectAllIcon: ImageVector = materialIcon(name = "Filled.CheckBox") {
    materialPath {
        moveTo(19f, 3f)
        horizontalLineTo(5f)
        curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        curveToRelative(1.11f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(5f)
        curveToRelative(0f, -1.1f, -0.89f, -2f, -2f, -2f)
        close()
        moveToRelative(-9f, 14f)
        lineToRelative(-5f, -5f)
        lineToRelative(1.41f, -1.41f)
        lineTo(10f, 14.17f)
        lineToRelative(7.59f, -7.59f)
        lineTo(19f, 8f)
        lineToRelative(-9f, 9f)
        close()
    }
}

/** Material "invert_colors" glyph — half-filled drop, used for the "Select inverse" action. */
val InvertSelectionIcon: ImageVector = materialIcon(name = "Filled.InvertColors") {
    materialPath {
        moveTo(17.66f, 7.93f)
        lineTo(12f, 2.27f)
        lineTo(6.34f, 7.93f)
        curveToRelative(-3.12f, 3.12f, -3.12f, 8.19f, 0f, 11.31f)
        curveTo(7.9f, 20.8f, 9.95f, 21.58f, 12f, 21.58f)
        curveToRelative(2.05f, 0f, 4.1f, -0.78f, 5.66f, -2.34f)
        curveToRelative(3.12f, -3.12f, 3.12f, -8.19f, 0f, -11.31f)
        close()
        moveTo(12f, 19.59f)
        curveToRelative(-1.6f, 0f, -3.11f, -0.62f, -4.24f, -1.76f)
        curveTo(6.62f, 16.69f, 6f, 15.19f, 6f, 13.59f)
        reflectiveCurveToRelative(0.62f, -3.11f, 1.76f, -4.24f)
        lineTo(12f, 5.1f)
        verticalLineToRelative(14.49f)
        close()
    }
}

/** A ring (outlined circle) — used for the "Mark as unread" bottom-bar action. */
val MarkUnreadIcon: ImageVector = materialIcon(name = "Filled.MarkUnread") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 3f)
        arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
        arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
        close()
        moveTo(12f, 6f)
        arcToRelative(6f, 6f, 0f, true, false, 0f, 12f)
        arcToRelative(6f, 6f, 0f, true, false, 0f, -12f)
        close()
    }
}

/** Material "label" (tag) glyph — used for the "Add to categories" bottom-bar action. */
val LabelIcon: ImageVector = materialIcon(name = "Filled.Label") {
    materialPath {
        moveTo(17.63f, 5.84f)
        curveTo(17.27f, 5.33f, 16.67f, 5f, 16f, 5f)
        lineTo(5f, 5.01f)
        curveTo(3.9f, 5.01f, 3f, 5.9f, 3f, 7f)
        verticalLineToRelative(10f)
        curveToRelative(0f, 1.1f, 0.9f, 1.99f, 2f, 1.99f)
        lineTo(16f, 19f)
        curveToRelative(0.67f, 0f, 1.27f, -0.33f, 1.63f, -0.84f)
        lineTo(22f, 12f)
        lineToRelative(-4.37f, -6.16f)
        close()
    }
}

/** Material "language" (globe) glyph — used for the Browse language filter button. */
val LanguageIcon: ImageVector = materialIcon(name = "Filled.Language") {
    materialPath {
        moveTo(11.99f, 2f)
        curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
        close()
        moveTo(18.92f, 8f)
        horizontalLineToRelative(-2.95f)
        curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
        curveToRelative(1.84f, 0.63f, 3.37f, 1.91f, 4.33f, 3.56f)
        close()
        moveTo(12f, 4.04f)
        curveToRelative(0.83f, 1.2f, 1.48f, 2.53f, 1.91f, 3.96f)
        horizontalLineToRelative(-3.82f)
        curveToRelative(0.43f, -1.43f, 1.08f, -2.76f, 1.91f, -3.96f)
        close()
        moveTo(4.26f, 14f)
        curveTo(4.1f, 13.36f, 4f, 12.69f, 4f, 12f)
        reflectiveCurveToRelative(0.1f, -1.36f, 0.26f, -2f)
        horizontalLineToRelative(3.38f)
        curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2f)
        curveToRelative(0f, 0.68f, 0.06f, 1.34f, 0.14f, 2f)
        horizontalLineTo(4.26f)
        close()
        moveTo(5.08f, 16f)
        horizontalLineToRelative(2.95f)
        curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
        curveToRelative(-1.84f, -0.63f, -3.37f, -1.9f, -4.33f, -3.56f)
        close()
        moveTo(8.03f, 8f)
        horizontalLineTo(5.08f)
        curveToRelative(0.96f, -1.66f, 2.49f, -2.93f, 4.33f, -3.56f)
        curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8f)
        close()
        moveTo(12f, 19.96f)
        curveToRelative(-0.83f, -1.2f, -1.48f, -2.53f, -1.91f, -3.96f)
        horizontalLineToRelative(3.82f)
        curveToRelative(-0.43f, 1.43f, -1.08f, 2.76f, -1.91f, 3.96f)
        close()
        moveTo(14.34f, 14f)
        horizontalLineTo(9.66f)
        curveToRelative(-0.09f, -0.66f, -0.16f, -1.32f, -0.16f, -2f)
        curveToRelative(0f, -0.68f, 0.07f, -1.35f, 0.16f, -2f)
        horizontalLineToRelative(4.68f)
        curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2f)
        curveToRelative(0f, 0.68f, -0.07f, 1.34f, -0.16f, 2f)
        close()
        moveTo(14.59f, 19.56f)
        curveToRelative(0.6f, -1.11f, 1.06f, -2.31f, 1.38f, -3.56f)
        horizontalLineToRelative(2.95f)
        curveToRelative(-0.96f, 1.65f, -2.49f, 2.93f, -4.33f, 3.56f)
        close()
        moveTo(16.36f, 14f)
        curveToRelative(0.08f, -0.66f, 0.14f, -1.32f, 0.14f, -2f)
        curveToRelative(0f, -0.68f, -0.06f, -1.34f, -0.14f, -2f)
        horizontalLineToRelative(3.38f)
        curveToRelative(0.16f, 0.64f, 0.26f, 1.31f, 0.26f, 2f)
        reflectiveCurveToRelative(-0.1f, 1.36f, -0.26f, 2f)
        horizontalLineToRelative(-3.38f)
        close()
    }
}

/** Material "visibility_off" (incognito-ish) glyph for the incognito toggle. */
val IncognitoIcon: ImageVector = materialIcon(name = "Filled.VisibilityOff") {
    materialPath {
        moveTo(12f, 7f)
        curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f)
        curveToRelative(0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
        lineToRelative(2.92f, 2.92f)
        curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
        curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
        curveToRelative(-1.4f, 0f, -2.74f, 0.25f, -3.98f, 0.7f)
        lineToRelative(2.16f, 2.16f)
        curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
        close()
        moveTo(2f, 4.27f)
        lineToRelative(2.28f, 2.28f)
        lineToRelative(0.46f, 0.46f)
        curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
        curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
        curveToRelative(1.55f, 0f, 3.03f, -0.3f, 4.38f, -0.84f)
        lineToRelative(0.42f, 0.42f)
        lineTo(19.73f, 22f)
        lineTo(21f, 20.73f)
        lineTo(3.27f, 3f)
        lineTo(2f, 4.27f)
        close()
        moveTo(7.53f, 9.8f)
        lineToRelative(1.55f, 1.55f)
        curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
        curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
        curveToRelative(0.22f, 0f, 0.44f, -0.03f, 0.65f, -0.08f)
        lineToRelative(1.55f, 1.55f)
        curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
        curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
        curveToRelative(0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
        close()
    }
}
