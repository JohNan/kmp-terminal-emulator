package com.johnan.terminal.core

/**
 * Pure Kotlin RFC 1950 (Zlib) and RFC 1951 (Deflate) decompressor.
 */
object ZlibInflater {
    private val CL_CODE_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private val LENGTH_BASE = intArrayOf(
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10,
        11,
        13,
        15,
        17,
        19,
        23,
        27,
        31,
        35,
        43,
        51,
        59,
        67,
        83,
        99,
        115,
        131,
        163,
        195,
        227,
        258,
    )
    private val LENGTH_EXTRA_BITS = intArrayOf(
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        1,
        1,
        1,
        1,
        2,
        2,
        2,
        2,
        3,
        3,
        3,
        3,
        4,
        4,
        4,
        4,
        5,
        5,
        5,
        5,
        0,
    )

    private val DIST_BASE = intArrayOf(
        1,
        2,
        3,
        4,
        5,
        7,
        9,
        13,
        17,
        25,
        33,
        49,
        65,
        97,
        129,
        193,
        257,
        385,
        513,
        769,
        1025,
        1537,
        2049,
        3073,
        4097,
        6145,
        8193,
        12289,
        16385,
        24577,
    )
    private val DIST_EXTRA_BITS = intArrayOf(
        0,
        0,
        0,
        0,
        1,
        1,
        2,
        2,
        3,
        3,
        4,
        4,
        5,
        5,
        6,
        6,
        7,
        7,
        8,
        8,
        9,
        9,
        10,
        10,
        11,
        11,
        12,
        12,
        13,
        13,
    )

    private val FIXED_LIT_LENGTHS = IntArray(288) { i ->
        when (i) {
            in 0..143 -> 8
            in 144..255 -> 9
            in 256..279 -> 7
            in 280..287 -> 8
            else -> 0
        }
    }
    private val FIXED_DIST_LENGTHS = IntArray(32) { 5 }

    private val FIXED_LIT_TREE by lazy { HuffmanTree(FIXED_LIT_LENGTHS) }
    private val FIXED_DIST_TREE by lazy { HuffmanTree(FIXED_DIST_LENGTHS) }

