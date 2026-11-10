package com.example.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight bundled Material-style Rounded vector icons used across Cute Share.
 * Replaces the ~25 MB material-icons-extended library to keep the APK compact.
 */
object AppIcons {

    private fun icon(
        name: String,
        autoMirror: Boolean = false,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror
    ).apply(block).build()

    private val black = SolidColor(Color.Black)

    val CallReceived: ImageVector by lazy {
        icon("CallReceived", autoMirror = true) {
            path(fill = black) {
                moveTo(19.3f, 4.71f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineTo(7f, 15.59f)
                verticalLineTo(10f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                verticalLineToRelative(8f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(8f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(8.41f)
                lineTo(19.3f, 6.12f)
                curveToRelative(0.38f, -0.38f, 0.38f, -1.02f, 0f, -1.41f)
                close()
            }
        }
    }

    val NearMe: ImageVector by lazy {
        icon("NearMe") {
            path(fill = black) {
                moveTo(18.75f, 3.94f)
                lineTo(4.07f, 10.08f)
                curveToRelative(-0.83f, 0.35f, -0.81f, 1.53f, 0.02f, 1.85f)
                lineTo(9.43f, 14f)
                curveToRelative(0.26f, 0.1f, 0.47f, 0.31f, 0.57f, 0.57f)
                lineToRelative(2.06f, 5.33f)
                curveToRelative(0.32f, 0.84f, 1.51f, 0.86f, 1.86f, 0.03f)
                lineToRelative(6.15f, -14.67f)
                curveToRelative(0.33f, -0.83f, -0.5f, -1.66f, -1.32f, -1.32f)
                close()
            }
        }
    }

    val Wifi: ImageVector by lazy {
        icon("Wifi") {
            path(fill = black) {
                moveTo(2.06f, 10.06f)
                curveToRelative(0.51f, 0.51f, 1.32f, 0.56f, 1.87f, 0.11f)
                curveToRelative(4.71f, -3.8f, 11.43f, -3.8f, 16.14f, 0f)
                curveToRelative(0.55f, 0.45f, 1.36f, 0.4f, 1.87f, -0.11f)
                curveToRelative(0.58f, -0.58f, 0.52f, -1.53f, -0.12f, -2.04f)
                curveToRelative(-5.74f, -4.64f, -13.9f, -4.64f, -19.64f, 0f)
                curveToRelative(-0.64f, 0.51f, -0.7f, 1.46f, -0.12f, 2.04f)
                close()
                moveTo(6.3f, 14.3f)
                curveToRelative(0.51f, 0.51f, 1.31f, 0.54f, 1.88f, 0.11f)
                curveToRelative(2.26f, -1.72f, 5.38f, -1.72f, 7.64f, 0f)
                curveToRelative(0.57f, 0.43f, 1.37f, 0.4f, 1.88f, -0.11f)
                curveToRelative(0.59f, -0.59f, 0.52f, -1.57f, -0.15f, -2.07f)
                curveToRelative(-3.29f, -2.47f, -7.81f, -2.47f, -11.1f, 0f)
                curveToRelative(-0.67f, 0.5f, -0.74f, 1.48f, -0.15f, 2.07f)
                close()
                moveTo(12f, 16f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
            }
        }
    }

    val WifiTethering: ImageVector by lazy {
        icon("WifiTethering") {
            path(fill = black) {
                moveTo(12f, 11f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 3f)
                curveTo(6.48f, 3f, 2f, 7.48f, 2f, 13f)
                curveToRelative(0f, 3.36f, 1.66f, 6.33f, 4.2f, 8.14f)
                curveToRelative(0.47f, 0.34f, 1.13f, 0.19f, 1.41f, -0.32f)
                curveToRelative(0.24f, -0.44f, 0.1f, -0.99f, -0.3f, -1.28f)
                curveTo(5.3f, 18.09f, 4f, 15.7f, 4f, 13f)
                curveToRelative(0f, -4.42f, 3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                curveToRelative(0f, 2.7f, -1.3f, 5.09f, -3.31f, 6.54f)
                curveToRelative(-0.4f, 0.29f, -0.54f, 0.84f, -0.3f, 1.28f)
                curveToRelative(0.28f, 0.51f, 0.94f, 0.66f, 1.41f, 0.32f)
                curveTo(20.34f, 19.33f, 22f, 16.36f, 22f, 13f)
                curveToRelative(0f, -5.52f, -4.48f, -10f, -10f, -10f)
                close()
                moveTo(12f, 7f)
                curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
                curveToRelative(0f, 2.02f, 1f, 3.81f, 2.53f, 4.9f)
                curveToRelative(0.47f, 0.33f, 1.13f, 0.18f, 1.41f, -0.32f)
                curveToRelative(0.24f, -0.43f, 0.09f, -0.97f, -0.31f, -1.25f)
                curveTo(8.64f, 15.61f, 8f, 14.38f, 8f, 13f)
                curveToRelative(0f, -2.21f, 1.79f, -4f, 4f, -4f)
                reflectiveCurveToRelative(4f, 1.79f, 4f, 4f)
                curveToRelative(0f, 1.38f, -0.64f, 2.61f, -1.63f, 3.33f)
                curveToRelative(-0.4f, 0.28f, -0.55f, 0.82f, -0.31f, 1.25f)
                curveToRelative(0.28f, 0.5f, 0.94f, 0.65f, 1.41f, 0.32f)
                curveTo(17f, 16.81f, 18f, 15.02f, 18f, 13f)
                curveToRelative(0f, -3.31f, -2.69f, -6f, -6f, -6f)
                close()
            }
        }
    }

    val Bluetooth: ImageVector by lazy {
        icon("Bluetooth") {
            path(fill = black) {
                moveTo(17f, 7f)
                lineToRelative(-4.29f, -4.29f)
                curveToRelative(-0.63f, -0.63f, -1.71f, -0.19f, -1.71f, 0.7f)
                verticalLineToRelative(6.18f)
                lineTo(7.11f, 5.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineTo(10.59f, 12f)
                lineTo(5.7f, 16.89f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(11f, 14.41f)
                verticalLineToRelative(6.18f)
                curveToRelative(0f, 0.89f, 1.08f, 1.34f, 1.71f, 0.71f)
                lineTo(17f, 17f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                lineTo(13.41f, 12f)
                lineTo(17f, 8.42f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.42f)
                close()
                moveTo(13f, 5.83f)
                lineToRelative(1.88f, 1.88f)
                lineTo(13f, 9.59f)
                verticalLineTo(5.83f)
                close()
                moveTo(14.88f, 16.29f)
                lineTo(13f, 18.17f)
                verticalLineToRelative(-3.76f)
                lineToRelative(1.88f, 1.88f)
                close()
            }
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            path(fill = black) {
                moveTo(10.59f, 4.59f)
                curveTo(10.21f, 4.21f, 9.7f, 4f, 9.17f, 4f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-8f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }
    }

    val FolderOpen: ImageVector by lazy {
        icon("FolderOpen") {
            path(fill = black) {
                moveTo(20f, 6f)
                horizontalLineToRelative(-8f)
                lineToRelative(-1.41f, -1.41f)
                curveTo(10.21f, 4.21f, 9.7f, 4f, 9.17f, 4f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 18f)
                horizontalLineTo(5f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineTo(9f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(14f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineToRelative(8f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                close()
            }
        }
    }

    val FolderZip: ImageVector by lazy {
        icon("FolderZip") {
            path(fill = black) {
                moveTo(20f, 6f)
                horizontalLineToRelative(-8f)
                lineToRelative(-1.41f, -1.41f)
                curveTo(10.21f, 4.21f, 9.7f, 4f, 9.17f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(18f, 12f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineTo(8f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val Speed: ImageVector by lazy {
        icon("Speed") {
            path(fill = black) {
                moveTo(19.46f, 10f)
                arcToRelative(1f, 1f, 0f, false, false, -0.07f, 1f)
                arcToRelative(7.55f, 7.55f, 0f, false, true, 0.52f, 1.81f)
                arcToRelative(8f, 8f, 0f, false, true, -0.69f, 4.73f)
                arcToRelative(1f, 1f, 0f, false, true, -0.89f, 0.53f)
                horizontalLineTo(5.68f)
                arcToRelative(1f, 1f, 0f, false, true, -0.89f, -0.54f)
                arcTo(8f, 8f, 0f, false, true, 13f, 6.06f)
                arcToRelative(7.69f, 7.69f, 0f, false, true, 2.11f, 0.56f)
                arcToRelative(1f, 1f, 0f, false, false, 1f, -0.07f)
                arcToRelative(1f, 1f, 0f, false, false, -0.17f, -1.76f)
                arcTo(10f, 10f, 0f, false, false, 3.35f, 19f)
                arcToRelative(2f, 2f, 0f, false, false, 1.72f, 1f)
                horizontalLineToRelative(13.85f)
                arcToRelative(2f, 2f, 0f, false, false, 1.74f, -1f)
                arcToRelative(10f, 10f, 0f, false, false, 0.55f, -8.89f)
                arcToRelative(1f, 1f, 0f, false, false, -1.75f, -0.11f)
                close()
            }
            path(fill = black) {
                moveTo(10.59f, 12.59f)
                arcToRelative(2f, 2f, 0f, false, false, 2.83f, 2.83f)
                lineToRelative(5.66f, -8.49f)
                close()
            }
        }
    }

    val Timer: ImageVector by lazy {
        icon("Timer") {
            path(fill = black) {
                moveTo(10f, 3f)
                horizontalLineToRelative(4f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-4f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(12f, 14f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineTo(9f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                close()
                moveTo(19.03f, 7.39f)
                lineToRelative(0.75f, -0.75f)
                curveToRelative(0.38f, -0.38f, 0.39f, -1.01f, 0f, -1.4f)
                curveToRelative(-0.39f, -0.39f, -1.01f, -0.38f, -1.4f, 0f)
                lineToRelative(-0.75f, 0.75f)
                curveTo(16.07f, 4.74f, 14.12f, 4f, 12f, 4f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.02f, 9f, 9f, 9f)
                reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
                curveToRelative(0f, -2.12f, -0.74f, -4.07f, -1.97f, -5.61f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
                reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = black) {
                moveTo(8f, 19f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                close()
                moveTo(14f, 7f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
                close()
            }
        }
    }

    val Image: ImageVector by lazy {
        icon("Image") {
            path(fill = black) {
                moveTo(21f, 19f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                close()
                moveTo(8.9f, 13.98f)
                lineToRelative(2.1f, 2.53f)
                lineToRelative(3.1f, -3.99f)
                curveToRelative(0.2f, -0.26f, 0.6f, -0.26f, 0.8f, 0.01f)
                lineToRelative(3.51f, 4.68f)
                curveToRelative(0.25f, 0.33f, 0.01f, 0.8f, -0.4f, 0.8f)
                horizontalLineTo(6.02f)
                curveToRelative(-0.42f, 0f, -0.65f, -0.48f, -0.39f, -0.81f)
                lineTo(8.12f, 14f)
                curveToRelative(0.19f, -0.26f, 0.57f, -0.27f, 0.78f, -0.02f)
                close()
            }
        }
    }

    val Android: ImageVector by lazy {
        icon("Android") {
            path(fill = black, pathFillType = PathFillType.EvenOdd) {
                moveTo(16.61f, 15.15f)
                curveToRelative(-0.46f, 0f, -0.84f, -0.37f, -0.84f, -0.83f)
                reflectiveCurveToRelative(0.38f, -0.83f, 0.84f, -0.83f)
                reflectiveCurveToRelative(0.84f, 0.37f, 0.84f, 0.83f)
                reflectiveCurveToRelative(-0.37f, 0.83f, -0.84f, 0.83f)
                close()
                moveTo(7.41f, 15.15f)
                curveToRelative(-0.46f, 0f, -0.84f, -0.37f, -0.84f, -0.83f)
                reflectiveCurveToRelative(0.38f, -0.83f, 0.84f, -0.83f)
                reflectiveCurveToRelative(0.84f, 0.37f, 0.84f, 0.83f)
                reflectiveCurveToRelative(-0.38f, 0.83f, -0.84f, 0.83f)
                close()
                moveTo(16.91f, 10.14f)
                lineToRelative(1.67f, -2.88f)
                curveToRelative(0.11f, -0.19f, 0.04f, -0.43f, -0.15f, -0.54f)
                curveToRelative(-0.19f, -0.11f, -0.43f, -0.04f, -0.54f, 0.15f)
                lineToRelative(-1.69f, 2.91f)
                curveTo(14.95f, 9.21f, 13.52f, 8.88f, 12.01f, 8.88f)
                reflectiveCurveToRelative(-2.94f, 0.33f, -4.19f, 0.9f)
                lineTo(6.13f, 6.87f)
                curveToRelative(-0.11f, -0.19f, -0.35f, -0.26f, -0.54f, -0.15f)
                curveToRelative(-0.19f, 0.11f, -0.26f, 0.35f, -0.15f, 0.54f)
                lineToRelative(1.67f, 2.88f)
                curveTo(4.15f, 11.73f, 2.18f, 14.69f, 2f, 18.14f)
                horizontalLineToRelative(20f)
                curveToRelative(-0.18f, -3.45f, -2.15f, -6.41f, -5.09f, -8f)
                close()
            }
        }
    }

    val DoneAll: ImageVector by lazy {
        icon("DoneAll") {
            path(fill = black) {
                moveTo(17.3f, 6.3f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                lineToRelative(-5.64f, 5.64f)
                lineToRelative(1.41f, 1.41f)
                lineTo(17.3f, 7.7f)
                curveToRelative(0.38f, -0.38f, 0.38f, -1.02f, 0f, -1.4f)
                close()
                moveTo(21.54f, 6.29f)
                lineToRelative(-9.88f, 9.88f)
                lineToRelative(-3.48f, -3.47f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0f, 1.41f)
                lineToRelative(4.18f, 4.18f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineTo(22.95f, 7.71f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0f, -1.41f)
                curveToRelative(-0.38f, -0.4f, -1.02f, -0.4f, -1.41f, -0.01f)
                close()
                moveTo(1.12f, 14.12f)
                lineTo(5.3f, 18.3f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0f)
                lineToRelative(0.7f, -0.7f)
                lineToRelative(-4.88f, -4.9f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.42f)
                close()
            }
        }
    }

    val DarkMode: ImageVector by lazy {
        icon("DarkMode") {
            path(fill = black) {
                moveTo(11.01f, 3.05f)
                curveTo(6.51f, 3.54f, 3f, 7.36f, 3f, 12f)
                curveToRelative(0f, 4.97f, 4.03f, 9f, 9f, 9f)
                curveToRelative(4.63f, 0f, 8.45f, -3.5f, 8.95f, -8f)
                curveToRelative(0.09f, -0.79f, -0.78f, -1.34f, -1.49f, -0.95f)
                curveToRelative(-1.05f, 0.57f, -2.26f, 0.87f, -3.56f, 0.78f)
                curveToRelative(-2.98f, -0.21f, -5.38f, -2.63f, -5.58f, -5.61f)
                curveToRelative(-0.08f, -1.25f, 0.21f, -2.41f, 0.74f, -3.41f)
                curveToRelative(0.38f, -0.72f, -0.18f, -1.56f, -0.99f, -1.47f)
                lineToRelative(-0.06f, 0.01f)
                close()
            }
        }
    }

    val LightMode: ImageVector by lazy {
        icon("LightMode") {
            path(fill = black) {
                moveTo(12f, 7f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
                reflectiveCurveToRelative(2.24f, 5f, 5f, 5f)
                reflectiveCurveToRelative(5f, -2.24f, 5f, -5f)
                reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f)
                close()
                moveTo(2f, 13f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(20f, 13f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(11f, 2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                verticalLineTo(2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                close()
                moveTo(11f, 20f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                close()
                moveTo(5.99f, 4.58f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0f)
                reflectiveCurveToRelative(0.39f, -1.03f, 0f, -1.41f)
                lineTo(5.99f, 4.58f)
                close()
                moveTo(18.36f, 16.95f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                lineToRelative(-1.06f, -1.06f)
                close()
                moveTo(19.42f, 5.99f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0f)
                lineToRelative(1.06f, -1.06f)
                close()
                moveTo(7.05f, 18.36f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0f)
                lineToRelative(1.06f, -1.06f)
                close()
            }
        }
    }

    val SettingsBrightness: ImageVector by lazy {
        icon("SettingsBrightness") {
            path(fill = black) {
                moveTo(21f, 3f)
                horizontalLineTo(3f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(20f, 19.01f)
                horizontalLineTo(4f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineTo(5.99f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(16f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                verticalLineToRelative(12.02f)
                curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
                close()
                moveTo(8f, 16f)
                horizontalLineToRelative(2.5f)
                lineToRelative(1.15f, 1.15f)
                curveToRelative(0.2f, 0.2f, 0.51f, 0.2f, 0.71f, 0f)
                lineTo(13.5f, 16f)
                horizontalLineTo(16f)
                verticalLineToRelative(-2.5f)
                lineToRelative(1.15f, -1.15f)
                curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0f, -0.71f)
                lineTo(16f, 10.5f)
                verticalLineTo(8f)
                horizontalLineToRelative(-2.5f)
                lineToRelative(-1.15f, -1.15f)
                curveToRelative(-0.2f, -0.2f, -0.51f, -0.2f, -0.71f, 0f)
                lineTo(10.5f, 8f)
                horizontalLineTo(8f)
                verticalLineToRelative(2.5f)
                lineToRelative(-1.15f, 1.15f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0f, 0.71f)
                lineTo(8f, 13.5f)
                verticalLineTo(16f)
                close()
                moveTo(12f, 9f)
                curveToRelative(1.66f, 0f, 3f, 1.34f, 3f, 3f)
                reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
                verticalLineTo(9f)
                close()
            }
        }
    }

    val RestartAlt: ImageVector by lazy {
        icon("RestartAlt") {
            path(fill = black) {
                moveTo(12f, 5f)
                verticalLineTo(2.21f)
                curveToRelative(0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
                lineTo(7.35f, 5.65f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0f, 0.71f)
                lineToRelative(3.79f, 3.79f)
                curveToRelative(0.32f, 0.31f, 0.86f, 0.09f, 0.86f, -0.36f)
                verticalLineTo(7f)
                curveToRelative(3.73f, 0f, 6.68f, 3.42f, 5.86f, 7.29f)
                curveToRelative(-0.47f, 2.27f, -2.31f, 4.1f, -4.57f, 4.57f)
                curveToRelative(-3.57f, 0.75f, -6.75f, -1.7f, -7.23f, -5.01f)
                curveToRelative(-0.07f, -0.48f, -0.49f, -0.85f, -0.98f, -0.85f)
                curveToRelative(-0.6f, 0f, -1.08f, 0.53f, -1f, 1.13f)
                curveToRelative(0.62f, 4.39f, 4.8f, 7.64f, 9.53f, 6.72f)
                curveToRelative(3.12f, -0.61f, 5.63f, -3.12f, 6.24f, -6.24f)
                curveTo(20.84f, 9.48f, 16.94f, 5f, 12f, 5f)
                close()
            }
        }
    }

    val SwapHoriz: ImageVector by lazy {
        icon("SwapHoriz") {
            path(fill = black) {
                moveTo(6.14f, 11.86f)
                lineTo(3.36f, 14.65f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0f, 0.71f)
                lineToRelative(2.79f, 2.79f)
                curveToRelative(0.31f, 0.31f, 0.85f, 0.09f, 0.85f, -0.36f)
                verticalLineTo(16f)
                horizontalLineToRelative(6f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(7f)
                verticalLineToRelative(-1.79f)
                curveToRelative(0f, -0.45f, -0.54f, -0.67f, -0.86f, -0.35f)
                close()
                moveTo(20.65f, 8.65f)
                lineToRelative(-2.79f, -2.79f)
                curveToRelative(-0.32f, -0.32f, -0.86f, -0.1f, -0.86f, 0.35f)
                verticalLineTo(8f)
                horizontalLineToRelative(-6f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(1.79f)
                curveToRelative(0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
                lineToRelative(2.79f, -2.79f)
                curveToRelative(0.2f, -0.19f, 0.2f, -0.51f, 0.01f, -0.7f)
                close()
            }
        }
    }

    val Videocam: ImageVector by lazy {
        icon("Videocam") {
            path(fill = black) {
                moveTo(17f, 10.5f)
                verticalLineTo(7f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                horizontalLineTo(4f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                horizontalLineToRelative(12f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-3.5f)
                lineToRelative(2.29f, 2.29f)
                curveToRelative(0.63f, 0.63f, 1.71f, 0.18f, 1.71f, -0.71f)
                verticalLineTo(8.91f)
                curveToRelative(0f, -0.89f, -1.08f, -1.34f, -1.71f, -0.71f)
                lineTo(17f, 10.5f)
                close()
            }
        }
    }

    val AudioFile: ImageVector by lazy {
        icon("AudioFile") {
            path(fill = black) {
                moveTo(14.59f, 2.59f)
                curveTo(14.21f, 2.21f, 13.7f, 2f, 13.17f, 2f)
                horizontalLineTo(6f)
                curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8.83f)
                curveToRelative(0f, -0.53f, -0.21f, -1.04f, -0.59f, -1.41f)
                lineToRelative(-4.82f, -4.83f)
                close()
                moveTo(15f, 13f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(3.61f)
                curveToRelative(0f, 1.28f, -1f, 2.34f, -2.28f, 2.39f)
                curveTo(9.4f, 19.05f, 8.3f, 18.04f, 8.25f, 16.72f)
                curveToRelative(-0.06f, -1.37f, 1.07f, -2.5f, 2.43f, -2.47f)
                curveToRelative(0.3f, 0.01f, 0.59f, 0.08f, 0.85f, 0.19f)
                verticalLineTo(12f)
                curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
                horizontalLineTo(15f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                close()
                moveTo(14f, 9f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(14f)
                close()
            }
        }
    }

    val Apps: ImageVector by lazy {
        icon("Apps") {
            path(fill = black) {
                moveTo(4f, 8f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(10f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4f)
                close()
                moveTo(10f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4f)
                close()
                moveTo(10f, 8f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 14f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
                moveTo(16f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(4f)
                close()
            }
        }
    }

    val Description: ImageVector by lazy {
        icon("Description") {
            path(fill = black) {
                moveTo(14.59f, 2.59f)
                curveTo(14.21f, 2.21f, 13.7f, 2f, 13.17f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8.83f)
                curveToRelative(0f, -0.53f, -0.21f, -1.04f, -0.59f, -1.41f)
                lineToRelative(-4.82f, -4.83f)
                close()
                moveTo(15f, 18f)
                horizontalLineTo(9f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(6f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                close()
                moveTo(15f, 14f)
                horizontalLineTo(9f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                horizontalLineToRelative(6f)
                curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                close()
                moveTo(13f, 8f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(14f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                close()
            }
        }
    }

    val Archive: ImageVector by lazy {
        icon("Archive") {
            path(fill = black) {
                moveTo(20.54f, 5.23f)
                lineToRelative(-1.39f, -1.68f)
                curveTo(18.88f, 3.21f, 18.47f, 3f, 18f, 3f)
                horizontalLineTo(6f)
                curveToRelative(-0.47f, 0f, -0.88f, 0.21f, -1.16f, 0.55f)
                lineTo(3.46f, 5.23f)
                curveTo(3.17f, 5.57f, 3f, 6.02f, 3f, 6.5f)
                verticalLineTo(19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6.5f)
                curveToRelative(0f, -0.48f, -0.17f, -0.93f, -0.46f, -1.27f)
                close()
                moveTo(11.65f, 17.15f)
                lineTo(6.5f, 12f)
                horizontalLineTo(10f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3.5f)
                lineToRelative(-5.15f, 5.15f)
                curveToRelative(-0.19f, 0.19f, -0.51f, 0.19f, -0.7f, 0f)
                close()
                moveTo(5.12f, 5f)
                lineToRelative(0.81f, -1f)
                horizontalLineToRelative(12f)
                lineToRelative(0.94f, 1f)
                horizontalLineTo(5.12f)
                close()
            }
        }
    }
}
