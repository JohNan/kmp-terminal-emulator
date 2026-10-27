package com.johnan.terminal.core

/**
 * Image format codes defined in the Kitty Graphics Protocol.
 */
enum class KittyImageFormat(
    val code: Int
) {
    RGB(24),
    RGBA(32),
    PNG(100),
    ;

    companion object {
        fun fromCode(code: Int): KittyImageFormat? = entries.find { it.code == code }
    }
}

/**
 * In-memory representation of an image loaded via the Kitty Graphics Protocol.
 *
 * @property id Unique terminal image ID.
 * @property imageNumber Client-assigned image number (0 if unassigned).
 * @property widthPx Image width in pixels.
 * @property heightPx Image height in pixels.
 * @property rgbaBytes Raw decoded 32-bit RGBA pixel byte array (length = widthPx * heightPx * 4).
 * @property encodedPngBytes Original compressed PNG bytes if transferred as PNG, or null.
 * @property version Monotonic version counter incremented when images are stored or updated.
 */
class TerminalImage(
    val id: Int,
    val imageNumber: Int = 0,
    val widthPx: Int,
    val heightPx: Int,
    val rgbaBytes: ByteArray,
    val encodedPngBytes: ByteArray? = null,
    val version: Long = 0L,
) {
    val byteSize: Int get() = rgbaBytes.size + (encodedPngBytes?.size ?: 0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalImage) return false
        return id == other.id && version == other.version
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + version.hashCode()
        return result
    }

    override fun toString(): String =
        "TerminalImage(id=$id, imageNumber=$imageNumber, widthPx=$widthPx, heightPx=$heightPx, " +
            "byteSize=$byteSize, version=$version)"
}

/**
 * Placement instance of a [TerminalImage] anchored to a character cell on the terminal grid.
 *
 * @property imageId ID of the referenced [TerminalImage].
 * @property placementId Client-assigned placement ID (0 if unassigned).
 * @property anchorLine Target absolute line (scrollback + screen row on primary, screen row on alternate).
 * @property col Target 0-based column.
 * @property cols Number of columns the image spans.
 * @property rows Number of rows the image spans.
 * @property srcX Source rectangle X offset in image pixels (0 = left).
 * @property srcY Source rectangle Y offset in image pixels (0 = top).
 * @property srcWidth Source rectangle width in image pixels (0 = full width).
 * @property srcHeight Source rectangle height in image pixels (0 = full height).
 * @property cellOffsetX Sub-cell horizontal pixel offset within the anchor cell.
 * @property cellOffsetY Sub-cell vertical pixel offset within the anchor cell.
 * @property zIndex Stacking order relative to cell background rects and text.
 * @property isVirtual Virtual placement flag (U=1); virtual placements do not advance the cursor.
 */
data class TerminalImagePlacement(
    val imageId: Int,
    val placementId: Int = 0,
    val anchorLine: Int,
    val col: Int,
    val cols: Int,
    val rows: Int,
    val srcX: Int = 0,
    val srcY: Int = 0,
    val srcWidth: Int = 0,
    val srcHeight: Int = 0,
    val cellOffsetX: Int = 0,
    val cellOffsetY: Int = 0,
    val zIndex: Int = 0,
    val isVirtual: Boolean = false,
)
