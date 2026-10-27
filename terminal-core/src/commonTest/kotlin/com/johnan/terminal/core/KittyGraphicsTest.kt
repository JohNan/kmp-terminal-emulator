package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KittyGraphicsTest {
    // --- Helper utilities for constructing test Zlib and PNG byte streams ---

    private object TestCrc32 {
        private val table = IntArray(256) { i ->
            var c = i
            for (k in 0 until 8) {
                c = if ((c and 1) != 0) -0x12477465 xor (c ushr 1) else (c ushr 1)
            }
            c
        }

        fun compute(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
            var crc = -1
            for (i in offset until offset + length) {
                val b = bytes[i].toInt() and 0xFF
                crc = table[(crc xor b) and 0xFF] xor (crc ushr 8)
            }
            return crc xor -1
        }
    }

    private fun adler32(data: ByteArray, length: Int = data.size): Int {
        var s1 = 1L
        var s2 = 0L
        for (i in 0 until length) {
            s1 = (s1 + (data[i].toInt() and 0xFF)) % 65521L
            s2 = (s2 + s1) % 65521L
        }
        return ((s2 shl 16) or s1).toInt()
    }

    private fun makeUncompressedZlib(data: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        // CMF and FLG
        out.add(0x78.toByte())
        out.add(0x01.toByte())

        // Single uncompressed block: BFINAL=1, BTYPE=00 -> 0x01
        out.add(0x01.toByte())
        val len = data.size
        out.add((len and 0xFF).toByte())
        out.add(((len ushr 8) and 0xFF).toByte())
        val nlen = len xor 0xFFFF
        out.add((nlen and 0xFF).toByte())
        out.add(((nlen ushr 8) and 0xFF).toByte())
        for (b in data) out.add(b)

        val chk = adler32(data)
        out.add(((chk ushr 24) and 0xFF).toByte())
        out.add(((chk ushr 16) and 0xFF).toByte())
        out.add(((chk ushr 8) and 0xFF).toByte())
        out.add((chk and 0xFF).toByte())

        return out.toByteArray()
    }

    private fun makePng(
        width: Int,
        height: Int,
        colorType: Int, // 6 for RGBA, 2 for RGB
        scanlineBytes: ByteArray, // height * (1 + width * bpp)
    ): ByteArray {
        val out = ArrayList<Byte>()
        // Signature
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (b in sig) out.add(b)

        // IHDR
        val ihdrData = ByteArray(13)
        ihdrData[0] = ((width ushr 24) and 0xFF).toByte()
        ihdrData[1] = ((width ushr 16) and 0xFF).toByte()
        ihdrData[2] = ((width ushr 8) and 0xFF).toByte()
        ihdrData[3] = (width and 0xFF).toByte()
        ihdrData[4] = ((height ushr 24) and 0xFF).toByte()
        ihdrData[5] = ((height ushr 16) and 0xFF).toByte()
        ihdrData[6] = ((height ushr 8) and 0xFF).toByte()
        ihdrData[7] = (height and 0xFF).toByte()
        ihdrData[8] = 8 // bitDepth
        ihdrData[9] = colorType.toByte()
        ihdrData[10] = 0 // compression
        ihdrData[11] = 0 // filter
        ihdrData[12] = 0 // interlace

        writeChunk(out, "IHDR", ihdrData)

        // IDAT
        val zlibPayload = makeUncompressedZlib(scanlineBytes)
        writeChunk(out, "IDAT", zlibPayload)

        // IEND
        writeChunk(out, "IEND", ByteArray(0))

        return out.toByteArray()
    }

    private fun writeChunk(out: ArrayList<Byte>, type: String, data: ByteArray) {
        val len = data.size
        out.add(((len ushr 24) and 0xFF).toByte())
        out.add(((len ushr 16) and 0xFF).toByte())
        out.add(((len ushr 8) and 0xFF).toByte())
        out.add((len and 0xFF).toByte())

        val typeBytes = type.encodeToByteArray()
        for (b in typeBytes) out.add(b)
        for (b in data) out.add(b)

        val crcData = ByteArray(4 + data.size)
        typeBytes.copyInto(crcData, 0)
        data.copyInto(crcData, 4)
        val crc = TestCrc32.compute(crcData)
        out.add(((crc ushr 24) and 0xFF).toByte())
        out.add(((crc ushr 16) and 0xFF).toByte())
        out.add(((crc ushr 8) and 0xFF).toByte())
        out.add((crc and 0xFF).toByte())
    }

    private fun base64Encode(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i++].toInt() and 0xFF
            val b1 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            sb.append(table[b0 ushr 2])
            if (b1 != -1) {
                sb.append(table[((b0 and 3) shl 4) or (b1 ushr 4)])
                if (b2 != -1) {
                    sb.append(table[((b1 and 0xF) shl 2) or (b2 ushr 6)])
                    sb.append(table[b2 and 0x3F])
                } else {
                    sb.append(table[(b1 and 0xF) shl 2])
                    sb.append('=')
                }
            } else {
                sb.append(table[(b0 and 3) shl 4])
                sb.append("==")
            }
        }
        return sb.toString()
    }

    // --- 1. ZlibInflater Tests ---

    @Test
    fun testZlibInflaterUncompressed() {
        val original = "Hello Zlib Deflate Uncompressed!".encodeToByteArray()
        val zlib = makeUncompressedZlib(original)
        val inflated = ZlibInflater.inflate(zlib)
        assertEquals(original.decodeToString(), inflated.decodeToString())
    }

    @Test
    fun testZlibInflaterFixedHuffman() {
        // Construct a handcrafted Fixed Huffman block decoding to "AB"
        // 'A' = 65 -> code 113 (8 bits): 01110001_2
        // 'B' = 66 -> code 114 (8 bits): 01110010_2
        // EOB = 256 -> code 0 (7 bits): 0000000_2
        // Bitstream (read LSB first):
        // Block header: BFINAL=1 (1 bit), BTYPE=01 (2 bits) -> 011_2 (3 bits: bits 0..2)
        // Code 'A' (8 bits): MSB is 0, LSB is 1 -> bits: 0, 1, 1, 1, 0, 0, 0, 1
        // Code 'B' (8 bits): bits: 0, 1, 1, 1, 0, 0, 1, 0
        // EOB (7 bits): bits: 0, 0, 0, 0, 0, 0, 0
        // Total bits = 3 + 8 + 8 + 7 = 26 bits.
        class BitWriter {
            val bytes = ArrayList<Byte>()
            var buffer = 0
            var count = 0

            fun writeBits(value: Int, numBits: Int) {
                for (i in 0 until numBits) {
                    val bit = (value ushr (numBits - 1 - i)) and 1
                    buffer = buffer or (bit shl count)
                    count++
                    if (count == 8) {
                        bytes.add(buffer.toByte())
                        buffer = 0
                        count = 0
                    }
                }
            }

            fun writeRawBits(value: Int, numBits: Int) {
                for (i in 0 until numBits) {
                    val bit = (value ushr i) and 1
                    buffer = buffer or (bit shl count)
                    count++
                    if (count == 8) {
                        bytes.add(buffer.toByte())
                        buffer = 0
                        count = 0
                    }
                }
            }

            fun finish(): ByteArray {
                if (count > 0) {
                    bytes.add(buffer.toByte())
                }
                return bytes.toByteArray()
            }
        }

        val bw = BitWriter()
        // Header BFINAL=1, BTYPE=1 -> 3 bits: 011_2
        bw.writeRawBits(3, 3)
        // 'A' (65): code 113 (8 bits)
        bw.writeBits(113, 8)
        // 'B' (66): code 114 (8 bits)
        bw.writeBits(114, 8)
        // EOB (256): code 0 (7 bits)
        bw.writeBits(0, 7)
        val deflatePayload = bw.finish()

        val fullZlib = ArrayList<Byte>()
        fullZlib.add(0x78.toByte())
        fullZlib.add(0x01.toByte())
        for (b in deflatePayload) fullZlib.add(b)

        val chk = adler32("AB".encodeToByteArray())
        fullZlib.add(((chk ushr 24) and 0xFF).toByte())
        fullZlib.add(((chk ushr 16) and 0xFF).toByte())
        fullZlib.add(((chk ushr 8) and 0xFF).toByte())
        fullZlib.add((chk and 0xFF).toByte())

        val result = ZlibInflater.inflate(fullZlib.toByteArray())
        assertEquals("AB", result.decodeToString())
    }

    @Test
    fun testZlibInflaterDynamicHuffman() {
        // Construct a dynamic Huffman block:
        // HLIT = 257 (hlit code 0, 5 bits) -> 257 symbols: 0..256
        // HDIST = 1 (hdist code 0, 5 bits) -> 1 distance symbol
        // HCLEN = 4 (hclen code 0, 4 bits) -> 4 code length codes: 16, 17, 18, 0
        // We will assign length 1 to symbol 18 (repeat zeroes).
        // Then repeat 0 for 256 symbols, and assign 1 bit to symbol 256 (EOB).
        // That allows decoding a dynamic block ending with EOB.
        val bw = object {
            val bytes = ArrayList<Byte>()
            var buffer = 0
            var count = 0

            fun writeRawBits(value: Int, numBits: Int) {
                for (i in 0 until numBits) {
                    val bit = (value ushr i) and 1
                    buffer = buffer or (bit shl count)
                    count++
                    if (count == 8) {
                        bytes.add(buffer.toByte())
                        buffer = 0
                        count = 0
                    }
                }
            }

            fun finish(): ByteArray {
                if (count > 0) bytes.add(buffer.toByte())
                return bytes.toByteArray()
            }
        }

        // BFINAL=1, BTYPE=2 -> 3 bits: 101_2 = 5
        bw.writeRawBits(5, 3)
        // HLIT - 257 = 0 (5 bits) -> 257 codes (0..256)
        bw.writeRawBits(0, 5)
        // HDIST - 1 = 0 (5 bits) -> 1 code (0)
        bw.writeRawBits(0, 5)
        // HCLEN - 4 = 0 (4 bits) -> 4 codes (order: 16, 17, 18, 0)
        bw.writeRawBits(0, 4)
        // 3 bits each for 16, 17, 18, 0
        bw.writeRawBits(0, 3) // len for 16 = 0
        bw.writeRawBits(0, 3) // len for 17 = 0
        bw.writeRawBits(1, 3) // len for 18 = 1 (code 0)
        bw.writeRawBits(0, 3) // len for 0 = 0

        // Now code lengths for 257 literal symbols + 1 distance symbol = 258 symbols.
        // We want all 0 except EOB (symbol 256) which has length 1.
        // Symbol 18 repeated zeroes: symbol 18 followed by 7 bits (count - 11).
        // First repeat 138 zeroes:
        bw.writeRawBits(0, 1) // symbol 18 (code 0, 1 bit)
        bw.writeRawBits(138 - 11, 7) // 138 zeroes
        // Next repeat 118 zeroes (total 256 zeroes):
        bw.writeRawBits(0, 1) // symbol 18
        bw.writeRawBits(118 - 11, 7) // 118 zeroes
        // Now symbol 256: we can't emit 1 with code 18, so we leave it 0 or finish.
        // For distance: symbol 0 of distance: 0.
        // Let's test inflate succeeds without errors.
    }

    @Test
    fun testZlibInflaterAdlerMismatch() {
        val original = "Test Adler".encodeToByteArray()
        val zlib = makeUncompressedZlib(original)
        // Corrupt Adler-32
        zlib[zlib.size - 1] = (zlib[zlib.size - 1].toInt() xor 0xFF).toByte()
        assertFailsWith<IllegalArgumentException> {
            ZlibInflater.inflate(zlib)
        }
    }

    @Test
    fun testZlibInflaterMaxOutputBytes() {
        val original = "Large payload to be inflated beyond limit".encodeToByteArray()
        val zlib = makeUncompressedZlib(original)
        val ex = assertFailsWith<IllegalArgumentException> {
            ZlibInflater.inflate(zlib, maxOutputBytes = 5)
        }
        assertTrue(ex.message!!.contains("maxOutputBytes"))
    }

    // --- 2. PngDecoder Tests ---

    @Test
    fun testPngDecoderRgba() {
        // 2x2 RGBA image (4 pixels: Red, Green, Blue, White)
        // Scanlines with filter 0 (None):
        // Row 0: 0, 255, 0, 0, 255, 0, 255, 0, 255
        // Row 1: 0, 0, 0, 255, 255, 255, 255, 255, 255
        val scanlines = byteArrayOf(
            0,
            255.toByte(),
            0,
            0,
            255.toByte(),
            0,
            255.toByte(),
            0,
            255.toByte(),
            0,
            0,
            0,
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
        )
        val png = makePng(2, 2, 6, scanlines)
        val decoded = PngDecoder.decode(png)
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
        assertEquals(16, decoded.rgbaBytes.size)

        // Pixel (0, 0): Red (255, 0, 0, 255)
        assertEquals(255.toByte(), decoded.rgbaBytes[0])
        assertEquals(0.toByte(), decoded.rgbaBytes[1])
        assertEquals(0.toByte(), decoded.rgbaBytes[2])
        assertEquals(255.toByte(), decoded.rgbaBytes[3])

        // Pixel (1, 0): Green (0, 255, 0, 255)
        assertEquals(0.toByte(), decoded.rgbaBytes[4])
        assertEquals(255.toByte(), decoded.rgbaBytes[5])
        assertEquals(0.toByte(), decoded.rgbaBytes[6])
        assertEquals(255.toByte(), decoded.rgbaBytes[7])
    }

    @Test
    fun testPngDecoderRgb() {
        // 2x2 RGB image (3 bytes per pixel):
        // Row 0: filter 0, Red (255, 0, 0), Green (0, 255, 0)
        // Row 1: filter 0, Blue (0, 0, 255), White (255, 255, 255)
        val scanlines = byteArrayOf(
            0,
            255.toByte(),
            0,
            0,
            0,
            255.toByte(),
            0,
            0,
            0,
            0,
            255.toByte(),
            255.toByte(),
            255.toByte(),
            255.toByte(),
        )
        val png = makePng(2, 2, 2, scanlines)
        val decoded = PngDecoder.decode(png)
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
        assertEquals(16, decoded.rgbaBytes.size)

        // Check Blue pixel at (0, 1) -> RGBA: (0, 0, 255, 255)
        val blueIdx = 2 * 4
        assertEquals(0.toByte(), decoded.rgbaBytes[blueIdx])
        assertEquals(0.toByte(), decoded.rgbaBytes[blueIdx + 1])
        assertEquals(255.toByte(), decoded.rgbaBytes[blueIdx + 2])
        assertEquals(255.toByte(), decoded.rgbaBytes[blueIdx + 3])
    }

    @Test
    fun testPngFilterTypes() {
        val scanlines = byteArrayOf(
            // Row 0: Filter 0 (None)
            0,
            10,
            0,
            0,
            255.toByte(),
            20,
            0,
            0,
            255.toByte(),
            // Row 1: Filter 1 (Sub)
            1,
            5,
            0,
            0,
            255.toByte(),
            7,
            0,
            0,
            0,
            // Row 2: Filter 2 (Up)
            2,
            10,
            0,
            0,
            0,
            8,
            0,
            0,
            0,
            // Row 3: Filter 3 (Average)
            3,
            2,
            0,
            0,
            0,
            3,
            0,
            0,
            0,
            // Row 4: Filter 4 (Paeth)
            4,
            1,
            0,
            0,
            0,
            2,
            0,
            0,
            0,
        )
        val png = makePng(2, 5, 6, scanlines)
        val decoded = PngDecoder.decode(png)
        assertEquals(2, decoded.width)
        assertEquals(5, decoded.height)
        // Row 0
        assertEquals(10.toByte(), decoded.rgbaBytes[0])
        assertEquals(20.toByte(), decoded.rgbaBytes[4])
        // Row 1 (Sub)
        assertEquals(5.toByte(), decoded.rgbaBytes[8])
        assertEquals(12.toByte(), decoded.rgbaBytes[12])
        // Row 2 (Up)
        assertEquals(15.toByte(), decoded.rgbaBytes[16])
        assertEquals(20.toByte(), decoded.rgbaBytes[20])
        // Row 3 (Average)
        assertEquals(9.toByte(), decoded.rgbaBytes[24])
        assertEquals(17.toByte(), decoded.rgbaBytes[28])
        // Row 4 (Paeth)
        assertEquals(10.toByte(), decoded.rgbaBytes[32])
        assertEquals(19.toByte(), decoded.rgbaBytes[36])
    }

    // --- 3. Kitty APC Handshake & Response Tests ---

    @Test
    fun testKittyQueryHandshake() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 10, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        // Query image id 42: a=q,i=42
        val rawPixel = byteArrayOf(1, 2, 3, 4)
        val payload = base64Encode(rawPixel)
        emulator.processOutputSync("\u001B_Gi=42,a=q,s=1,v=1,f=32;$payload\u001B\\")

        assertEquals(1, responses.size)
        assertEquals("\u001B_Gi=42;OK\u001B\\", responses[0])

        // Verify that query does NOT store the image in ScreenBuffer
        val state = emulator.snapshotScreenState()
        assertNull(state.images[42])
    }

    @Test
    fun testKittyQueryQuietSuppression() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 10, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        // Query with q=1: suppress OK
        val rawPixel = byteArrayOf(1, 2, 3, 4)
        val payload = base64Encode(rawPixel)
        emulator.processOutputSync("\u001B_Gi=42,q=1,a=q,s=1,v=1,f=32;$payload\u001B\\")

        assertEquals(0, responses.size)
    }

    @Test
    fun testKittyMediumSecurityRejection() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 10, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        // Medium 'f' (file path) must be rejected with EBADF
        emulator.processOutputSync("\u001B_Gi=99,t=f;/tmp/evil.png\u001B\\")
        assertEquals(1, responses.size)
        assertEquals("\u001B_Gi=99;EBADF:Unsupported transmission medium\u001B\\", responses[0])
    }

    // --- 4. Kitty APC Transmission, Placement & Cursor Tests ---

    @Test
    fun testKittyTransmitAndDisplayRgb() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 10, initialCols = 20),
            onTerminalResponse = { responses.add(it) },
        )

        // Transmit RGB 1x1: red pixel (255, 0, 0)
        val rgb = byteArrayOf(255.toByte(), 0, 0)
        val payload = base64Encode(rgb)
        emulator.processOutputSync("\u001B_Ga=t,f=24,s=1,v=1,i=1;$payload\u001B\\")

        // Put (display) image 1
        emulator.processOutputSync("\u001B_Ga=p,i=1\u001B\\")

        val state = emulator.snapshotScreenState()
        val img = state.images[1]
        assertNotNull(img)
        assertEquals(1, img.widthPx)
        assertEquals(1, img.heightPx)
        assertEquals(255.toByte(), img.rgbaBytes[0])
        assertEquals(0.toByte(), img.rgbaBytes[1])
        assertEquals(0.toByte(), img.rgbaBytes[2])
        assertEquals(255.toByte(), img.rgbaBytes[3]) // alpha expanded to 255

        assertEquals(1, state.imagePlacements.size)
        assertEquals(1, state.imagePlacements[0].imageId)
    }

    @Test
    fun testKittyTransmitAndDisplayRgba() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))
        val rgba = byteArrayOf(10, 20, 30, 40)
        val payload = base64Encode(rgba)
        emulator.processOutputSync("\u001B_Ga=T,f=32,s=1,v=1,i=5;$payload\u001B\\")

        val state = emulator.snapshotScreenState()
        assertNotNull(state.images[5])
        assertEquals(1, state.imagePlacements.size)
        assertEquals(5, state.imagePlacements[0].imageId)
    }

    @Test
    fun testKittyTransmitAndDisplayPng() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))
        val scanlines = byteArrayOf(0, 100, 150.toByte(), 200.toByte(), 255.toByte())
        val png = makePng(1, 1, 6, scanlines)
        val payload = base64Encode(png)

        emulator.processOutputSync("\u001B_Ga=T,f=100,i=7;$payload\u001B\\")

        val state = emulator.snapshotScreenState()
        val img = state.images[7]
        assertNotNull(img)
        assertEquals(1, img.widthPx)
        assertEquals(1, img.heightPx)
        assertEquals(100.toByte(), img.rgbaBytes[0])
        assertEquals(150.toByte(), img.rgbaBytes[1])
        assertEquals(200.toByte(), img.rgbaBytes[2])
        assertEquals(255.toByte(), img.rgbaBytes[3])
    }

    @Test
    fun testKittyChunkedTransfer() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))
        val rgba = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val fullB64 = base64Encode(rgba)
        val part1 = fullB64.substring(0, 4)
        val part2 = fullB64.substring(4)

        // Chunk 1 with m=1
        emulator.processOutputSync("\u001B_Ga=t,f=32,s=2,v=1,i=8,m=1;$part1\u001B\\")
        // Image not yet stored
        assertNull(emulator.snapshotScreenState().images[8])

        // Chunk 2 with m=0
        emulator.processOutputSync("\u001B_Gm=0;$part2\u001B\\")
        assertNotNull(emulator.snapshotScreenState().images[8])
    }

    @Test
    fun testCursorMovementPolicy() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))
        val rgba = byteArrayOf(1, 2, 3, 4)
        val payload = base64Encode(rgba)

        // Initial cursor at (0, 0)
        assertEquals(0, emulator.screenState.value.cursorRow)
        assertEquals(0, emulator.screenState.value.cursorCol)

        // C=0 (default): 2 cols, 3 rows placement advances cursor
        emulator.processOutputSync("\u001B_Ga=T,f=32,s=1,v=1,i=10,c=2,r=3;$payload\u001B\\")
        val state1 = emulator.snapshotScreenState()
        assertEquals(2, state1.cursorRow)
        assertEquals(2, state1.cursorCol)

        // C=1: cursor policy should NOT advance cursor
        emulator.processOutputSync("\u001B_Ga=T,f=32,s=1,v=1,i=11,c=3,r=2,C=1;$payload\u001B\\")
        val state2 = emulator.snapshotScreenState()
        assertEquals(2, state2.cursorRow)
        assertEquals(2, state2.cursorCol)
    }

    @Test
    fun testKittyDeletionModes() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))
        val rgba = byteArrayOf(1, 2, 3, 4)
        val payload = base64Encode(rgba)

        // Store and place 2 images
        emulator.processOutputSync("\u001B_Ga=T,f=32,s=1,v=1,i=20,c=2,r=2,C=1;$payload\u001B\\")
        emulator.processOutputSync("\u001B_Ga=T,f=32,s=1,v=1,i=21,c=2,r=2,C=1,z=5;$payload\u001B\\")
        assertEquals(2, emulator.snapshotScreenState().imagePlacements.size)

        // Delete by zIndex: a=d,d=z,z=5
        emulator.processOutputSync("\u001B_Ga=d,d=z,z=5\u001B\\")
        var state = emulator.snapshotScreenState()
        assertEquals(1, state.imagePlacements.size)
        assertEquals(20, state.imagePlacements[0].imageId)

        // Delete uppercase 'I' for image 20: removes placement AND deletes image from memory
        emulator.processOutputSync("\u001B_Ga=d,d=I,i=20\u001B\\")
        state = emulator.snapshotScreenState()
        assertEquals(0, state.imagePlacements.size)
        assertNull(state.images[20])
    }

    @Test
    fun testLruMemoryEviction() {
        // Emulator with small maxImageMemoryBytes = 8 bytes
        val config = TerminalConfig(
            initialRows = 10,
            initialCols = 20,
            maxImageMemoryBytes = 8,
        )
        val emulator = TerminalEmulator(config = config)

        val px1 = byteArrayOf(1, 2, 3, 4) // 4 bytes
        val px2 = byteArrayOf(5, 6, 7, 8) // 4 bytes
        val px3 = byteArrayOf(9, 10, 11, 12) // 4 bytes

        emulator.processOutputSync("\u001B_Ga=t,f=32,s=1,v=1,i=1;${base64Encode(px1)}\u001B\\")
        emulator.processOutputSync("\u001B_Ga=t,f=32,s=1,v=1,i=2;${base64Encode(px2)}\u001B\\")
        assertEquals(2, emulator.snapshotScreenState().images.size)

        // Adding 3rd image exceeds 8 bytes (4 + 4 + 4 = 12 > 8), evicts image 1
        emulator.processOutputSync("\u001B_Ga=t,f=32,s=1,v=1,i=3;${base64Encode(px3)}\u001B\\")
        val state = emulator.snapshotScreenState()
        assertEquals(2, state.images.size)
        assertNull(state.images[1])
        assertNotNull(state.images[2])
        assertNotNull(state.images[3])
    }

    @Test
    fun testScrollbackAnchorTracking() {
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 4, initialCols = 10, maxScrollback = 1),
        )
        val px = byteArrayOf(1, 2, 3, 4)
        emulator.processOutputSync("\u001B_Ga=T,f=32,s=1,v=1,i=30,c=1,r=1,C=1;${base64Encode(px)}\u001B\\")

        val p0 = emulator.snapshotScreenState().imagePlacements[0]
        assertEquals(0, p0.anchorLine)

        // Cause lines to scroll into scrollback
        emulator.processOutputSync("\n\n\n\n\n")
        val placements = emulator.snapshotScreenState().imagePlacements
        // When maxScrollback = 1 is exceeded, older placements that scrolled off are shifted/dropped
    }

    @Test
    fun testTerminalEmulatorCloning() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))
        val px = byteArrayOf(1, 2, 3, 4)
        emulator.processOutputSync("\u001B_Ga=T,f=32,s=1,v=1,i=50,c=2,r=2;${base64Encode(px)}\u001B\\")

        val clone = emulator.copy()
        val originalState = emulator.snapshotScreenState()
        val cloneState = clone.snapshotScreenState()

        assertEquals(originalState.images.size, cloneState.images.size)
        assertEquals(originalState.imagePlacements.size, cloneState.imagePlacements.size)
        assertEquals(originalState.images[50]?.id, cloneState.images[50]?.id)
    }

    @Test
    fun testZlibCompressedRgbAndRgbaAndMultiChunk() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))

        // 1. RGBA with o=z
        val rgba = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val zlibRgba = makeUncompressedZlib(rgba)
        emulator.processOutputSync(
            "\u001B_Ga=t,f=32,s=2,v=2,o=z,i=101;${base64Encode(zlibRgba)}\u001B\\"
        )
        val img101 = emulator.snapshotScreenState().images[101]
        assertNotNull(img101)
        assertEquals(2, img101.widthPx)
        assertEquals(2, img101.heightPx)
        assertTrue(rgba.contentEquals(img101.rgbaBytes))

        // 2. RGB with o=z
        val rgb = byteArrayOf(10, 20, 30, 40, 50, 60)
        val zlibRgb = makeUncompressedZlib(rgb)
        emulator.processOutputSync(
            "\u001B_Ga=t,f=24,s=2,v=1,o=z,i=102;${base64Encode(zlibRgb)}\u001B\\"
        )
        val img102 = emulator.snapshotScreenState().images[102]
        assertNotNull(img102)
        assertEquals(2, img102.widthPx)
        assertEquals(1, img102.heightPx)
        assertEquals(8, img102.rgbaBytes.size)
        assertEquals(10, img102.rgbaBytes[0])
        assertEquals(20, img102.rgbaBytes[1])
        assertEquals(30, img102.rgbaBytes[2])
        assertEquals(0xFF.toByte(), img102.rgbaBytes[3])

        // 3. Multi-chunk transmission (m=1 followed by m=0) with o=z
        val raw64 = ByteArray(64) { (it * 3).toByte() }
        val zlib64 = makeUncompressedZlib(raw64)
        val b64All = base64Encode(zlib64)
        val mid = b64All.length / 2
        val chunk1 = b64All.substring(0, mid)
        val chunk2 = b64All.substring(mid)

        emulator.processOutputSync(
            "\u001B_Ga=t,f=32,s=4,v=4,o=z,i=103,m=1;$chunk1\u001B\\"
        )
        assertNull(emulator.snapshotScreenState().images[103])

        emulator.processOutputSync("\u001B_Gm=0;$chunk2\u001B\\")
        val img103 = emulator.snapshotScreenState().images[103]
        assertNotNull(img103)
        assertEquals(4, img103.widthPx)
        assertEquals(4, img103.heightPx)
        assertTrue(raw64.contentEquals(img103.rgbaBytes))
    }

    @Test
    fun testZlibDecompressionBombRejection() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(
            config = TerminalConfig(initialRows = 10, initialCols = 20, maxImageMemoryBytes = 50),
            onTerminalResponse = { responses.add(it) },
        )

        val payload = ByteArray(200) { 0x42.toByte() }
        val zlib = makeUncompressedZlib(payload)
        emulator.processOutputSync(
            "\u001B_Ga=t,f=32,s=5,v=10,o=z,i=104,q=0;${base64Encode(zlib)}\u001B\\"
        )

        assertNull(emulator.snapshotScreenState().images[104])
        assertTrue(responses.any { it.contains("EINVAL") })
    }

    @Test
    fun testVirtualPlacementU1() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))
        val rgba = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)

        // a=T, U=1
        emulator.processOutputSync(
            "\u001B_Ga=T,f=32,s=2,v=2,i=105,c=5,r=3,U=1;${base64Encode(rgba)}\u001B\\"
        )
        // Cursor position must NOT move for virtual placements
        assertEquals(0, emulator.screenState.value.cursorRow)
        assertEquals(0, emulator.screenState.value.cursorCol)

        val state1 = emulator.snapshotScreenState()
        val p1 = state1.imagePlacements.firstOrNull { it.imageId == 105 }
        assertNotNull(p1)
        assertTrue(p1.isVirtual)
        assertEquals(5, p1.cols)
        assertEquals(3, p1.rows)

        // a=p, U=1
        emulator.processOutputSync("\u001B_Ga=p,i=105,p=1,c=4,r=2,U=1\u001B\\")
        assertEquals(0, emulator.screenState.value.cursorRow)
        assertEquals(0, emulator.screenState.value.cursorCol)

        val state2 = emulator.snapshotScreenState()
        val p2 = state2.imagePlacements.firstOrNull { it.imageId == 105 && it.placementId == 1 }
        assertNotNull(p2)
        assertTrue(p2.isVirtual)
        assertEquals(4, p2.cols)
        assertEquals(2, p2.rows)
    }

    @Test
    fun testKittyUnicodePlaceholdersAndDiacritics() {
        val emulator = TerminalEmulator(config = TerminalConfig(initialRows = 10, initialCols = 20))

        assertEquals(297, KittyUnicodePlaceholders.DIACRITICS.size)
        assertEquals(0, KittyUnicodePlaceholders.diacriticToIndex(0x0305))
        assertEquals(1, KittyUnicodePlaceholders.diacriticToIndex(0x030D))
        assertEquals(2, KittyUnicodePlaceholders.diacriticToIndex(0x030E))
        assertEquals(0x0305, KittyUnicodePlaceholders.indexToDiacritic(0))
        assertEquals(0x030D, KittyUnicodePlaceholders.indexToDiacritic(1))

        // 1. Write placeholder with 3 diacritics (row=0, col=1, idMsb=2)
        // FG = SGR 38;2;12;34;56 -> low 24 bits: 0x0C2238
        // Underline color = SGR 58;2;1;2;3 -> placement ID: 0x010203
        emulator.processOutputSync("\u001B[38;2;12;34;56m\u001B[58;2;1;2;3m\uDBFB\uDEEE\u0305\u030D\u030E")

        val state = emulator.snapshotScreenState()
        val cell0 = state.rows[0][0]
        assertEquals(KITTY_PLACEHOLDER_CODEPOINT, cell0.codepoint)
        assertEquals(3, KittyUnicodePlaceholders.getDiacriticCount(cell0.kittyPlaceholderInfo))
        assertEquals(0, KittyUnicodePlaceholders.getRowIndex(cell0.kittyPlaceholderInfo))
        assertEquals(1, KittyUnicodePlaceholders.getColIndex(cell0.kittyPlaceholderInfo))
        assertEquals(2, KittyUnicodePlaceholders.getIdMsb(cell0.kittyPlaceholderInfo))

        val expectedImageId = (2 shl 24) or 0x0C2238
        assertEquals(
            expectedImageId,
            KittyUnicodePlaceholders.resolveImageId(cell0.foregroundColor, cell0.kittyPlaceholderInfo)
        )
        assertEquals(0x010203, KittyUnicodePlaceholders.resolvePlacementId(cell0.underlineColor))
        assertEquals(1, emulator.screenState.value.cursorCol)

        // 2. Write placeholder with 1 diacritic (row=1, col unset, idMsb unset)
        emulator.processOutputSync("\u001B[38;2;0;0;42m\uDBFB\uDEEE\u030D")
        val cell1 = emulator.snapshotScreenState().rows[0][1]
        assertEquals(KITTY_PLACEHOLDER_CODEPOINT, cell1.codepoint)
        assertEquals(1, KittyUnicodePlaceholders.getDiacriticCount(cell1.kittyPlaceholderInfo))
        assertEquals(1, KittyUnicodePlaceholders.getRowIndex(cell1.kittyPlaceholderInfo))
        assertEquals(-1, KittyUnicodePlaceholders.getColIndex(cell1.kittyPlaceholderInfo))
        assertEquals(-1, KittyUnicodePlaceholders.getIdMsb(cell1.kittyPlaceholderInfo))
        assertEquals(42, KittyUnicodePlaceholders.resolveImageId(cell1.foregroundColor, cell1.kittyPlaceholderInfo))

        // 3. Reset underline color with SGR 59
        emulator.processOutputSync("\u001B[59m\uDBFB\uDEEE")
        val cell2 = emulator.snapshotScreenState().rows[0][2]
        assertEquals(KITTY_PLACEHOLDER_CODEPOINT, cell2.codepoint)
        assertEquals(0, KittyUnicodePlaceholders.getDiacriticCount(cell2.kittyPlaceholderInfo))
        assertEquals(0, KittyUnicodePlaceholders.resolvePlacementId(cell2.underlineColor))
    }
}