    /**
     * Inflates an RFC 1950 zlib-compressed payload.
     *
     * @param input Raw zlib-compressed bytes.
     * @param maxOutputBytes Maximum allowed decompressed byte count.
     * @return Decompressed byte array.
     */
    fun inflate(input: ByteArray, maxOutputBytes: Int = 32 * 1024 * 1024): ByteArray {
        require(input.size >= 6) { "Zlib input too short: ${input.size} bytes" }

        val cmf = input[0].toInt() and 0xFF
        val flg = input[1].toInt() and 0xFF
        require((cmf * 256 + flg) % 31 == 0) { "Invalid Zlib header checksum" }

        val cm = cmf and 0x0F
        require(cm == 8) { "Unsupported compression method: $cm" }

        val fdict = (flg and 0x20) != 0
        var offset = 2
        if (fdict) {
            offset += 4
        }

        val reader = BitReader(input, offset, input.size)
        var output = ByteArray(minOf(input.size * 2, maxOutputBytes).coerceAtLeast(1024))
        var outputSize = 0

        var isFinal = false
        while (!isFinal) {
            val bfinal = reader.readBits(1)
            isFinal = bfinal == 1
            val btype = reader.readBits(2)

            when (btype) {
                0 -> {
                    // Uncompressed block
                    reader.alignToByte()
                    val len = reader.readByte() or (reader.readByte() shl 8)
                    val nlen = reader.readByte() or (reader.readByte() shl 8)
                    require((len xor nlen) == 0xFFFF) { "Invalid uncompressed block length: len=$len, nlen=$nlen" }
                    if (outputSize + len > maxOutputBytes) {
                        throw IllegalArgumentException("Decompressed payload exceeds maxOutputBytes")
                    }
                    while (output.size < outputSize + len) {
                        val newCap = (output.size.toLong() * 2).coerceAtMost(maxOutputBytes.toLong()).toInt()
                        output = output.copyOf(maxOf(newCap, outputSize + len))
                    }
                    reader.readBytes(output, outputSize, len)
                    outputSize += len
                }
                1 -> {
                    // Compressed with fixed Huffman codes
                    outputSize = decodeHuffmanBlock(
                        reader,
                        FIXED_LIT_TREE,
                        FIXED_DIST_TREE,
                        output,
                        outputSize,
                        maxOutputBytes,
                    ).also { output = it.first }.second
                }
                2 -> {
                    // Compressed with dynamic Huffman codes
                    val hlit = reader.readBits(5) + 257
                    val hdist = reader.readBits(5) + 1
                    val hclen = reader.readBits(4) + 4

                    val clLengths = IntArray(19)
                    for (i in 0 until hclen) {
                        clLengths[CL_CODE_ORDER[i]] = reader.readBits(3)
                    }
                    val clTree = HuffmanTree(clLengths)

                    val totalCodes = hlit + hdist
                    val codeLengths = IntArray(totalCodes)
                    var i = 0
                    while (i < totalCodes) {
                        val sym = clTree.decode(reader)
                        when (sym) {
                            in 0..15 -> codeLengths[i++] = sym
                            16 -> {
                                require(i > 0) { "Repeat previous length at index 0" }
                                val prev = codeLengths[i - 1]
                                val repeatCount = reader.readBits(2) + 3
                                require(i + repeatCount <= totalCodes) { "Code length repeat overflow" }
                                repeat(repeatCount) { codeLengths[i++] = prev }
                            }
                            17 -> {
                                val repeatCount = reader.readBits(3) + 3
                                require(i + repeatCount <= totalCodes) { "Code length repeat overflow" }
                                repeat(repeatCount) { codeLengths[i++] = 0 }
                            }
                            18 -> {
                                val repeatCount = reader.readBits(7) + 11
                                require(i + repeatCount <= totalCodes) { "Code length repeat overflow" }
                                repeat(repeatCount) { codeLengths[i++] = 0 }
                            }
                            else -> throw IllegalArgumentException("Invalid code length symbol: $sym")
                        }
                    }

                    val litTree = HuffmanTree(codeLengths.copyOfRange(0, hlit))
                    val distTree = HuffmanTree(codeLengths.copyOfRange(hlit, totalCodes))

                    outputSize = decodeHuffmanBlock(
                        reader,
                        litTree,
                        distTree,
                        output,
                        outputSize,
                        maxOutputBytes,
                    ).also { output = it.first }.second
                }
                else -> throw IllegalArgumentException("Unsupported Deflate block type: $btype")
            }
        }

        // Validate Adler-32 checksum (4 bytes big-endian)
        reader.alignToByte()
        val chk0 = reader.readByte()
        val chk1 = reader.readByte()
        val chk2 = reader.readByte()
        val chk3 = reader.readByte()
        val expectedAdler = (chk0 shl 24) or (chk1 shl 16) or (chk2 shl 8) or chk3
        val actualAdler = computeAdler32(output, outputSize)
        require(expectedAdler == actualAdler) {
            "Adler-32 checksum mismatch: expected $expectedAdler, got $actualAdler"
        }

        return if (output.size == outputSize) output else output.copyOf(outputSize)
    }

    private fun decodeHuffmanBlock(
        reader: BitReader,
        litTree: HuffmanTree,
        distTree: HuffmanTree,
        initialOutput: ByteArray,
        initialOutputSize: Int,
        maxOutputBytes: Int,
    ): Pair<ByteArray, Int> {
        var output = initialOutput
        var outputSize = initialOutputSize

        while (true) {
            val sym = litTree.decode(reader)
            if (sym < 256) {
                if (outputSize >= maxOutputBytes) {
                    throw IllegalArgumentException("Decompressed payload exceeds maxOutputBytes")
                }
                if (outputSize == output.size) {
                    val newCap = (output.size.toLong() * 2).coerceAtMost(maxOutputBytes.toLong()).toInt()
                    output = output.copyOf(maxOf(newCap, outputSize + 1))
                }
                output[outputSize++] = sym.toByte()
            } else if (sym == 256) {
                // End of block
                break
            } else if (sym in 257..285) {
                val lenIdx = sym - 257
                val length = LENGTH_BASE[lenIdx] + reader.readBits(LENGTH_EXTRA_BITS[lenIdx])
                val distSym = distTree.decode(reader)
                require(distSym in 0..29) { "Invalid distance symbol: $distSym" }
                val distance = DIST_BASE[distSym] + reader.readBits(DIST_EXTRA_BITS[distSym])
                require(distance in 1..outputSize) {
                    "Invalid backward distance: $distance with current size $outputSize"
                }
                if (outputSize + length > maxOutputBytes) {
                    throw IllegalArgumentException("Decompressed payload exceeds maxOutputBytes")
                }
                while (output.size < outputSize + length) {
                    val newCap = (output.size.toLong() * 2).coerceAtMost(maxOutputBytes.toLong()).toInt()
                    output = output.copyOf(maxOf(newCap, outputSize + length))
                }
                for (k in 0 until length) {
                    output[outputSize] = output[outputSize - distance]
                    outputSize++
                }
            } else {
                throw IllegalArgumentException("Invalid literal/length symbol: $sym")
            }
        }
        return Pair(output, outputSize)
    }

