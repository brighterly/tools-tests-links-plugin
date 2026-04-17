package com.brighterly.testlinks.index

/**
 * Counts PHPUnit test methods in a test file via regex over the raw text.
 *
 * Matches any of:
 *  - `function testFoo(...)` — classic naming convention
 *  - `#[Test]` — PHPUnit 10+ attribute
 *  - `@test` — docblock annotation
 *
 * Intentionally does NOT expand `#[DataProvider]` datasets — counts the method once.
 * Intentionally does NOT resolve inheritance from abstract base classes.
 * These are documented limitations; regex is sub-millisecond per file.
 */
object TestMethodCounter {

    private val FUNCTION_TEST_REGEX = Regex("""\bfunction\s+test[A-Z_]\w*\s*\(""")
    private val ATTRIBUTE_TEST_REGEX = Regex("""#\[\s*Test\s*(?:\(|])""")
    private val DOCBLOCK_TEST_REGEX = Regex("""(?m)^\s*(?:/\*\*?|\*)\s*@test\b""")

    fun count(text: String): Int {
        val fromNames = FUNCTION_TEST_REGEX.findAll(text).count()
        val fromAttributes = ATTRIBUTE_TEST_REGEX.findAll(text).count()
        val fromDocblock = DOCBLOCK_TEST_REGEX.findAll(text).count()
        return fromNames + fromAttributes + fromDocblock
    }
}
