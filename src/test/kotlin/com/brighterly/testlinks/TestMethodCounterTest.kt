package com.brighterly.testlinks

import com.brighterly.testlinks.index.TestMethodCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class TestMethodCounterTest {

    @Test
    fun `counts classic testFoo methods`() {
        val src = """
            <?php
            class FooTest {
                public function testOne() {}
                public function testTwoThings() {}
                private function helper() {}
                public function notATest() {}
            }
        """.trimIndent()
        assertEquals(2, TestMethodCounter.count(src))
    }

    @Test
    fun `counts PHPUnit Test attribute`() {
        val src = """
            <?php
            class FooTest {
                #[Test]
                public function does_things() {}

                #[Test()]
                public function does_more_things() {}
            }
        """.trimIndent()
        assertEquals(2, TestMethodCounter.count(src))
    }

    @Test
    fun `counts @test docblock annotation`() {
        val src = """
            <?php
            class FooTest {
                /**
                 * @test
                 */
                public function does_things() {}

                /** @test */
                public function inline_style() {}
            }
        """.trimIndent()
        // Only the first one matches on its own line after "* "
        // The inline style isn't on its own line; our regex anchors on "^\s*\*\s*@test"
        // which matches both because the inline is also on its own line.
        assertEquals(2, TestMethodCounter.count(src))
    }

    @Test
    fun `mixed styles are summed`() {
        val src = """
            <?php
            class FooTest {
                public function testOne() {}

                #[Test]
                public function two() {}

                /**
                 * @test
                 */
                public function three() {}
            }
        """.trimIndent()
        assertEquals(3, TestMethodCounter.count(src))
    }

    @Test
    fun `returns zero for empty class`() {
        assertEquals(0, TestMethodCounter.count("<?php class Empty {}"))
    }
}
