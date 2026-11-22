package com.johnan.terminal.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PointerMouseEventTest {
    @Test
    fun testPressEncodingSgrAndX10() {
        assertEquals(
            "\u001B[<0;10;5M",
            PointerMouseEvent(MouseEvent.Press, MouseButton.LEFT, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[M *%",
            PointerMouseEvent(MouseEvent.Press, MouseButton.LEFT, row = 4, col = 9).encode(sgrMode = false),
        )

        assertEquals(
            "\u001B[<1;10;5M",
            PointerMouseEvent(MouseEvent.Press, MouseButton.MIDDLE, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[M!*%",
            PointerMouseEvent(MouseEvent.Press, MouseButton.MIDDLE, row = 4, col = 9).encode(sgrMode = false),
        )

        assertEquals(
            "\u001B[<2;10;5M",
            PointerMouseEvent(MouseEvent.Press, MouseButton.RIGHT, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[M\"*%",
            PointerMouseEvent(MouseEvent.Press, MouseButton.RIGHT, row = 4, col = 9).encode(sgrMode = false),
        )

        assertEquals(
            "\u001B[<0;10;5M",
            PointerMouseEvent(MouseEvent.Press, MouseButton.NONE, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[M *%",
            PointerMouseEvent(MouseEvent.Press, MouseButton.NONE, row = 4, col = 9).encode(sgrMode = false),
        )
    }

    @Test
    fun testReleaseEncodingSgrAndX10() {
        assertEquals(
            "\u001B[<0;10;5m",
            PointerMouseEvent(MouseEvent.Release, MouseButton.LEFT, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[<1;10;5m",
            PointerMouseEvent(MouseEvent.Release, MouseButton.MIDDLE, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[<2;10;5m",
            PointerMouseEvent(MouseEvent.Release, MouseButton.RIGHT, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[<0;10;5m",
            PointerMouseEvent(MouseEvent.Release, MouseButton.NONE, row = 4, col = 9).encode(sgrMode = true),
        )

        for (button in MouseButton.entries) {
            assertEquals(
                "\u001B[M#*%",
                PointerMouseEvent(MouseEvent.Release, button, row = 4, col = 9).encode(sgrMode = false),
            )
        }
    }

    @Test
    fun testDragEncodingSgrAndX10() {
        assertEquals(
            "\u001B[<32;10;5M",
            PointerMouseEvent(MouseEvent.Drag, MouseButton.LEFT, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[M@*%",
            PointerMouseEvent(MouseEvent.Drag, MouseButton.LEFT, row = 4, col = 9).encode(sgrMode = false),
        )

        assertEquals(
            "\u001B[<33;10;5M",
            PointerMouseEvent(MouseEvent.Drag, MouseButton.MIDDLE, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[MA*%",
            PointerMouseEvent(MouseEvent.Drag, MouseButton.MIDDLE, row = 4, col = 9).encode(sgrMode = false),
        )

        assertEquals(
            "\u001B[<34;10;5M",
            PointerMouseEvent(MouseEvent.Drag, MouseButton.RIGHT, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[MB*%",
            PointerMouseEvent(MouseEvent.Drag, MouseButton.RIGHT, row = 4, col = 9).encode(sgrMode = false),
        )

        assertEquals(
            "\u001B[<35;10;5M",
            PointerMouseEvent(MouseEvent.Drag, MouseButton.NONE, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[MC*%",
            PointerMouseEvent(MouseEvent.Drag, MouseButton.NONE, row = 4, col = 9).encode(sgrMode = false),
        )
    }

    @Test
    fun testWheelEncodingSgrAndX10() {
        assertEquals(
            "\u001B[<64;10;5M",
            PointerMouseEvent(MouseEvent.WheelUp, MouseButton.RIGHT, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[M`*%",
            PointerMouseEvent(MouseEvent.WheelUp, MouseButton.RIGHT, row = 4, col = 9).encode(sgrMode = false),
        )

        assertEquals(
            "\u001B[<65;10;5M",
            PointerMouseEvent(MouseEvent.WheelDown, MouseButton.NONE, row = 4, col = 9).encode(sgrMode = true),
        )
        assertEquals(
            "\u001B[Ma*%",
            PointerMouseEvent(MouseEvent.WheelDown, MouseButton.NONE, row = 4, col = 9).encode(sgrMode = false),
        )
    }

    @Test
    fun testModifierBitmasks() {
        val shiftEvent =
            PointerMouseEvent(
                type = MouseEvent.Press,
                button = MouseButton.LEFT,
                row = 0,
                col = 0,
                shiftPressed = true,
            )
        assertEquals("\u001B[<4;1;1M", shiftEvent.encode(sgrMode = true))
        assertEquals("\u001B[M${'$'}!!", shiftEvent.encode(sgrMode = false))

        val altEvent =
            PointerMouseEvent(
                type = MouseEvent.Press,
                button = MouseButton.MIDDLE,
                row = 0,
                col = 0,
                altPressed = true,
            )
        assertEquals("\u001B[<9;1;1M", altEvent.encode(sgrMode = true))
        assertEquals("\u001B[M)!!", altEvent.encode(sgrMode = false))

        val ctrlEvent =
            PointerMouseEvent(
                type = MouseEvent.Press,
                button = MouseButton.RIGHT,
                row = 0,
                col = 0,
                ctrlPressed = true,
            )
        assertEquals("\u001B[<18;1;1M", ctrlEvent.encode(sgrMode = true))
        assertEquals("\u001B[M2!!", ctrlEvent.encode(sgrMode = false))

        val allModsPress =
            PointerMouseEvent(
                type = MouseEvent.Press,
                button = MouseButton.LEFT,
                row = 0,
                col = 0,
                shiftPressed = true,
                altPressed = true,
                ctrlPressed = true,
            )
        assertEquals("\u001B[<28;1;1M", allModsPress.encode(sgrMode = true))
        assertEquals("\u001B[M<!!", allModsPress.encode(sgrMode = false))

        val allModsDragNone =
            PointerMouseEvent(
                type = MouseEvent.Drag,
                button = MouseButton.NONE,
                row = 0,
                col = 0,
                shiftPressed = true,
                altPressed = true,
                ctrlPressed = true,
            )
        assertEquals("\u001B[<63;1;1M", allModsDragNone.encode(sgrMode = true))
        assertEquals("\u001B[M_!!", allModsDragNone.encode(sgrMode = false))

        val allModsRelease =
            PointerMouseEvent(
                type = MouseEvent.Release,
                button = MouseButton.LEFT,
                row = 0,
                col = 0,
                shiftPressed = true,
                altPressed = true,
                ctrlPressed = true,
            )
        assertEquals("\u001B[<28;1;1m", allModsRelease.encode(sgrMode = true))
        assertEquals("\u001B[M?!!", allModsRelease.encode(sgrMode = false))

        val allModsWheelUp =
            PointerMouseEvent(
                type = MouseEvent.WheelUp,
                row = 0,
                col = 0,
                shiftPressed = true,
                altPressed = true,
                ctrlPressed = true,
            )
        assertEquals("\u001B[<92;1;1M", allModsWheelUp.encode(sgrMode = true))
        assertEquals("\u001B[M|!!", allModsWheelUp.encode(sgrMode = false))
    }

    @Test
    fun testX10CoordinateLimitsAndNegativeCoordinates() {
        val maxX10 = PointerMouseEvent(MouseEvent.Press, MouseButton.LEFT, row = 222, col = 222)
        assertEquals("\u001B[M ${Char(255)}${Char(255)}", maxX10.encode(sgrMode = false))
        assertEquals("\u001B[<0;223;223M", maxX10.encode(sgrMode = true))

        val overflowRow = PointerMouseEvent(MouseEvent.Press, MouseButton.LEFT, row = 223, col = 0)
        assertNull(overflowRow.encode(sgrMode = false))
        assertEquals("\u001B[<0;1;224M", overflowRow.encode(sgrMode = true))

        val overflowCol = PointerMouseEvent(MouseEvent.Press, MouseButton.LEFT, row = 0, col = 223)
        assertNull(overflowCol.encode(sgrMode = false))
        assertEquals("\u001B[<0;224;1M", overflowCol.encode(sgrMode = true))

        val negativeRow = PointerMouseEvent(MouseEvent.Press, MouseButton.LEFT, row = -1, col = 0)
        assertNull(negativeRow.encode(sgrMode = true))
        assertNull(negativeRow.encode(sgrMode = false))

        val negativeCol = PointerMouseEvent(MouseEvent.Press, MouseButton.LEFT, row = 0, col = -1)
        assertNull(negativeCol.encode(sgrMode = true))
        assertNull(negativeCol.encode(sgrMode = false))

        val bothNegative = PointerMouseEvent(MouseEvent.Drag, MouseButton.RIGHT, row = -5, col = -3)
        assertNull(bothNegative.encode(sgrMode = true))
        assertNull(bothNegative.encode(sgrMode = false))
    }
}
