package com.pesatrack.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the block parser used by [MarkdownText]. Rendering
 * itself is JUnit-visible only via Compose UI tests; the block-shape
 * contract is what we lock down here so the system prompt and the
 * client renderer stay in sync.
 */
class MarkdownTextTest {

    @Test
    fun `plain paragraph is one Paragraph block`() {
        val blocks = parseMarkdownBlocks("You have spent KES 12,400 this month.")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MdBlock.Paragraph
        assertEquals("You have spent KES 12,400 this month.", p.text)
    }

    @Test
    fun `hard-wrapped paragraph collapses to a single Paragraph`() {
        val blocks = parseMarkdownBlocks(
            """
            You have spent KES 12,400 on Food & Dining
            so far this month, 20% more than your 3-month
            average of KES 9,800.
            """.trimIndent(),
        )
        assertEquals(1, blocks.size)
        val p = blocks[0] as MdBlock.Paragraph
        assertTrue("hard-wrap should join with a space", p.text.contains("Dining so far this"))
    }

    @Test
    fun `blank line splits paragraphs`() {
        val blocks = parseMarkdownBlocks(
            """
            First paragraph.

            Second paragraph.
            """.trimIndent(),
        )
        assertEquals(2, blocks.size)
        assertEquals("First paragraph.", (blocks[0] as MdBlock.Paragraph).text)
        assertEquals("Second paragraph.", (blocks[1] as MdBlock.Paragraph).text)
    }

    @Test
    fun `## heading becomes a Heading block`() {
        val blocks = parseMarkdownBlocks("## Strengths")
        assertEquals(1, blocks.size)
        assertEquals("Strengths", (blocks[0] as MdBlock.Heading).text)
    }

    @Test
    fun `bullet list collects consecutive dash lines`() {
        val blocks = parseMarkdownBlocks(
            """
            - Food & Dining: KES 12,400
            - Transport: KES 4,200
            - Utilities: KES 8,900
            """.trimIndent(),
        )
        assertEquals(1, blocks.size)
        val list = blocks[0] as MdBlock.BulletList
        assertEquals(3, list.items.size)
        assertEquals("Food & Dining: KES 12,400", list.items[0])
    }

    @Test
    fun `numbered list collects consecutive digit-dot lines`() {
        val blocks = parseMarkdownBlocks(
            """
            1. First priority
            2. Second priority
            3. Third priority
            """.trimIndent(),
        )
        assertEquals(1, blocks.size)
        val list = blocks[0] as MdBlock.NumberedList
        assertEquals(3, list.items.size)
        assertEquals("First priority", list.items[0])
        assertEquals("Third priority", list.items[2])
    }

    @Test
    fun `pipe table parses header separator and rows`() {
        val blocks = parseMarkdownBlocks(
            """
            | Category | Month | Change |
            | -------- | ----- | ------ |
            | Food     | 12,400 | +18% |
            | Transport | 4,200 | -3% |
            """.trimIndent(),
        )
        assertEquals(1, blocks.size)
        val table = blocks[0] as MdBlock.Table
        assertEquals(listOf("Category", "Month", "Change"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Food", "12,400", "+18%"), table.rows[0])
        assertEquals(listOf("Transport", "4,200", "-3%"), table.rows[1])
    }

    @Test
    fun `mixed content parses heading, paragraph, bullets, table in order`() {
        val md = """
            ## Bottom line

            Your spending is stable but Food & Dining is drifting up.

            - Food is 18% above the 3-month average
            - Transport is 3% below

            | Category | Change |
            | -------- | ------ |
            | Food | +18% |
        """.trimIndent()
        val blocks = parseMarkdownBlocks(md)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.BulletList)
        assertTrue(blocks[3] is MdBlock.Table)
    }

    @Test
    fun `single line that looks like a table without separator stays a paragraph`() {
        // Guards against false-positive table detection when a
        // paragraph happens to start with a `|` — rare, but not
        // impossible in casual copy.
        val blocks = parseMarkdownBlocks("|Just | a paragraph with pipes|")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }

    @Test
    fun `blocks are separated by blank lines correctly`() {
        val blocks = parseMarkdownBlocks(
            """
            ## Section one

            - item a
            - item b

            ## Section two

            Body of section two.
            """.trimIndent(),
        )
        assertEquals(4, blocks.size)
        assertEquals("Section one", (blocks[0] as MdBlock.Heading).text)
        assertEquals(listOf("item a", "item b"), (blocks[1] as MdBlock.BulletList).items)
        assertEquals("Section two", (blocks[2] as MdBlock.Heading).text)
        assertEquals("Body of section two.", (blocks[3] as MdBlock.Paragraph).text)
    }
}
