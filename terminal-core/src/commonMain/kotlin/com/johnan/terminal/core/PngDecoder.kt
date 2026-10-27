package com.johnan.terminal.core

/**
 * Pure Kotlin PNG decoder supporting 8-bit PNG images.
 */
object PngDecoder {
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50.toByte(),
        0x4E.toByte(),
        0x47.toByte(),
        0x0D.toByte(),
        0x0A.toByte(),
        0x1A.toByte(),
        0x0A.toByte(),
    )

    data class DecodedPng(
        val width: Int,
        val height: Int,
        val rgbaBytes: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DecodedPng) return false
            return width == other.width && height == other.height && rgbaBytes.contentEquals(other.rgbaBytes)
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + rgbaBytes.contentHashCode()
            return result
        }
    }

    /**
     * Decodes PNG byte array into raw RGBA pixels.
     *
     * @param pngBytes Raw PNG file bytes.
     * @param maxOutputBytes Maximum allowed RGBA byte count (width * height * 4).
     * @return Decoded width, height, and RGBA pixel byte array.
     */
    fun decode(pngBytes: ByteArray, maxOutputBytes: Int = 32 * 1024 * 1024): DecodedPng {
        require(pngBytes.size >= 8) { "PNG file too short: ${pngBytes.size} bytes" }
        for (i in 0 until 8) {
            require(pngBytes[i] == PNG_SIGNATURE[i]) { "Invalid PNG signature" }
        }

        var offset = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        var plte: ByteArray? = null
        var trns: ByteArray? = null
        val idatChunks = mutableListOf<ByteArray>()
        var totalIdatBytes = 0

        while (offset + 8 <= pngBytes.size) {
            val length = readInt32(pngBytes, offset)
            require(length >= 0) { "Invalid PNG chunk length: $length" }
            offset += 4
            val type = (0 until 4).map { pngBytes[offset + it].toInt().toChar() }.joinToString("")
            offset += 4
            require(offset + length + 4 <= pngBytes.size) { "Premature end of PNG file in chunk $type" }

            when (type) {
                "IHDR" -> {
                    width = readInt32(pngBytes, offset)
                    height = readInt32(pngBytes, offset + 4)
                    bitDepth = pngBytes[offset + 8].toInt() and 0xFF
                    colorType = pngBytes[offset + 9].toInt() and 0xFF
                    val compression = pngBytes[offset + 10].toInt() and 0xFF
                    val filter = pngBytes[offset + 11].toInt() and 0xFF
                    val interlace = pngBytes[offset + 12].toInt() and 0xFF

                    require(width > 0 && height > 0) { "Invalid PNG dimensions: ${width}x$height" }
                    require(bitDepth == 8) { "Unsupported PNG bit depth: $bitDepth (only bitDepth=8 supported)" }
                    require(colorType in intArrayOf(0, 2, 3, 4, 6)) { "Unsupported PNG color type: $colorType" }
                    require(compression == 0) { "Unsupported PNG compression method: $compression" }
                    require(filter == 0) { "Unsupported PNG filter method: $filter" }
                    require(interlace == 0) { "Interlaced PNG is not supported" }

                    if (width.toLong() * height.toLong() * 4L > maxOutputBytes.toLong()) {
                        throw IllegalArgumentException("PNG dimensions exceed maxOutputBytes")
                    }
                }
                "PLTE" -> {
                    plte = pngBytes.copyOfRange(offset, offset + length)
                }
                "tRNS" -> {
                    trns = pngBytes.copyOfRange(offset, offset + length)
                }
                "IDAT" -> {
                    val chunk = pngBytes.copyOfRange(offset, offset + length)
                    idatChunks.add(chunk)
                    totalIdatBytes += length
                }
                "IEND" -> {
                    offset += length + 4
                    break
                }
            }
            offset += length + 4
        }

        require(width > 0 && height > 0 && totalIdatBytes > 0) { "Incomplete PNG data" }

        val idatBytes = ByteArray(totalIdatBytes)
        var idatOffset = 0
        for (chunk in idatChunks) {
            chunk.copyInto(idatBytes, idatOffset)
            idatOffset += chunk.size
        }

        val decompressed = ZlibInflater.inflate(idatBytes, maxOutputBytes)

        val bytesPerPixel = when (colorType) {
            0 -> 1
            2 -> 3
            3 -> 1
            4 -> 2
            6 -> 4
            else -> throw IllegalArgumentException("Unsupported color type: $colorType")
        }

        val scanlineDataSize = width * bytesPerPixel
        val expectedScanlineLength = 1 + scanlineDataSize
        require(decompressed.size == height * expectedScanlineLength) {
            "Decompressed PNG data size mismatch: expected ${height * expectedScanlineLength}, got ${decompressed.size}"
        }

        val rgbaBytes = ByteArray(width * height * 4)
        var prevScanline = ByteArray(scanlineDataSize)
        var currScanline = ByteArray(scanlineDataSize)
        var decompOffset = 0

        val trnsR = if (colorType == 2 && trns != null && trns.size >= 6) (trns[1].toInt() and 0xFF) else -1
        val trnsG = if (colorType == 2 && trns != null && trns.size >= 6) (trns[3].toInt() and 0xFF) else -1
        val trnsB = if (colorType == 2 && trns != null && trns.size >= 6) (trns[5].toInt() and 0xFF) else -1
        val trnsGray = if (colorType == 0 && trns != null && trns.size >= 2) (trns[1].toInt() and 0xFF) else -1

        for (y in 0 until height) {
            val filterType = decompressed[decompOffset++].toInt() and 0xFF
            for (x in 0 until scanlineDataSize) {
                val a = if (x >= bytesPerPixel) (currScanline[x - bytesPerPixel].toInt() and 0xFF) else 0
                val b = if (y > 0) (prevScanline[x].toInt() and 0xFF) else 0
                val c = if (x >= bytesPerPixel && y > 0) (prevScanline[x - bytesPerPixel].toInt() and 0xFF) else 0
                val raw = decompressed[decompOffset + x].toInt() and 0xFF
                val recon = when (filterType) {
                    0 -> raw
                    1 -> raw + a
                    2 -> raw + b
                    3 -> raw + (a + b) / 2
                    4 -> raw + paethPredictor(a, b, c)
                    else -> throw IllegalArgumentException("Unknown PNG filter type: $filterType")
                } and 0xFF
                currScanline[x] = recon.toByte()
            }
            decompOffset += scanlineDataSize

            val rowDstOffset = y * width * 4
            when (colorType) {
                6 -> {
                    currScanline.copyInto(rgbaBytes, rowDstOffset, 0, scanlineDataSize)
                }
                2 -> {
                    for (x in 0 until width) {
                        val srcIdx = x * 3
                        val dstIdx = rowDstOffset + x * 4
                        val r = currScanline[srcIdx].toInt() and 0xFF
                        val g = currScanline[srcIdx + 1].toInt() and 0xFF
                        val b = currScanline[srcIdx + 2].toInt() and 0xFF
                        val isTrns = (r == trnsR && g == trnsG && b == trnsB)
                        rgbaBytes[dstIdx] = r.toByte()
                        rgbaBytes[dstIdx + 1] = g.toByte()
                        rgbaBytes[dstIdx + 2] = b.toByte()
                        rgbaBytes[dstIdx + 3] = if (isTrns) 0 else 0xFF.toByte()
                    }
                }
                3 -> {
                    requireNotNull(plte) { "Missing PLTE chunk for indexed-color PNG" }
                    for (x in 0 until width) {
                        val idx = currScanline[x].toInt() and 0xFF
                        val plteIdx = idx * 3
                        require(plteIdx + 2 < plte.size) { "Palette index out of bounds: $idx" }
                        val dstIdx = rowDstOffset + x * 4
                        val r = plte[plteIdx]
                        val g = plte[plteIdx + 1]
                        val b = plte[plteIdx + 2]
                        val a = if (trns != null && idx < trns.size) trns[idx] else 0xFF.toByte()
                        rgbaBytes[dstIdx] = r
                        rgbaBytes[dstIdx + 1] = g
                        rgbaBytes[dstIdx + 2] = b
                        rgbaBytes[dstIdx + 3] = a
                    }
                }
                4 -> {
                    for (x in 0 until width) {
                        val gray = currScanline[x * 2]
                        val alpha = currScanline[x * 2 + 1]
                        val dstIdx = rowDstOffset + x * 4
                        rgbaBytes[dstIdx] = gray
                        rgbaBytes[dstIdx + 1] = gray
                        rgbaBytes[dstIdx + 2] = gray
                        rgbaBytes[dstIdx + 3] = alpha
                    }
                }
                0 -> {
                    for (x in 0 until width) {
                        val gray = currScanline[x].toInt() and 0xFF
                        val dstIdx = rowDstOffset + x * 4
                        val isTrns = (gray == trnsGray)
                        rgbaBytes[dstIdx] = gray.toByte()
                        rgbaBytes[dstIdx + 1] = gray.toByte()
                        rgbaBytes[dstIdx + 2] = gray.toByte()
                        rgbaBytes[dstIdx + 3] = if (isTrns) 0 else 0xFF.toByte()
                    }
                }
            }

            val temp = prevScanline
            prevScanline = currScanline
            currScanline = temp
        }

        return DecodedPng(width, height, rgbaBytes)
    }

    private fun readInt32(data: ByteArray, offset: Int): Int = ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    private fun paethPredictor(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) {
            a
        } else if (pb <= pc) {
            b
        } else {
            c
        }
    }
}