    private fun computeAdler32(data: ByteArray, length: Int): Int {
        var s1 = 1L
        var s2 = 0L
        for (i in 0 until length) {
            s1 = (s1 + (data[i].toInt() and 0xFF)) % 65521L
            s2 = (s2 + s1) % 65521L
        }
        return ((s2 shl 16) or s1).toInt()
    }

    private class BitReader(
        private val data: ByteArray,
        private var byteOffset: Int,
        private val endOffset: Int,
    ) {
        private var bitBuffer = 0
        private var bitsInBuffer = 0

        fun readBits(n: Int): Int {
            if (n == 0) return 0
            while (bitsInBuffer < n) {
                if (byteOffset >= endOffset) {
                    throw IllegalArgumentException("Unexpected end of compressed stream")
                }
                bitBuffer = bitBuffer or ((data[byteOffset++].toInt() and 0xFF) shl bitsInBuffer)
                bitsInBuffer += 8
            }
            val result = bitBuffer and ((1 shl n) - 1)
            bitBuffer = bitBuffer ushr n
            bitsInBuffer -= n
            return result
        }

        fun alignToByte() {
            val unusedBytes = bitsInBuffer / 8
            byteOffset -= unusedBytes
            bitBuffer = 0
            bitsInBuffer = 0
        }

        fun readByte(): Int {
            alignToByte()
            if (byteOffset >= endOffset) {
                throw IllegalArgumentException("Unexpected end of compressed stream")
            }
            return data[byteOffset++].toInt() and 0xFF
        }

        fun readBytes(out: ByteArray, outOffset: Int, count: Int) {
            alignToByte()
            if (byteOffset + count > endOffset) {
                throw IllegalArgumentException("Unexpected end of compressed stream")
            }
            data.copyInto(out, outOffset, byteOffset, byteOffset + count)
            byteOffset += count
        }
    }

    private class HuffmanTree(
        codeLengths: IntArray
    ) {
        private val maxNodes = maxOf(64, codeLengths.size * 3)
        private val left = IntArray(maxNodes) { -1 }
        private val right = IntArray(maxNodes) { -1 }
        private val symbol = IntArray(maxNodes) { -1 }
        private var nodeCount = 1

        init {
            val maxLen = codeLengths.maxOrNull() ?: 0
            if (maxLen > 0) {
                val blCount = IntArray(maxLen + 1)
                for (len in codeLengths) {
                    if (len > 0) blCount[len]++
                }
                val nextCode = IntArray(maxLen + 1)
                var code = 0
                for (bits in 1..maxLen) {
                    code = (code + blCount[bits - 1]) shl 1
                    nextCode[bits] = code
                }
                for (sym in codeLengths.indices) {
                    val len = codeLengths[sym]
                    if (len > 0) {
                        val c = nextCode[len]++
                        var curr = 0
                        for (b in (len - 1) downTo 0) {
                            val bit = (c ushr b) and 1
                            if (bit == 0) {
                                if (left[curr] == -1) {
                                    require(nodeCount < maxNodes) { "Huffman tree node capacity exceeded" }
                                    left[curr] = nodeCount++
                                }
                                curr = left[curr]
                            } else {
                                if (right[curr] == -1) {
                                    require(nodeCount < maxNodes) { "Huffman tree node capacity exceeded" }
                                    right[curr] = nodeCount++
                                }
                                curr = right[curr]
                            }
                        }
                        symbol[curr] = sym
                    }
                }
            }
        }

        fun decode(reader: BitReader): Int {
            var curr = 0
            while (symbol[curr] == -1) {
                val bit = reader.readBits(1)
                curr = if (bit == 0) left[curr] else right[curr]
                if (curr == -1) {
                    throw IllegalArgumentException("Invalid Huffman code in stream")
                }
            }
            return symbol[curr]
        }
    }
}
